package com.example.evaluator.service;

import com.example.evaluator.domain.CaseEvaluation;
import com.example.evaluator.domain.EvaluationRequestDto;
import com.example.evaluator.domain.EvaluationRun;
import com.example.evaluator.domain.RepeatEvaluation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

@Component
public class ArtifactStore {
    private final ObjectMapper objectMapper;
    private final Path root;

    public ArtifactStore(ObjectMapper objectMapper, @Value("${evaluator.artifact-root}") String root) {
        this.objectMapper = objectMapper;
        this.root = Path.of(root);
    }

    public void persist(EvaluationRequestDto request, EvaluationRun run, Map<String, TestCaseQualityEvaluator.ModelJudgement> judgements) {
        try {
            Path dir = root.resolve(run.runId());
            Files.createDirectories(dir.resolve("prompts"));
            Files.createDirectories(dir.resolve("raw"));
            Files.createDirectories(dir.resolve("parsed"));
            write(dir.resolve("request.json"), request);
            write(dir.resolve("result.json"), run);
            for (CaseEvaluation caseEvaluation : run.testCases()) {
                TestCaseQualityEvaluator.ModelJudgement judgement = judgements.get(caseEvaluation.id());
                if (judgement == null) continue;
                writeText(dir.resolve("prompts").resolve(caseEvaluation.id() + ".md"),
                        "# System\n\n" + judgement.systemPrompt() + "\n\n# User\n\n" + judgement.userPrompt());
                writeText(dir.resolve("raw").resolve(caseEvaluation.id() + ".txt"), judgement.rawOutput());
                write(dir.resolve("parsed").resolve(caseEvaluation.id() + ".json"), judgement.score());
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not persist evaluator artifacts", exception);
        }
    }

    public void persistRepeat(RepeatEvaluation repeat) {
        try {
            Files.createDirectories(root);
            write(root.resolve("repeat-5.json"), repeat);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not persist repeat artifact", exception);
        }
    }

    private void write(Path path, Object value) throws IOException {
        writeText(path, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value));
    }

    private void writeText(Path path, String content) throws IOException {
        Files.writeString(path, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
}
