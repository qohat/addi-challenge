package com.addi.lead.domain;

import java.time.LocalDate;

/** A CRM record: it exists locally and its fields are well formed. Nothing has been checked yet. */
public record Lead(NationalId id, String firstName, String lastName, LocalDate birthDate, Email email) {}
