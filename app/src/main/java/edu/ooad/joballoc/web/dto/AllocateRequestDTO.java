package edu.ooad.joballoc.web.dto;

/** Body of POST /api/v1/allocations. */
public record AllocateRequestDTO(String jobId, String workerId, Long jobVersion) { }
