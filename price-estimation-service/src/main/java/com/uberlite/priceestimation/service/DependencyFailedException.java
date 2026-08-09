package com.uberlite.priceestimation.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_GATEWAY)
public class DependencyFailedException extends RuntimeException {
    public DependencyFailedException(String message) { super(message); }
}
