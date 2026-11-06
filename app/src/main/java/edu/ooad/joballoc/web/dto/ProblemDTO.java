package edu.ooad.joballoc.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import edu.ooad.joballoc.web.http.HttpStatus;

/**
 * Error body of the REST API: RFC 9457 "Problem Details for HTTP APIs", media type
 * {@code application/problem+json}. Before: a home-made {@code {status, message}} object that
 * every client had to learn; now generic clients and gateways understand it.
 *
 * @param type      URI reference identifying the problem type, e.g. {@code /problems/conflict}
 * @param title     short, human-readable summary of the type (the same for every occurrence)
 * @param status    HTTP status code
 * @param detail    explanation of this occurrence
 * @param instance  the request path
 * @param requestId extension member: the correlation ID, also in the X-Request-Id header and the server log
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProblemDTO(String type, String title, int status, String detail, String instance, String requestId) {

    public static ProblemDTO of(HttpStatus status, String detail, String instance, String requestId) {
        return new ProblemDTO(status.problemType(), status.title(), status.code(), detail, instance, requestId);
    }
}
