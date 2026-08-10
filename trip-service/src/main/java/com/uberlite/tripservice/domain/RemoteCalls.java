package com.uberlite.tripservice.domain;

import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Normalises every failure mode of a synchronous downstream call into
 * {@link DependencyFailedException}, so orchestration code never has to know about Feign.
 *
 * <p>Catching only {@code FeignException} is not enough — it misses the two most common real
 * failures: {@code feign.RetryableException} (connection refused / read timeout) and the
 * {@code IllegalStateException} Spring Cloud LoadBalancer throws when Eureka has no instances
 * registered for a service id.
 */
final class RemoteCalls {

    private static final Logger log = LoggerFactory.getLogger(RemoteCalls.class);

    private RemoteCalls() {
    }

    /**
     * @param dependency service id to surface to the caller, e.g. {@code matching-service}
     * @param description what was being fetched, for logs and the error message
     * @param call        the remote call and <em>nothing else</em>, so local bugs are not
     *                    misreported as dependency failures
     * @return a non-null response body
     */
    static <T> T call(String dependency, String description, Supplier<T> call) {
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
            log.warn("Dependency {} failed while {}", dependency, description, e);
            throw new DependencyFailedException(dependency, description + " — " + rootMessage(e), e);
        }
    }

    /** Same as {@link #call} for a void endpoint. */
    static void callVoid(String dependency, String description, Runnable call) {
        call(dependency, description, () -> {
            call.run();
            return Boolean.TRUE;
        });
    }

    /** @return the HTTP status of {@code t} or one of its causes, or -1 if it wasn't an HTTP error. */
    static int httpStatusOf(Throwable t) {
        for (Throwable current = t; current != null && current.getCause() != current; current = current.getCause()) {
            if (current instanceof FeignException feignException) {
                return feignException.status();
            }
        }
        return -1;
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

