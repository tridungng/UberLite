package com.uberlite.priceestimation.domain;

import java.util.Objects;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Invokes a downstream dependency and normalises every failure mode into a
 * {@link DependencyFailedException} that names the dependency.
 *
 * <p>The previous implementation only caught {@code FeignException}. That misses the two most
 * common real-world failures: {@code feign.RetryableException} (connection refused / read timeout)
 * and the {@code IllegalStateException} Spring Cloud LoadBalancer throws when Eureka has no
 * instances registered. Both would have surfaced as an anonymous "Failed to estimate price".
 */
@Component
public class DependencyInvoker {

    private static final Logger log = LoggerFactory.getLogger(DependencyInvoker.class);

    /**
     * @param dependency the service name to surface to the caller, e.g. {@code route-service}
     * @param description what was being fetched, used for logs and the error message
     * @param call        the remote call; must contain <em>only</em> the remote call so local bugs
     *                    are not misreported as dependency failures
     * @return a non-null response body
     * @throws DependencyFailedException on any transport error, error status, or null body
     */
    public <T> T call(String dependency, String description, Supplier<T> call) {
        Objects.requireNonNull(dependency, "dependency");
        try {
            T response = call.get();
            if (response == null) {
                throw new DependencyFailedException(
                        dependency, "returned an empty body for " + description, null);
            }
            return response;
        } catch (DependencyFailedException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Dependency {} failed while fetching {}", dependency, description, e);
            throw new DependencyFailedException(
                    dependency, description + " — " + rootMessage(e), e);
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return message == null || message.isBlank() ? root.getClass().getSimpleName() : message;
    }
}

