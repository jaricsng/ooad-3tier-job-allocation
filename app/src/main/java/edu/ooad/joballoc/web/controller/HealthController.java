package edu.ooad.joballoc.web.controller;

import edu.ooad.joballoc.business.event.WorkloadMetricsListener;
import edu.ooad.joballoc.business.port.DatabaseHealth;
import edu.ooad.joballoc.web.http.HttpStatus;
import edu.ooad.joballoc.web.http.Request;
import edu.ooad.joballoc.web.http.Response;

import java.util.LinkedHashMap;
import java.util.Map;

/** GET /health: liveness plus a database check, for the runbook and load balancers. Public, no secrets. */
public class HealthController {

    private final DatabaseHealth database;
    private final WorkloadMetricsListener metrics;
    private final String version;

    public HealthController(DatabaseHealth database, WorkloadMetricsListener metrics, String version) {
        this.database = database;
        this.metrics = metrics;
        this.version = version;
    }

    public Response health(Request req) {
        boolean dbUp = database.isUp();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", dbUp ? "UP" : "DOWN");
        body.put("database", dbUp ? "UP" : "DOWN");
        body.put("version", version);
        body.put("eventsSinceStart", metrics.snapshot());
        return Response.json(dbUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE, body);
    }
}
