package com.addi.lead.domain;

import java.util.Optional;

/** Key of the local database, the fixtures, the bureau cache and the case id. Never a bare String. */
public record NationalId(String value) {

    /**
     * The syntactic rule only has to catch empty input: a well formed id absent from the database is
     * a business rejection, not an input error (`docs/assumptions.md`).
     */
    public static Optional<NationalId> parse(String token) {
        return token.isEmpty() || token.chars().anyMatch(Character::isWhitespace)
                ? Optional.empty()
                : Optional.of(new NationalId(token));
    }
}
