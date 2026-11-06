package edu.ooad.joballoc.web.controller;

import edu.ooad.joballoc.web.http.Request;
import edu.ooad.joballoc.web.http.Response;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * GET /api/v1/openapi.yaml: the API contract (OpenAPI 3.1) served by the API itself, so clients and
 * tools (Swagger UI, code generators, Postman) always get the version that matches the running code.
 */
public class ApiDocsController {

    static final String SPEC = "/api/openapi.yaml";

    private final byte[] spec;

    public ApiDocsController() {
        try (InputStream in = ApiDocsController.class.getResourceAsStream(SPEC)) {
            if (in == null) {
                throw new IllegalStateException("OpenAPI document " + SPEC + " missing from the classpath");
            }
            this.spec = in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Response openapi(Request req) {
        return new Response().header("Content-Type", "application/yaml; charset=utf-8")
                .header("Cache-Control", "public, max-age=300").body(spec.clone());
    }
}
