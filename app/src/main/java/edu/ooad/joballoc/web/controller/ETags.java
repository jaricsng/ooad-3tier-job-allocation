package edu.ooad.joballoc.web.controller;

import edu.ooad.joballoc.web.http.BadRequestException;

/** Strong ETags derived from an entity's @Version: {@code "3"}. */
final class ETags {

    private ETags() { }

    static String of(long version) {
        return "\"" + version + "\"";
    }

    static long version(String etag) {
        String v = etag.trim();
        if (v.startsWith("W/")) {
            throw new BadRequestException("Weak ETags cannot be used with If-Match");
        }
        try {
            return Long.parseLong(v.replace("\"", ""));
        } catch (NumberFormatException e) {
            throw new BadRequestException("If-Match must be an ETag returned by GET /api/v1/jobs/{id}, e.g. \"3\"");
        }
    }
}
