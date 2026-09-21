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
package com.volcengine.veadk.knowledgebase.backends.opensearch;

import com.volcengine.veadk.knowledgebase.KnowledgebaseEntry;
import dev.langchain4j.data.embedding.Embedding;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.net.ssl.SSLContext;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5Transport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class OpensearchSdkVectorStore implements OpensearchVectorStore {

    private static final Logger log = LoggerFactory.getLogger(OpensearchSdkVectorStore.class);

    private final OpensearchConfig config;
    private OpenSearchClient client;
    private ApacheHttpClient5Transport transport;

    OpensearchSdkVectorStore(OpensearchConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null.");
    }

    OpensearchSdkVectorStore(OpenSearchClient client) {
        this.config = null;
        this.client = Objects.requireNonNull(client, "client must not be null.");
    }

    @Override
    public void ensureIndex(String index, int dimensions) throws IOException {
        OpenSearchClient openSearchClient = client();
        if (openSearchClient.indices().exists(e -> e.index(index)).value()) {
            return;
        }

        try {
            openSearchClient
                    .indices()
                    .create(
                            c ->
                                    c.index(index)
                                            .settings(s -> s.knn(true))
                                            .mappings(
                                                    m ->
                                                            m.properties(
                                                                            "text",
                                                                            p -> p.text(t -> t))
                                                                    .properties(
                                                                            "metadata",
                                                                            p ->
                                                                                    p.object(
                                                                                            o ->
                                                                                                    o
                                                                                                            .enabled(
                                                                                                                    true)))
                                                                    .properties(
                                                                            "embedding",
                                                                            p ->
                                                                                    p.knnVector(
                                                                                            k ->
                                                                                                    k
                                                                                                            .dimension(
                                                                                                                    dimensions)))));
        } catch (OpenSearchException e) {
            if (e.status() != 400
                    || !String.valueOf(e.error()).contains("resource_already_exists")) {
                throw e;
            }
        }
    }

    @Override
    public void upsert(
            String index, String id, String text, Map<String, String> metadata, Embedding embedding)
            throws IOException {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("text", text);
        document.put("metadata", metadata);
        document.put("embedding", embedding.vectorAsList());

        client().index(i -> i.index(index).id(id).document(document));
    }

    @Override
    public List<KnowledgebaseEntry> search(String index, Embedding embedding, int topK)
            throws IOException {
        SearchResponse<Map> response =
                client().search(
                                s ->
                                        s.index(index)
                                                .size(topK)
                                                .source(
                                                        source ->
                                                                source.filter(
                                                                        filter ->
                                                                                filter.includes(
                                                                                        "text",
                                                                                        "metadata")))
                                                .query(
                                                        q ->
                                                                q.knn(
                                                                        k ->
                                                                                k.field("embedding")
                                                                                        .vector(
                                                                                                embedding
                                                                                                        .vectorAsList())
                                                                                        .k(topK))),
                                Map.class);

        List<KnowledgebaseEntry> entries = new ArrayList<>();
        for (Hit<Map> hit : response.hits().hits()) {
            Map<?, ?> source = hit.source();
            if (source == null || source.get("text") == null) {
                continue;
            }
            Map<String, String> metadata = parseMetadata(source.get("metadata"));
            if (hit.score() != null) {
                metadata = new LinkedHashMap<>(metadata);
                metadata.put("score", String.valueOf(hit.score()));
            }
            entries.add(new KnowledgebaseEntry(String.valueOf(source.get("text")), metadata));
        }
        return entries;
    }

    @Override
    public synchronized void close() throws IOException {
        if (transport == null) {
            return;
        }
        try {
            transport.close();
        } finally {
            transport = null;
            client = null;
        }
    }

    private synchronized OpenSearchClient client() {
        if (client != null) {
            return client;
        }
        ApacheHttpClient5Transport createdTransport = createTransport(config);
        transport = createdTransport;
        client = new OpenSearchClient(createdTransport);
        return client;
    }

    private static ApacheHttpClient5Transport createTransport(OpensearchConfig config) {
        if (StringUtils.isBlank(config.getHost())) {
            throw new IllegalArgumentException("OpenSearch host must not be blank.");
        }

        ApacheHttpClient5TransportBuilder transportBuilder =
                ApacheHttpClient5TransportBuilder.builder(toHttpHost(config))
                        .setMapper(new JacksonJsonpMapper());

        if (StringUtils.isNotBlank(config.getUsername())) {
            String credential = config.getUsername() + ":" + config.getPassword();
            String encoded =
                    Base64.getEncoder().encodeToString(credential.getBytes(StandardCharsets.UTF_8));
            transportBuilder.setDefaultHeaders(
                    new Header[] {new BasicHeader("Authorization", "Basic " + encoded)});
        }

        if (config.isUseSsl()) {
            transportBuilder.setHttpClientConfigCallback(
                    httpClientBuilder ->
                            httpClientBuilder.setConnectionManager(
                                    PoolingAsyncClientConnectionManagerBuilder.create()
                                            .setTlsStrategy(createTlsStrategy(config.getCertPath()))
                                            .build()));
        }

        return transportBuilder.build();
    }

    private static HttpHost toHttpHost(OpensearchConfig config) {
        String host = StringUtils.trim(config.getHost());
        if (host.startsWith("http://") || host.startsWith("https://")) {
            return HttpHost.create(URI.create(host));
        }
        String scheme = config.isUseSsl() ? "https" : "http";
        return new HttpHost(scheme, host, config.getPort());
    }

    private static SSLContext createSslContext(String certPath) {
        try {
            if (StringUtils.isBlank(certPath)) {
                log.warn(
                        "OpenSearch certPath is not set; TLS certificate verification is"
                                + " disabled.");
                return SSLContextBuilder.create()
                        .loadTrustMaterial(TrustAllStrategy.INSTANCE)
                        .build();
            }

            Certificate certificate;
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            try (java.io.InputStream inputStream =
                    java.nio.file.Files.newInputStream(Path.of(certPath))) {
                certificate = certificateFactory.generateCertificate(inputStream);
            }
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            keyStore.setCertificateEntry("opensearch-ca", certificate);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            javax.net.ssl.TrustManagerFactory trustManagerFactory =
                    javax.net.ssl.TrustManagerFactory.getInstance(
                            javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore);
            sslContext.init(null, trustManagerFactory.getTrustManagers(), new SecureRandom());
            return sslContext;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize OpenSearch SSL context.", e);
        }
    }

    private static org.apache.hc.core5.http.nio.ssl.TlsStrategy createTlsStrategy(String certPath) {
        ClientTlsStrategyBuilder builder =
                ClientTlsStrategyBuilder.create().setSslContext(createSslContext(certPath));
        if (StringUtils.isBlank(certPath)) {
            builder.setHostnameVerifier(NoopHostnameVerifier.INSTANCE);
        }
        return builder.build();
    }

    private static Map<String, String> parseMetadata(Object metadataValue) {
        if (!(metadataValue instanceof Map<?, ?> sourceMetadata)) {
            return Map.of();
        }
        Map<String, String> metadata = new LinkedHashMap<>();
        sourceMetadata.forEach(
                (key, value) -> metadata.put(String.valueOf(key), String.valueOf(value)));
        return metadata;
    }
}
