package com.example.evaluator.domain;

import java.util.List;

public record RepeatEvaluation(
        List<EvaluationRun> runs,
        List<Double> suiteScores,
        double mean,
        double standardDeviation,
        double range,
        double suggestedCiThreshold,
        String calibrationNote
) { }
