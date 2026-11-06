package edu.ooad.joballoc.web.http;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ADAPTER (GoF structural): converts between the JDK server's {@link HttpExchange} (the adaptee) and
 * this application's own {@link Request} / {@link Response} (the target interface).
 *
 * <p>Why: only this class imports {@code com.sun.net.httpserver}. Controllers, the router and the
 * front controller work with plain objects, so they can be unit-tested without starting a server,
 * and moving to Jetty or a servlet container means writing one new adapter, not editing controllers.
 */
public final class HttpExchangeAdapter {

    private HttpExchangeAdapter() { }

    /** Adaptee -> target: reads method, path, query, headers and a size-limited body. */
    public static Request toRequest(HttpExchange exchange) throws IOException {
        Map<String, String> headers = new HashMap<>();
        exchange.getRequestHeaders().forEach((name, values) -> {
            if (!values.isEmpty()) {
                headers.put(name, values.get(0));
            }
        });
        return new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                exchange.getRequestURI().getRawQuery(), Request.readBody(exchange.getRequestBody()), headers);
    }

    /** Target -> adaptee: copies status, headers and body to the exchange, then closes it. */
    public static void write(HttpExchange exchange, Response response) throws IOException {
        var h = exchange.getResponseHeaders();
        for (Map.Entry<String, List<String>> e : response.headers().entrySet()) {
            h.put(e.getKey(), List.copyOf(e.getValue()));
        }
        byte[] body = response.body();
        boolean noBody = HttpStatus.of(response.status()).hasNoBody()
                || "HEAD".equalsIgnoreCase(exchange.getRequestMethod());
        exchange.sendResponseHeaders(response.status(), noBody ? -1 : body.length);
        if (!noBody) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
        exchange.close();
    }
}
