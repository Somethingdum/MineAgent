package com.mineagent.engine.planning;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** SMH plan 69, MA4.2: the brief is never longer than 600 characters and lists a hostile 10 blocks away. */
final class SituationBriefTest {
    @Test void listsAHostileTenBlocksAway() {
        String brief = SituationBrief.build("mining iron", "g2 (active) get 64 oak logs: 3 steps, short 24 oak log",
                List.of(new SituationBrief.Hostile("zombie", 10)), List.of("light the tunnel first"));
        assertTrue(brief.contains("zombie 10"), brief);
        assertTrue(brief.contains("Doing: mining iron."), brief);
        assertTrue(brief.contains("Goal: g2 (active) get 64 oak logs"), brief);
        assertTrue(brief.contains("Lessons: light the tunnel first."), brief);
    }

    @Test void neverLongerThan600WhateverItIsGiven() {
        String huge = "x".repeat(5000);
        List<SituationBrief.Hostile> many = new ArrayList<>();
        for (int i = 0; i < 40; i++) many.add(new SituationBrief.Hostile("cave_spider_" + "y".repeat(30), i));
        List<String> lessons = List.of(huge, huge, huge, huge);
        String brief = SituationBrief.build(huge, huge, many, lessons);
        assertTrue(brief.length() <= SituationBrief.MAX, "length " + brief.length());
        assertTrue(brief.contains("Hostiles"), "hostiles keep their room");
        for (int n = 0; n < 200; n += 7) {
            String b = SituationBrief.build("d".repeat(n), "g".repeat(n * 2), many.subList(0, n % 7), List.of("l".repeat(n * 3), "m".repeat(n)));
            assertTrue(b.length() <= SituationBrief.MAX, "length " + b.length() + " at " + n);
        }
    }

    @Test void saysWhenNothingIsNear() {
        assertTrue(SituationBrief.build(null, null, List.of(), List.of()).contains("No hostiles within 16 blocks."));
    }
}
