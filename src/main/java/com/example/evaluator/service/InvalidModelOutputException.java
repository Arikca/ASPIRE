package com.example.evaluator.service;

public class InvalidModelOutputException extends RuntimeException {
    public InvalidModelOutputException(String message) {
        super(message);
    }
}
