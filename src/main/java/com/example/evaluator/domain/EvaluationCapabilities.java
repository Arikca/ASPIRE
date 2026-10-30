package com.example.evaluator.domain;

import java.util.List;

/** Documents the stable checks versus the semantic LLM-as-a-judge responsibilities. */
public record EvaluationCapabilities(
        List<String> deterministic,
        List<String> llm,
        List<String> springAiIntegration
) { }
