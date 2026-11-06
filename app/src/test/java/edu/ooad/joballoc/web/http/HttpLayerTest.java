package edu.ooad.joballoc.web.http;

import edu.ooad.joballoc.business.ConcurrencyConflictException;
import edu.ooad.joballoc.business.NotFoundException;
import edu.ooad.joballoc.business.security.ForbiddenException;
import edu.ooad.joballoc.business.security.UnauthenticatedException;
import edu.ooad.joballoc.domain.AllocationRuleException;
import edu.ooad.joballoc.domain.Priority;
import edu.ooad.joballoc.persistence.dao.DataAccessException;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** The framework-free Request (built without a server, thanks to the ADAPTER) and the error table. */
public class HttpLayerTest {

    private static Request get(String query) {
        return new Request("get", "/board", query, new byte[0], Map.of("Cookie", "JA_SESSION=abc"));
    }

    @Test
    public void requestIsAPlainValueWithCaseInsensitiveHeaders() {
        Request r = get("priority=high&skill=%20&date=2026-11-05");
        assertEquals("GET", r.method());
        assertEquals("JA_SESSION=abc", r.header("cookie"));
        assertEquals("abc", r.cookie("JA_SESSION"));
        assertEquals(Priority.HIGH, r.enumParam("priority", Priority.class));
        assertNull("blank means absent", r.optionalParam("skill"));
        assertThrows(BadRequestException.class, () -> get("priority=urgent").enumParam("priority", Priority.class));
    }

    @Test
    public void formBodyWinsOverQuery() {
        Request r = new Request("POST", "/board/allocate", "jobId=fromQuery",
                "jobId=fromForm".getBytes(StandardCharsets.UTF_8),
                Map.of("Content-Type", "application/x-www-form-urlencoded"));
        assertEquals("fromForm", r.requireParam("jobId"));
    }

    @Test
    public void statusCodesCarryTheirProblemTypes() {
        assertEquals(409, HttpStatus.CONFLICT.code());
        assertEquals("/problems/conflict", HttpStatus.CONFLICT.problemType());
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, HttpStatus.of(422));
        assertTrue(HttpStatus.NOT_MODIFIED.hasNoBody());
    }

    @Test
    public void oneTableMapsExceptionsToStatusCodes() {
        assertEquals(HttpStatus.UNAUTHORIZED, ErrorMapper.statusOf(new UnauthenticatedException("x")));
        assertEquals(HttpStatus.FORBIDDEN, ErrorMapper.statusOf(new ForbiddenException("x")));
        assertEquals(HttpStatus.NOT_FOUND, ErrorMapper.statusOf(new NotFoundException("x")));
        assertEquals(HttpStatus.CONFLICT, ErrorMapper.statusOf(new ConcurrencyConflictException("x", null)));
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, ErrorMapper.statusOf(new AllocationRuleException("x")));
        assertEquals(HttpStatus.BAD_REQUEST,
                ErrorMapper.statusOf(new NumberFormatException("subclass of IllegalArgumentException")));
        assertEquals("unknown and persistence errors are 500", HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorMapper.statusOf(new DataAccessException("x")));
        assertTrue(ErrorMapper.isUserCorrectable(new AllocationRuleException("x")));
        assertFalse("forbidden is not shown as a flash message",
                ErrorMapper.isUserCorrectable(new ForbiddenException("x")));
    }
}
