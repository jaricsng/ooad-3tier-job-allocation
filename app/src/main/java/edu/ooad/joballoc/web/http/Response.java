package edu.ooad.joballoc.web.http;

import edu.ooad.joballoc.web.Json;
import edu.ooad.joballoc.web.dto.ProblemDTO;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** An HTTP response under construction. */
public final class Response {

    private int status = HttpStatus.OK.code();
    private byte[] body = new byte[0];
    private final Map<String, List<String>> headers = new LinkedHashMap<>();

    public static Response html(String html) {
        return new Response().header("Content-Type", "text/html; charset=utf-8").body(html);
    }

    /** Serialises {@code body} (a DTO, list or map) to JSON. Before: every caller wrote Json.write(...) itself. */
    public static Response json(HttpStatus status, Object body) {
        return new Response().status(status).header("Content-Type", "application/json; charset=utf-8")
                .body(Json.write(body));
    }

    /** RFC 9457 Problem Details ({@code application/problem+json}). */
    public static Response problem(ProblemDTO problem) {
        return new Response().status(HttpStatus.of(problem.status()))
                .header("Content-Type", "application/problem+json; charset=utf-8").body(Json.write(problem));
    }

    public static Response text(HttpStatus status, String text) {
        return new Response().status(status).header("Content-Type", "text/plain; charset=utf-8").body(text);
    }

    /** POST-Redirect-GET: a 303 so the browser follows with GET and a refresh never re-posts. */
    public static Response redirect(String location) {
        return new Response().status(HttpStatus.SEE_OTHER).header("Location", location);
    }

    public int status() { return status; }

    public Response status(HttpStatus s) {
        this.status = s.code();
        return this;
    }

    public Response header(String name, String value) {
        headers.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
        return this;
    }

    public byte[] body() { return body; }

    public Response body(String s) {
        this.body = s.getBytes(StandardCharsets.UTF_8);
        return this;
    }

    public Response body(byte[] b) {
        this.body = b;
        return this;
    }

    public Map<String, List<String>> headers() { return headers; }

    /** Replaces any existing values of the header. */
    public Response setHeader(String name, String value) {
        headers.put(name, new ArrayList<>(List.of(value)));
        return this;
    }

    /** A deep copy (headers and body), so a stored response can be replayed safely. */
    public Response copy() {
        Response r = new Response().status(HttpStatus.of(status)).body(body.clone());
        headers.forEach((k, v) -> r.headers.put(k, new ArrayList<>(v)));
        return r;
    }

    public boolean hasHeader(String name) {
        return headers.containsKey(name);
    }
}
