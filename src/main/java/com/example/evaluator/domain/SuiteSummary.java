package com.example.evaluator.domain;

import java.util.List;

public record SuiteSummary(
        int requirementCoveragePercent,
        List<String> coveredRequirements,
        List<String> missingRequirements,
        double meanCaseQuality,
        double overallScore
) { }
