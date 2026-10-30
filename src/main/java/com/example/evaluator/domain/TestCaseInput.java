package com.example.evaluator.domain;

import java.util.List;

public record TestCaseInput(String id, String description, List<String> steps, String expectedResult) { }
