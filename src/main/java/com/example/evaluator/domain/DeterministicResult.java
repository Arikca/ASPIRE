package com.example.evaluator.domain;

import java.util.List;

public record DeterministicResult(
        boolean structurallyValid,
        TestCaseInput testCase,
        List<String> findings,
        List<String> matchedAtoms
) { }
