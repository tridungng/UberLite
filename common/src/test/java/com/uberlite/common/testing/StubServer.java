package com.uberlite.common.testing;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * A minimal record-and-replay HTTP stub server for downstream services, built on the JDK's
 * {@link HttpServer}. Shipped in {@code common}'s test-jar so services share one copy.
 *
 * <p>This plays the role WireMock plays in the issues' acceptance criteria: real sockets, real HTTP,
 * real JSON, so the Feign encoders/decoders and the URL/verb of every {@code @FeignClient} are
 * genuinely exercised — a client annotation that disagrees with a downstream route fails the test.
 *
 * <h2>Why not WireMock</h2>
 *
 * <p>WireMock cannot run on this classpath, and the failure is not configurable away:
 *
 * <ul>
 *   <li>{@code wiremock-jre8:2.35.x} embeds Jetty 9.4, which requires the pre-Jakarta
 *       {@code javax.servlet} API. Spring Boot 4 is Jakarta-only, so it fails with
 *       {@code NoClassDefFoundError: javax/servlet/DispatcherType}.
 *   <li>Adding {@code javax.servlet-api} back gets one step further and then fails with
 *       {@code NoClassDefFoundError: org.eclipse.jetty.util.log.Log} — a class deleted in Jetty 10.
 *       Spring Boot 4 manages Jetty 12, so {@code jetty-util} resolves to 12.x and that class is
 *       simply gone.
 *   <li>Fixing that would mean pinning Jetty back to 9.4 for the whole module, breaking anything
 *       else that expects the managed Jetty. Not a trade worth making for a test double.
 *   <li>The shaded {@code wiremock-standalone} artifact relocates its own Jetty and would work, but
 *       is not available in this environment's artifact mirror.
 * </ul>
 *
 * <p>The API below is deliberately WireMock-shaped so the swap is mechanical if that ever changes.
 */
public final class StubServer implements AutoCloseable {

    /** A canned response for one path. */
    public record Stub(int status, String body) {
        public static Stub okJson(String json) {
            return new Stub(200, json);
        }

        public static Stub status(int status, String body) {
            return new Stub(status, body);
        }
    }

    /** A request the service actually made. */
    public record RecordedRequest(String method, String path, String query, String body) {}

    private final HttpServer server;
    private final Map<String, Stub> stubs = new ConcurrentHashMap<>();
    /**
     * Query-aware responders, keyed by path. Needed when a service calls one path repeatedly with
     * different parameters — e.g. Matching calls {@code /route/estimate} once per candidate, where
     * only the query string distinguishes "driver A is 9 km away" from "driver B is 1.5 km away".
     */
    private final Map<String, Function<String, Stub>> dynamicStubs = new ConcurrentHashMap<>();
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    /** Paths that should drop the connection, simulating an unreachable dependency. */
    private final Map<String, Boolean> faults = new ConcurrentHashMap<>();

    public StubServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", this::handle);
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + port();
    }

    /** Registers a canned response for an exact path. */
    public StubServer stub(String path, Stub stub) {
        stubs.put(path, stub);
        return this;
    }

    /**
     * Registers a responder that sees the raw query string, for paths called repeatedly with
     * different parameters.
     */
    public StubServer stubByQuery(String path, Function<String, Stub> responder) {
        dynamicStubs.put(path, responder);
        return this;
    }

    /** Makes the given path drop the connection without replying. */
    public StubServer failConnection(String path) {
        faults.put(path, Boolean.TRUE);
        return this;
    }

    public void reset() {
        stubs.clear();
        dynamicStubs.clear();
        faults.clear();
        requests.clear();
    }

    public List<RecordedRequest> requests() {
        return List.copyOf(requests);
    }

    /** @return the single request recorded for {@code path}, failing if there wasn't exactly one. */
    public RecordedRequest requestTo(String path) {
        List<RecordedRequest> matches = requests.stream().filter(r -> r.path().equals(path)).toList();
        if (matches.size() != 1) {
            throw new AssertionError("Expected exactly 1 request to " + path + " but got " + matches.size());
        }
        return matches.getFirst();
    }

    public long countRequestsTo(String path) {
        return requests.stream().filter(r -> r.path().equals(path)).count();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(new RecordedRequest(
                exchange.getRequestMethod(), path, exchange.getRequestURI().getQuery(), body));

        if (faults.containsKey(path)) {
            exchange.close(); // connection reset — the dependency is unreachable
            return;
        }

        Function<String, Stub> responder = dynamicStubs.get(path);
        if (responder != null) {
            Stub dynamic = responder.apply(exchange.getRequestURI().getQuery());
            respond(exchange, dynamic.status(), dynamic.body());
            return;
        }

        Stub stub = stubs.get(path);
        if (stub == null) {
            respond(exchange, 404, "{\"message\":\"no stub for " + path + "\"}");
            return;
        }
        respond(exchange, stub.status(), stub.body());
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}


