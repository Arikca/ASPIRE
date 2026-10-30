package com.example.evaluator.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import tools.jackson.databind.JsonNode;

import java.util.List;

/** JsonNode intentionally preserves malformed generated cases for deterministic reporting. */
public record EvaluationRequestDto(
        @NotBlank String spec,
        @NotEmpty List<JsonNode> testCases
) { }
