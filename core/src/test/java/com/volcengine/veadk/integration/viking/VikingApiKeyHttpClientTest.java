package com.volcengine.veadk.integration.viking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class VikingApiKeyHttpClientTest {
    private static final String TEST_API_KEY = "fake-viking-key-for-test";

    @Test
    void postBuildsBearerJsonRequestAndSendsOnce() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        VikingApiKeyHttpClient client =
                new VikingApiKeyHttpClient(
                        TEST_API_KEY,
                        request -> {
                            calls.incrementAndGet();
                            assertEquals(
                                    VikingApiKeyHttpClient.ENDPOINT + "/api/memory/search",
                                    request.uri().toString());
                            assertEquals(
                                    "Bearer " + TEST_API_KEY,
                                    request.headers().firstValue("Authorization").orElseThrow());
                            assertEquals(
                                    "application/json",
                                    request.headers().firstValue("Accept").orElseThrow());
                            assertEquals(
                                    VikingApiKeyHttpClient.TIMEOUT,
                                    request.timeout().orElseThrow());
                            return response(200, "{\"code\":0}");
                        });

        VikingApiKeyHttpClient.Response response =
                client.post("/api/memory/search", "{\"query\":\"hello\"}");

        assertEquals(1, calls.get());
        assertEquals(200, response.statusCode());
        assertFalse(response.toString().contains(TEST_API_KEY));
        assertFalse(client.toString().contains(TEST_API_KEY));
    }

    @Test
    void postRestoresInterruptAndDoesNotExposeCredential() {
        VikingApiKeyHttpClient client =
                new VikingApiKeyHttpClient(
                        TEST_API_KEY,
                        request -> {
                            throw new InterruptedException("interrupted");
                        });
        IOException error =
                assertThrows(IOException.class, () -> client.post("/api/memory/search", "{}"));
        assertTrue(Thread.currentThread().isInterrupted());
        assertFalse(error.getMessage().contains(TEST_API_KEY));
        Thread.interrupted();
    }

    private static HttpResponse<String> response(int status, String body) {
        return new HttpResponse<>() {
            public int statusCode() {
                return status;
            }

            public HttpRequest request() {
                return null;
            }

            public Optional<HttpResponse<String>> previousResponse() {
                return Optional.empty();
            }

            public HttpHeaders headers() {
                return HttpHeaders.of(Map.of(), (a, b) -> true);
            }

            public String body() {
                return body;
            }

            public Optional<javax.net.ssl.SSLSession> sslSession() {
                return Optional.empty();
            }

            public java.net.URI uri() {
                return java.net.URI.create(VikingApiKeyHttpClient.ENDPOINT);
            }

            public java.net.http.HttpClient.Version version() {
                return java.net.http.HttpClient.Version.HTTP_1_1;
            }
        };
    }
}
