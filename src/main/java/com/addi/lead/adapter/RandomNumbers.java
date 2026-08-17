package com.addi.lead.adapter;

import com.addi.lead.port.Randomness;
import java.util.OptionalLong;
import java.util.Random;

/** The only place a seed is read. A run with no seed is a different run, which is the point. */
public record RandomNumbers(Random random) implements Randomness {

    public static RandomNumbers from(OptionalLong seed) {
        return new RandomNumbers(seed.isPresent() ? new Random(seed.getAsLong()) : new Random());
    }

    @Override
    public int nextInt(int bound) {
        return random.nextInt(bound);
    }
}
