package com.mineagent.engine.planning;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** SMH plan 69, MA4.1: big plans and repeated failures get a second read; a fix comes back; it cannot loop. */
final class PlanReviewTest {
    @Test void aPlanMissingAStepComesBackWithAFix() {
        String reply = "<think>no pickaxe</think>{\"ok\":false,\"fix\":\"Craft a stone pickaxe before mining the iron.\"}";
        assertEquals("Craft a stone pickaxe before mining the iron.", PlanReview.fix(reply).orElseThrow());
        assertTrue(PlanReview.fix("{\"ok\":true}").isEmpty());
        assertTrue(PlanReview.fix("{\"ok\":false}").isEmpty(), "no fix given: nothing to act on");
        assertTrue(PlanReview.fix("sure, looks good").isEmpty());
    }

    @Test void onlyBigPlansAndTheSecondFailureAreReviewed() {
        PlanReview review = new PlanReview();
        assertFalse(review.onPlan("get iron", 3));
        assertTrue(review.onPlan("get iron", 4));
        assertFalse(review.onRepeatedFailure("build a house"));
        assertTrue(review.onRepeatedFailure("build a house"));
        assertFalse(review.onRepeatedFailure("build a house"));
        assertTrue(review.onRepeatedFailure("build a house"));
    }

    @Test void atMostThreeReviewsPerGoal() {
        PlanReview review = new PlanReview();
        for (int i = 0; i < PlanReview.PER_GOAL; i++) assertTrue(review.onPlan("Get Iron", 5));
        assertFalse(review.onPlan("get iron", 5));
        assertTrue(review.onPlan("another goal", 5));
    }
}
