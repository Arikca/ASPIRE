package com.example.evaluator.domain;

import java.util.List;

public record CaseEvaluation(
        String id,
        String status,
        int coverageScore,
        int relevanceScore,
        int bugCatchingScore,
        String explanation,
        List<String> mappedRequirements,
        List<String> missingAssertions,
        String hypothesizedBugThisWouldMiss,
        List<String> deterministicFindings,
        SuggestedTestCase suggestedReplacement,
        String rawModelOutput
) { }
