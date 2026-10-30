package com.example.evaluator.service;

import com.example.evaluator.domain.DeterministicResult;
import com.example.evaluator.domain.SpecAtom;
import com.example.evaluator.domain.TestCaseInput;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class DeterministicTestCaseValidator {
    private final ObjectMapper objectMapper;

    public DeterministicTestCaseValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public DeterministicResult validate(JsonNode raw, List<SpecAtom> atoms) {
        List<String> findings = new ArrayList<>();
        if (!raw.isObject()) {
            return new DeterministicResult(false, null, List.of("Test case must be a JSON object."), List.of());
        }
        String id = text(raw, "id", findings, true);
        String description = text(raw, "description", findings, true);
        String expectedResult = text(raw, "expectedResult", findings, true);
        List<String> steps = steps(raw, findings);
        if (!findings.isEmpty()) {
            return new DeterministicResult(false, safeCase(id, description, steps, expectedResult), findings, List.of());
        }

        TestCaseInput testCase = new TestCaseInput(id, description, steps, expectedResult);
        String allText = (description + " " + String.join(" ", steps) + " " + expectedResult).toLowerCase(Locale.ROOT);
        List<String> matched = matchAtoms(allText, atoms);
        applyDomainFloors(testCase, allText, findings);
        return new DeterministicResult(true, testCase, findings, matched);
    }

    private List<String> steps(JsonNode raw, List<String> findings) {
        JsonNode steps = raw.get("steps");
        if (steps == null || !steps.isArray() || steps.isEmpty()) {
            findings.add("steps must be a non-empty array of strings.");
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode step : steps) {
            if (!step.isTextual() || step.asText().isBlank()) {
                findings.add("Every step must be a non-blank string.");
                return List.of();
            }
            values.add(step.asText());
        }
        return values;
    }

    private String text(JsonNode raw, String field, List<String> findings, boolean required) {
        JsonNode value = raw.get(field);
        if (required && (value == null || !value.isTextual() || value.asText().isBlank())) {
            findings.add(field + " must be a non-blank string.");
            return "";
        }
        return value == null ? "" : value.asText();
    }

    private TestCaseInput safeCase(String id, String description, List<String> steps, String expected) {
        return new TestCaseInput(id.isBlank() ? "unknown" : id, description, steps, expected);
    }

    private List<String> matchAtoms(String text, List<SpecAtom> atoms) {
        List<String> matched = new ArrayList<>();
        for (SpecAtom atom : atoms) {
            boolean match = switch (atom.id()) {
                case "fx_lock" -> text.contains("fx") || text.contains("rate");
                case "credit_before_debit" -> (text.contains("credit") || text.contains("wallet")) && (text.contains("debit") || text.contains("funding"));
                case "timeout_rollback" -> text.contains("timeout") || text.contains("rollback");
                case "dual_approval" -> text.contains("approval") || text.contains("owner");
                default -> true;
            };
            if (match) matched.add(atom.id());
        }
        return matched;
    }

    private void applyDomainFloors(TestCaseInput testCase, String allText, List<String> findings) {
        if ((allText.contains("timeout") || allText.contains("rollback"))
                && !allText.matches("(?s).*\\b(5\\s*(seconds?|secs?|s)|5000\\s*ms)\\b.*")) {
            findings.add("Timeout/rollback test does not assert the specified 5-second timeout.");
        }
        if ((allText.contains("timeout") || allText.contains("rollback"))
                && (!(allText.contains("wallet") || allText.contains("destination"))
                || !(allText.contains("funding") || allText.contains("debit")))) {
            findings.add("Rollback test must check both the wallet and the funding source.");
        }
        if ((allText.contains("approval") || allText.contains("15,000") || allText.contains("15000"))
                && (!(allText.contains("second") && allText.contains("owner"))
                || !(allText.contains("settle") || allText.contains("settlement") || allText.contains("not settle")))) {
            findings.add("Approval test must assert second-owner approval and that settlement is blocked until approval.");
        }
        if ((allText.contains("credit") || allText.contains("wallet")) && (allText.contains("debit") || allText.contains("funding"))) {
            int creditStep = firstStep(testCase.steps(), Set.of("credit", "wallet", "destination"));
            int debitStep = firstStep(testCase.steps(), Set.of("debit", "funding"));
            if (creditStep >= 0 && debitStep >= 0 && creditStep > debitStep) {
                findings.add("Steps place debit before credit; they do not exercise the required ordering.");
            }
        }
    }

    private int firstStep(List<String> steps, Set<String> terms) {
        for (int i = 0; i < steps.size(); i++) {
            String step = steps.get(i).toLowerCase(Locale.ROOT);
            if (terms.stream().anyMatch(step::contains)) return i;
        }
        return -1;
    }
}
