package com.example.evaluator.api;

import com.example.evaluator.service.InvalidModelOutputException;
import com.example.evaluator.service.MissingOpenAiKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MissingOpenAiKeyException.class)
    ProblemDetail missingKey(MissingOpenAiKeyException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
        problem.setTitle("OpenAI is not configured");
        return problem;
    }

    @ExceptionHandler(InvalidModelOutputException.class)
    ProblemDetail invalidModelOutput(InvalidModelOutputException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, exception.getMessage());
        problem.setTitle("Model returned an unusable evaluation");
        return problem;
    }
}
