package com.uberlite.tripservice.domain;

/**
 * A downstream service could not give us an answer. Distinct from a <em>negative</em> answer
 * (e.g. "no drivers available"), which is a normal domain outcome and drives a state transition.
 *
 * <p>Surfaced as {@code 502} carrying the dependency name, mirroring price-estimation-service and
 * matching-service so error bodies are consistent across the estate.
 */
public class DependencyFailedException extends RuntimeException {

    private final String dependency;

    public DependencyFailedException(String dependency, String message, Throwable cause) {
        super(dependency + " unavailable: " + message, cause);
        this.dependency = dependency;
    }

    /** Re-wraps an existing failure without re-prefixing its already-formatted message. */
    protected DependencyFailedException(DependencyFailedException cause) {
        super(cause.getMessage(), cause);
        this.dependency = cause.getDependency();
    }

    public String getDependency() {
        return dependency;
    }
}


