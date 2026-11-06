package edu.ooad.joballoc.web.http;

import edu.ooad.joballoc.business.security.Actor;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Read-only view of an incoming HTTP request plus what the front controller resolved for it.
 *
 * <p>Refactored: this class no longer holds the JDK {@code HttpExchange}. It is a plain value built
 * by {@link HttpExchangeAdapter} (ADAPTER) from the server's API, or directly by tests. Before, it
 * had two constructors and {@code if (exchange == null)} branches so tests could avoid a real server.
 */
public final class Request {

    public static final int MAX_BODY_BYTES = 64 * 1024;
    /** CORRELATION IDENTIFIER (EIP): the caller's X-Request-Id if well-formed, otherwise a new one. */
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{8,64}");

    private final String method;
    private final String path;
    private final Map<String, List<String>> query;
    private final Map<String, String> headers;
    private final byte[] body;
    private final String requestId;
    private Map<String, List<String>> form;
    private Map<String, String> pathVars = Map.of();
    private Session session;
    private Actor actor;

    /** @param headers first value of each header; names are matched case-insensitively */
    Request(String method, String path, String rawQuery, byte[] body, Map<String, String> headers) {
        this.method = method.toUpperCase();
        this.path = path;
        this.query = parse(rawQuery);
        this.body = body == null ? new byte[0] : body;
        Map<String, String> h = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        h.putAll(headers);
        this.headers = Collections.unmodifiableMap(h);
        this.requestId = correlationId(this.headers.get(REQUEST_ID_HEADER));
    }

    private static String correlationId(String supplied) {
        return supplied != null && SAFE_ID.matcher(supplied).matches()
                ? supplied
                : UUID.randomUUID().toString();
    }

    /** Correlation ID of this request: echoed in the response, written to the access log and to problem details. */
    public String requestId() { return requestId; }

    public String method() { return method; }
    public String path() { return path; }
    public byte[] body() { return body; }
    public Optional<Session> session() { return Optional.ofNullable(session); }
    public Actor actor() { return actor; }

    void bind(Map<String, String> vars, Session s, Actor a) {
        this.pathVars = vars;
        this.session = s;
        this.actor = a;
    }

    public String header(String name) {
        return headers.get(name);
    }

    public String cookie(String name) {
        String all = header("Cookie");
        if (all == null) {
            return null;
        }
        for (String part : all.split(";")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2 && kv[0].equals(name)) {
                return kv[1];
            }
        }
        return null;
    }

    public String pathVar(String name) {
        return pathVars.get(name);
    }

    /** First value of a query or form parameter (form wins), or null. */
    public String param(String name) {
        List<String> v = params(name);
        return v.isEmpty() ? null : v.get(0);
    }

    public List<String> params(String name) {
        List<String> f = form().getOrDefault(name, List.of());
        return f.isEmpty() ? query.getOrDefault(name, List.of()) : f;
    }

    /** Trimmed value, or null when missing or blank. */
    public String optionalParam(String name) {
        String v = param(name);
        return v == null || v.isBlank() ? null : v.trim();
    }

    /**
     * Parses an optional enum parameter (null when blank). Before: ApiController and BoardController
     * each had their own copy of this try/valueOf/catch block for Priority.
     */
    public <E extends Enum<E>> E enumParam(String name, Class<E> type) {
        String v = optionalParam(name);
        if (v == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, v.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown " + name + " '" + v + "'");
        }
    }

    public String requireParam(String name) {
        String v = param(name);
        if (v == null || v.isBlank()) {
            throw new BadRequestException("Missing parameter '" + name + "'");
        }
        return v.trim();
    }

    public LocalDate dateParam(String name, LocalDate fallback) {
        String v = param(name);
        if (v == null || v.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(v.trim());
        } catch (DateTimeParseException e) {
            throw new BadRequestException("Parameter '" + name + "' must be a date like 2026-11-05");
        }
    }

    public long longParam(String name) {
        try {
            return Long.parseLong(requireParam(name));
        } catch (NumberFormatException e) {
            throw new BadRequestException("Parameter '" + name + "' must be a number");
        }
    }

    private Map<String, List<String>> form() {
        if (form == null) {
            String ct = header("Content-Type");
            form = ct != null && ct.startsWith("application/x-www-form-urlencoded")
                    ? parse(new String(body, StandardCharsets.UTF_8))
                    : Map.of();
        }
        return form;
    }

    private static Map<String, List<String>> parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> m = new LinkedHashMap<>();
        for (String pair : raw.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            String[] kv = pair.split("=", 2);
            String k = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
            String v = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
            m.computeIfAbsent(k, x -> new ArrayList<>()).add(v);
        }
        m.replaceAll((k, v) -> Collections.unmodifiableList(v));
        return m;
    }

    static byte[] readBody(InputStream in) throws IOException {
        byte[] data = in.readNBytes(MAX_BODY_BYTES + 1);
        if (data.length > MAX_BODY_BYTES) {
            throw new PayloadTooLargeException();
        }
        return data;
    }

    /** Request body above {@link #MAX_BODY_BYTES} (HTTP 413). */
    public static class PayloadTooLargeException extends IOException {
        private static final long serialVersionUID = 1L;

        PayloadTooLargeException() {
            super("Request body too large");
        }
    }
}
