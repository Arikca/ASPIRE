package com.example.evaluator.service;

public class MissingOpenAiKeyException extends RuntimeException {
    public MissingOpenAiKeyException() {
        super("OPENAI_API_KEY is required. The evaluator never substitutes canned model scores.");
    }
}
