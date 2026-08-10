package com.uberlite.priceestimation.domain;

/**
 * Thrown when a downstream dependency needed to build a quote is unavailable or misbehaving.
 *
 * <p>Carries the dependency name so the API layer can name it in the 502 body. Unlike Surge
 * Pricing, this service never substitutes a default: a wrong price is worse than no price.
 */
public class DependencyFailedException extends RuntimeException {

    private final String dependency;

    public DependencyFailedException(String dependency, String detail, Throwable cause) {
        super(dependency + " failed: " + detail, cause);
        this.dependency = dependency;
    }

    public String getDependency() {
        return dependency;
    }
}

