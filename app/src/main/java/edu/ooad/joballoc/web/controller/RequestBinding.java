package edu.ooad.joballoc.web.controller;

import edu.ooad.joballoc.domain.JobCriteria;
import edu.ooad.joballoc.domain.Priority;
import edu.ooad.joballoc.web.http.Request;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Turns request parameters into the objects the facade expects, in one place. Before: the board and
 * the REST controller each parsed date, priority and skill with their own copies of the same code.
 */
final class RequestBinding {

    private RequestBinding() { }

    /** {@code ?date=} or today. */
    static LocalDate date(Request req, Clock clock) {
        return req.dateParam("date", LocalDate.now(clock));
    }

    /** {@code ?date=&priority=&skill=} as a {@link JobCriteria}. */
    static JobCriteria jobCriteria(Request req, Clock clock) {
        return new JobCriteria(date(req, clock), req.enumParam("priority", Priority.class), req.optionalParam("skill"));
    }
}
