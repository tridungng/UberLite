package com.uberlite.priceestimation.support;

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

/**
 * A minimal record-and-replay HTTP stub server for the downstream services, built on the JDK's
 * {@link HttpServer}.
 *
 * <p>This plays the role WireMock plays in the issue's acceptance criteria: real sockets, real HTTP,
 * real JSON, so the Feign encoders/decoders and the URL/verb of every {@code @FeignClient} are
 * genuinely exercised — a client annotation that disagrees with a downstream route fails the test.
 * It is used in preference to WireMock because WireMock 3.x embeds Jetty 11 while Spring Boot 4
 * manages Jetty 12, and the two are binary-incompatible on the test classpath. The API below is
 * deliberately WireMock-shaped so the swap is mechanical if that conflict is ever resolved.
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

    /** Makes the given path drop the connection without replying. */
    public StubServer failConnection(String path) {
        faults.put(path, Boolean.TRUE);
        return this;
    }

    public void reset() {
        stubs.clear();
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
        return matches.get(0);
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

