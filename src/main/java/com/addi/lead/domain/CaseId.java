package com.addi.lead.domain;

/** A case is {@code <nationalId>-<timestamp>} and is also its filename. Spec 06 mints them. */
public record CaseId(String value) {}
