package com.example.evaluator.service;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.example.evaluator.domain.LlmTestCaseScore;
import com.example.evaluator.domain.SpecAtom;
import com.example.evaluator.domain.TestCaseInput;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.evaluation.Evaluator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Assignment-specific evaluator that still plugs into Spring AI's Evaluator contract. */
@Component
public class TestCaseQualityEvaluator implements Evaluator {
    private static final String SYSTEM = """
            You are a strict software test-quality evaluator. Evaluate only against the supplied product specification.
            Score each field from 0 to 100. Coverage is how completely this one test exercises each mapped requirement.
            Relevance is whether its steps and oracle test the specified behaviour rather than a superficial proxy.
            Bug-catching is whether a realistic implementation defect would make this test fail.
            Do not reward merely displaying a value or screen when the specification requires a transactional invariant.
            `mappedRequirements` must contain only IDs that this test case explicitly attempts to exercise;
            it must not contain IDs mentioned only in `missingAssertions` or the explanation. Include every
            explicitly attempted requirement even when coverage is partial. Explain a concrete missed assertion
            and a concrete bug this case would miss.
            Always provide a complete corrected replacement test with a descriptive id, executable steps, and an observable oracle.
            Return JSON matching the requested schema and no prose outside it.
            """;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final Schema responseSchema;

    public TestCaseQualityEvaluator(ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper,
                                    @Value("${spring.ai.openai.api-key:}") String apiKey) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.responseSchema = loadResponseSchema();
    }

    @Override
    public EvaluationResponse evaluate(EvaluationRequest request) {
        ModelJudgement judgement = judge(request.getUserText(), request.getResponseContent(),
                request.getDataList().stream().map(Document::getText).toList());
        LlmTestCaseScore score = judgement.score();
        float overall = (score.coverageScore() + score.relevanceScore() + score.bugCatchingScore()) / 300f;
        return new EvaluationResponse(overall >= 0.65f, overall, score.explanation(), Map.of(
                "mappedRequirements", score.mappedRequirements(),
                "missingAssertions", score.missingAssertions(),
                "hypothesizedBugThisWouldMiss", score.hypothesizedBugThisWouldMiss(),
                "suggestedReplacement", score.suggestedReplacement(),
                "rawModelOutput", judgement.rawOutput()));
    }

    public ModelJudgement judge(String spec, TestCaseInput testCase, List<SpecAtom> atoms) {
        try {
            return judge(spec, objectMapper.writeValueAsString(testCase),
                    atoms.stream().map(atom -> atom.id() + ": " + atom.requirement()).toList());
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize test case for model evaluation", exception);
        }
    }

    private ModelJudgement judge(String spec, String testCaseJson, List<String> atomContext) {
        if (apiKey == null || apiKey.isBlank()) throw new MissingOpenAiKeyException();
        String user = """
                PRODUCT SPECIFICATION:
                %s

                REQUIREMENT IDS (map only these):
                %s

                GENERATED TEST CASE JSON:
                %s
                """.formatted(spec, String.join("\n", atomContext), testCaseJson);
        ResponseEntity<ChatResponse, LlmTestCaseScore> response = chatClient.prompt()
                .system(SYSTEM)
                .user(user)
                .call()
                .responseEntity(LlmTestCaseScore.class, options -> options
                        .useProviderStructuredOutput()
                        .validateSchema());
        String raw = response.response().getResult().getOutput().getText();
        validate(response.entity(), raw);
        return new ModelJudgement(response.entity(), raw, SYSTEM, user);
    }

    private void validate(LlmTestCaseScore score, String raw) {
        if (score == null || raw == null || raw.isBlank()) {
            throw new InvalidModelOutputException("The model response was empty or could not be parsed as the score schema.");
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (!node.isObject()) throw new InvalidModelOutputException("Model response is not a JSON object.");
            List<com.networknt.schema.Error> schemaErrors = responseSchema.validate(node);
            if (!schemaErrors.isEmpty()) {
                throw new InvalidModelOutputException("Model response fails llm-test-case-score.json: "
                        + schemaErrors.getFirst().getMessage());
            }
        } catch (JacksonException exception) {
            throw new InvalidModelOutputException("Model response is not valid JSON and was not accepted.");
        }
        if (!inRange(score.coverageScore()) || !inRange(score.relevanceScore()) || !inRange(score.bugCatchingScore())
                || score.explanation() == null || score.explanation().isBlank()
                || score.mappedRequirements() == null || score.missingAssertions() == null
                || score.suggestedReplacement() == null || score.suggestedReplacement().steps() == null
                || score.suggestedReplacement().steps().isEmpty()) {
            throw new InvalidModelOutputException("Model response violates the required score schema or score ranges.");
        }
    }

    private boolean inRange(int score) {
        return score >= 0 && score <= 100;
    }

    private Schema loadResponseSchema() {
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/schema/llm-test-case-score.json"),
                "Missing llm score JSON schema")) {
            return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12).getSchema(input);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not load llm-test-case-score.json", exception);
        }
    }

    public record ModelJudgement(LlmTestCaseScore score, String rawOutput, String systemPrompt, String userPrompt) { }
}
