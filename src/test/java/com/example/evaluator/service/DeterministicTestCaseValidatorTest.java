package com.example.evaluator.service;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicTestCaseValidatorTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RequirementExtractor extractor = new RequirementExtractor();
    private final DeterministicTestCaseValidator validator = new DeterministicTestCaseValidator(objectMapper);
    private final String spec = "FX rate is locked. Wallet is credited before funding is debited. Timeout after 5 seconds rolls back. Second owner approval is required before settling.";

    @Test
    void rejectsMalformedApprovalFixtureWithoutCallingAModel() throws Exception {
        var raw = objectMapper.readTree("""
                {"id":"tc-4","description":"approval","steps":["start", {"expectedResult":"screen"}]}
                """);

        var result = validator.validate(raw, extractor.extract(spec));

        assertThat(result.structurallyValid()).isFalse();
        assertThat(result.findings()).anyMatch(finding -> finding.contains("expectedResult"));
        assertThat(result.findings()).anyMatch(finding -> finding.contains("Every step"));
    }

    @Test
    void flagsATimeoutCaseThatOmitsTheFiveSecondBoundary() throws Exception {
        var raw = objectMapper.readTree("""
                {"id":"tc-3","description":"rollback on timeout","steps":["start","simulate timeout"],"expectedResult":"wallet unchanged and funding source not debited"}
                """);

        var result = validator.validate(raw, extractor.extract(spec));

        assertThat(result.structurallyValid()).isTrue();
        assertThat(result.findings()).anyMatch(finding -> finding.contains("5-second"));
    }

    @Test
    void recognizesTheProductSpecPhraseTimesOutAsATimeoutRequirement() {
        var atoms = extractor.extract("If the provider callback times out after 5 seconds, the transaction is rolled back.");

        assertThat(atoms).extracting(atom -> atom.id()).contains("timeout_rollback");
    }
}
