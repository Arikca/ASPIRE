package com.example.evaluator.domain;

import java.util.List;

/** Shape enforced by Spring AI provider-native structured output. Scores must be whole percentages. */
public record LlmTestCaseScore(
        List<String> mappedRequirements,
        int coverageScore,
        int relevanceScore,
        int bugCatchingScore,
        String explanation,
        List<String> missingAssertions,
        String hypothesizedBugThisWouldMiss,
        SuggestedTestCase suggestedReplacement
) { }
