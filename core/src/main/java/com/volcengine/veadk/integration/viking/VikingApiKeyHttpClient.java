/**
 * Copyright (c) 2025 Beijing Volcano Engine Technology Co., Ltd. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.volcengine.veadk.integration.viking;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class VikingApiKeyHttpClient {

    static final String ENDPOINT = "https://api-knowledgebase.mlp.cn-beijing.volces.com";
    static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final String apiKey;
    private final Transport transport;

    public VikingApiKeyHttpClient(String apiKey) {
        this(apiKey, defaultTransport());
    }

    VikingApiKeyHttpClient(String apiKey, Transport transport) {
        this.apiKey = apiKey;
        this.transport = transport;
    }

    public Response post(String path, String jsonBody) throws IOException {
        HttpRequest request =
                HttpRequest.newBuilder(URI.create(ENDPOINT + path))
                        .timeout(TIMEOUT)
                        .header("Accept", "application/json")
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();
        try {
            HttpResponse<String> response = transport.send(request);
            return new Response(response.statusCode(), response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Viking API Key request was interrupted", e);
        }
    }

    private static Transport defaultTransport() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        return request -> client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @FunctionalInterface
    interface Transport {
        HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException;
    }

    public record Response(int statusCode, String body) {}
}
