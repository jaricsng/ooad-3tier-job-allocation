package edu.ooad.joballoc.web.http;

import edu.ooad.joballoc.business.ConcurrencyConflictException;
import edu.ooad.joballoc.business.NotFoundException;
import edu.ooad.joballoc.business.security.ForbiddenException;
import edu.ooad.joballoc.business.security.UnauthenticatedException;
import edu.ooad.joballoc.domain.AllocationRuleException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The single table from exception type to HTTP status.
 *
 * <p>Before: FrontController had an {@code if (e instanceof ...) else if ...} chain, and
 * BoardController repeated the list of "expected" exceptions in a multi-catch. Adding a business
 * exception meant editing both (shotgun surgery). After: one table, used by both.
 */
public final class ErrorMapper {

    private static final Map<Class<? extends Throwable>, HttpStatus> STATUS = new LinkedHashMap<>();

    static {
        STATUS.put(UnauthenticatedException.class, HttpStatus.UNAUTHORIZED);
        STATUS.put(ForbiddenException.class, HttpStatus.FORBIDDEN);
        STATUS.put(NotFoundException.class, HttpStatus.NOT_FOUND);
        STATUS.put(ConcurrencyConflictException.class, HttpStatus.CONFLICT);
        STATUS.put(AllocationRuleException.class, HttpStatus.UNPROCESSABLE_CONTENT);
        STATUS.put(PreconditionFailedException.class, HttpStatus.PRECONDITION_FAILED);
        STATUS.put(IdempotentHandler.ConflictException.class, HttpStatus.CONFLICT);
        STATUS.put(IdempotentHandler.UnprocessableException.class, HttpStatus.UNPROCESSABLE_CONTENT);
        STATUS.put(BadRequestException.class, HttpStatus.BAD_REQUEST);
        STATUS.put(IllegalArgumentException.class, HttpStatus.BAD_REQUEST);
    }

    /** Errors the user can fix on the same page (wrong input, stale data, a rule): shown as a message. */
    private static final Set<HttpStatus> USER_CORRECTABLE = Set.of(HttpStatus.BAD_REQUEST, HttpStatus.NOT_FOUND,
            HttpStatus.CONFLICT, HttpStatus.UNPROCESSABLE_CONTENT);

    private ErrorMapper() { }

    /** HTTP status for an exception, matching the nearest registered superclass; 500 when unknown. */
    public static HttpStatus statusOf(Throwable e) {
        for (Class<?> c = e.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            HttpStatus status = STATUS.get(c);
            if (status != null) {
                return status;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    public static boolean isUserCorrectable(Throwable e) {
        return USER_CORRECTABLE.contains(statusOf(e));
    }
}
