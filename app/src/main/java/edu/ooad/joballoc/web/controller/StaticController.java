package edu.ooad.joballoc.web.controller;

import edu.ooad.joballoc.web.http.HttpStatus;
import edu.ooad.joballoc.web.http.Request;
import edu.ooad.joballoc.web.http.Response;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/** Serves the few static files from the classpath (an allow-list, so no path traversal is possible). */
public class StaticController {

    private static final Map<String, String> FILES = Map.of(
            "app.css", "text/css; charset=utf-8",
            "bootstrap.min.css", "text/css; charset=utf-8");

    public Response serve(Request req) throws IOException {
        String name = req.pathVar("file");
        String type = FILES.get(name);
        if (type == null) {
            return Response.text(HttpStatus.NOT_FOUND, "Not found");
        }
        try (InputStream in = StaticController.class.getResourceAsStream("/static/" + name)) {
            if (in == null) {
                return Response.text(HttpStatus.NOT_FOUND, "Not found");
            }
            return new Response().header("Content-Type", type).header("Cache-Control", "public, max-age=300")
                    .body(in.readAllBytes());
        }
    }
}
