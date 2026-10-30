package com.example.evaluator.domain;

import java.util.List;

/** A runnable replacement test proposed by the judge or supplied by a demo fixture. */
public record SuggestedTestCase(
        String id,
        String description,
        List<String> steps,
        String expectedResult
) { }
