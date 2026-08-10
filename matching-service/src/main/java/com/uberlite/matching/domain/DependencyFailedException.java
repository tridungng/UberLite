package com.uberlite.matching.domain;

/**
 * A downstream service required to produce a match was unavailable. Maps to HTTP 502.
 *
 * <p>Mirrors the price-estimation-service exception of the same name so error semantics are
 * consistent across the estate: we refuse to invent a match rather than degrade silently.
 */
public class DependencyFailedException extends RuntimeException {

    private final String dependency;

    public DependencyFailedException(String dependency, String message, Throwable cause) {
        super(message, cause);
        this.dependency = dependency;
    }

    public String getDependency() {
        return dependency;
    }
}

