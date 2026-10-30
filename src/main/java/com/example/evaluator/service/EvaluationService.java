package com.example.evaluator.service;

import com.example.evaluator.domain.CaseEvaluation;
import com.example.evaluator.domain.DeterministicResult;
import com.example.evaluator.domain.EvaluationRequestDto;
import com.example.evaluator.domain.EvaluationRun;
import com.example.evaluator.domain.EvaluationCapabilities;
import com.example.evaluator.domain.LlmTestCaseScore;
import com.example.evaluator.domain.RepeatEvaluation;
import com.example.evaluator.domain.SpecAtom;
import com.example.evaluator.domain.SuggestedTestCase;
import com.example.evaluator.domain.SuiteSummary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class EvaluationService {
    private final RequirementExtractor requirementExtractor;
    private final DeterministicTestCaseValidator validator;
    private final TestCaseQualityEvaluator qualityEvaluator;
    private final ArtifactStore artifactStore;
    private final int fallbackThreshold;

    public EvaluationService(RequirementExtractor requirementExtractor, DeterministicTestCaseValidator validator,
                             TestCaseQualityEvaluator qualityEvaluator, ArtifactStore artifactStore,
                             @Value("${evaluator.ci.fallback-threshold}") int fallbackThreshold) {
        this.requirementExtractor = requirementExtractor;
        this.validator = validator;
        this.qualityEvaluator = qualityEvaluator;
        this.artifactStore = artifactStore;
        this.fallbackThreshold = fallbackThreshold;
    }

    public EvaluationRun evaluate(EvaluationRequestDto request) {
        List<SpecAtom> atoms = requirementExtractor.extract(request.spec());
        List<CaseEvaluation> cases = new ArrayList<>();
        Map<String, TestCaseQualityEvaluator.ModelJudgement> judgements = new HashMap<>();
        Set<String> seenIds = new HashSet<>();
        for (var rawCase : request.testCases()) {
            DeterministicResult deterministic = validator.validate(rawCase, atoms);
            String id = deterministic.testCase() == null ? "unknown" : deterministic.testCase().id();
            if (!seenIds.add(id)) {
                cases.add(invalid(id, List.of("Duplicate test case id: " + id)));
                continue;
            }
            if (!deterministic.structurallyValid()) {
                cases.add(invalid(id, deterministic.findings()));
                continue;
            }
            TestCaseQualityEvaluator.ModelJudgement judgement = qualityEvaluator.judge(request.spec(), deterministic.testCase(), atoms);
            LlmTestCaseScore model = judgement.score();
            int coverage = model.coverageScore();
            int relevance = model.relevanceScore();
            int bugCatching = model.bugCatchingScore();
            for (String finding : deterministic.findings()) {
                if (finding.contains("5-second")) coverage = Math.min(coverage, 40);
                if (finding.contains("both the wallet")) bugCatching = Math.min(bugCatching, 40);
                if (finding.contains("second-owner")) {
                    relevance = Math.min(relevance, 45);
                    bugCatching = Math.min(bugCatching, 45);
                }
                if (finding.contains("debit before credit")) bugCatching = Math.min(bugCatching, 45);
            }
            cases.add(new CaseEvaluation(id, "SCORED", coverage, relevance, bugCatching, model.explanation(),
                    model.mappedRequirements(), model.missingAssertions(), model.hypothesizedBugThisWouldMiss(),
                    deterministic.findings(), model.suggestedReplacement(), judgement.rawOutput()));
            judgements.put(id, judgement);
        }
        EvaluationRun run = new EvaluationRun(UUID.randomUUID().toString(), Instant.now(), cases, summarize(cases, atoms));
        artifactStore.persist(request, run, judgements);
        return run;
    }

    public RepeatEvaluation repeat(EvaluationRequestDto request, int count) {
        List<EvaluationRun> runs = new ArrayList<>();
        List<Double> scores = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            EvaluationRun run = evaluate(request);
            runs.add(run);
            scores.add(run.summary().overallScore());
        }
        double mean = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = scores.stream().mapToDouble(score -> Math.pow(score - mean, 2)).average().orElse(0);
        double deviation = Math.sqrt(variance);
        double range = scores.stream().max(Comparator.naturalOrder()).orElse(0d)
                - scores.stream().min(Comparator.naturalOrder()).orElse(0d);
        // A gate belongs to offline calibration, not to the suite currently being judged: a low-quality
        // candidate would otherwise lower its own passing bar. The configured value is set from labelled
        // accepted/rejected suites and exposed beside this run's observed variance for auditability.
        double threshold = fallbackThreshold;
        RepeatEvaluation repeat = new RepeatEvaluation(runs, scores, round(mean), round(deviation), round(range), threshold,
                "CI threshold is an offline-calibrated configuration. Keep it above the upper noisy bound of known-bad suites and below the lower noisy bound of known-good suites.");
        artifactStore.persistRepeat(repeat);
        return repeat;
    }

    private CaseEvaluation invalid(String id, List<String> findings) {
        return new CaseEvaluation(id, "VALIDATION_FAILED", 0, 0, 0,
                "No LLM score was requested because the generated test case is structurally invalid.",
                List.of(), List.of(), "Unknown: invalid test case", findings,
                new SuggestedTestCase(id + "-repair", "Repair the generated test case structure",
                        List.of("Provide non-blank id, description, string-only steps, and expectedResult fields.",
                                "Then rerun the evaluator so an LLM can assess semantic quality."),
                        "The replacement is structurally valid and can be evaluated."), null);
    }

    public EvaluationCapabilities capabilities() {
        return new EvaluationCapabilities(
                List.of(
                        "JSON shape, non-blank fields, string-only non-empty steps, and duplicate IDs.",
                        "Known spec-rule floors: 5-second timeout boundary, both rollback sides, second-owner approval before settlement, and visible credit/debit ordering.",
                        "Suite requirement-union coverage and score aggregation. Structural failures score zero without a model call."),
                List.of(
                        "Map each test to requirement IDs extracted from the supplied spec.",
                        "Score coverage, semantic relevance, and ability to catch a concrete implementation bug.",
                        "Explain missing assertions, hypothesize a missed bug, and return a structured corrected replacement test."),
                List.of(
                        "TestCaseQualityEvaluator implements Spring AI Evaluator and accepts EvaluationRequest/EvaluationResponse.",
                        "The endpoint uses ChatClient provider-native structured output plus Spring AI schema retry.",
                        "Raw output is independently validated against src/main/resources/schema/llm-test-case-score.json and persisted with exact prompts."));
    }

    private SuiteSummary summarize(List<CaseEvaluation> cases, List<SpecAtom> atoms) {
        Set<String> knownAtoms = atoms.stream().map(SpecAtom::id).collect(java.util.stream.Collectors.toSet());
        List<String> covered = cases.stream().flatMap(c -> c.mappedRequirements().stream())
                .filter(knownAtoms::contains).distinct().sorted().toList();
        List<String> missing = atoms.stream().map(SpecAtom::id).filter(atom -> !covered.contains(atom)).toList();
        double meanQuality = cases.stream()
                .mapToDouble(c -> (c.coverageScore() + c.relevanceScore() + c.bugCatchingScore()) / 3d)
                .average().orElse(0);
        double coverage = atoms.isEmpty() ? 0 : (covered.size() * 100d / atoms.size());
        return new SuiteSummary((int) Math.round(coverage), covered, missing, round(meanQuality), round(0.4 * coverage + 0.6 * meanQuality));
    }

    private double round(double value) {
        return Math.round(value * 100d) / 100d;
    }
}
