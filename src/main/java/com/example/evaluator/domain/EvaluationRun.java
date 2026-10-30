package com.example.evaluator.domain;

import java.time.Instant;
import java.util.List;

public record EvaluationRun(
        String runId,
        Instant createdAt,
        List<CaseEvaluation> testCases,
        SuiteSummary summary
) { }
