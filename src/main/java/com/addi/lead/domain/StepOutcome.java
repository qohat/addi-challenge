package com.addi.lead.domain;

/**
 * A marker with no cases of its own, so the collapse in {@link Decisions#terminal} is one switch
 * instead of four. Nothing is ever matched at this level except that function, and it should never
 * gain a method.
 */
public sealed interface StepOutcome permits RegistryOutcome, JudicialOutcome, BureauOutcome, ScoreOutcome {}
