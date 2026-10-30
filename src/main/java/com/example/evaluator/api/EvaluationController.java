package com.example.evaluator.api;

import com.example.evaluator.domain.EvaluationRequestDto;
import com.example.evaluator.domain.EvaluationRun;
import com.example.evaluator.domain.EvaluationCapabilities;
import com.example.evaluator.domain.RepeatEvaluation;
import com.example.evaluator.service.EvaluationService;
import com.example.evaluator.service.FixtureService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/eval")
public class EvaluationController {
    private final EvaluationService evaluationService;
    private final FixtureService fixtureService;

    public EvaluationController(EvaluationService evaluationService, FixtureService fixtureService) {
        this.evaluationService = evaluationService;
        this.fixtureService = fixtureService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public EvaluationRun evaluate(@Valid @RequestBody EvaluationRequestDto request) {
        return evaluationService.evaluate(request);
    }

    @PostMapping("/repeat")
    public RepeatEvaluation repeat(@Valid @RequestBody EvaluationRequestDto request) {
        return evaluationService.repeat(request, 5);
    }

    @GetMapping("/fixtures")
    public Map<String, Object> fixtures() {
        return fixtureService.assignmentFixture();
    }

    @GetMapping("/methodology")
    public EvaluationCapabilities methodology() {
        return evaluationService.capabilities();
    }
}
