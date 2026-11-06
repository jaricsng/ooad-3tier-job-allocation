package edu.ooad.joballoc.web.dto;

import java.util.List;

/** Body of POST /api/v1/allocations/auto. {@code policy} is optional (default SKILL_MATCH). */
public record AutoAllocateRequestDTO(List<String> jobIds, String policy) { }
