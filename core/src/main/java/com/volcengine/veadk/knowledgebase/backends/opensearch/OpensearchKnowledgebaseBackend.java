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
import com.volcengine.veadk.knowledgebase.backends.BaseKnowledgebaseBackend;
import com.volcengine.veadk.knowledgebase.backends.KnowledgebaseDocumentPipeline;
import com.volcengine.veadk.model.embedding.ArkEmbeddingModel;
import com.volcengine.veadk.model.embedding.LangChain4jEmbeddingModelAdapter;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;

public class OpensearchKnowledgebaseBackend implements BaseKnowledgebaseBackend {

    private final String index;
    private final OpensearchVectorStore vectorStore;
    private final dev.langchain4j.model.embedding.EmbeddingModel embeddingModel;
    private final KnowledgebaseDocumentPipeline documentPipeline;

    public OpensearchKnowledgebaseBackend(String index) {
        this(
                index,
                new OpensearchSdkVectorStore(OpensearchConfig.fromEnv()),
                new LangChain4jEmbeddingModelAdapter(new ArkEmbeddingModel()),
                new KnowledgebaseDocumentPipeline());
    }

    public OpensearchKnowledgebaseBackend(
            String index,
            OpensearchConfig config,
            dev.langchain4j.model.embedding.EmbeddingModel embeddingModel) {
        this(
                index,
                new OpensearchSdkVectorStore(config),
                embeddingModel,
                new KnowledgebaseDocumentPipeline());
    }

    OpensearchKnowledgebaseBackend(
            String index,
            OpensearchVectorStore vectorStore,
            dev.langchain4j.model.embedding.EmbeddingModel embeddingModel,
            KnowledgebaseDocumentPipeline documentPipeline) {
        this.index = index;
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
        this.documentPipeline = documentPipeline;
        precheckIndexNaming();
    }

    @Override
    public void precheckIndexNaming() {
        if (!(StringUtils.isNotBlank(index)
                && !index.startsWith("_")
                && !index.startsWith("-")
                && index.equals(index.toLowerCase())
                && index.matches("^[a-z0-9_\\-.]+$"))) {
            throw new IllegalArgumentException(
                    "The index name does not conform to the naming rules of OpenSearch");
        }
    }

    @Override
    public boolean addFromDirectory(Path directory) throws IOException {
        return insertSegments(documentPipeline.loadDirectory(directory));
    }

    @Override
    public boolean addFromFiles(List<Path> files) throws IOException {
        return insertSegments(documentPipeline.loadFiles(files));
    }

    @Override
    public boolean addFromText(String text) throws IOException {
        return insertSegments(documentPipeline.loadText(text));
    }

    @Override
    public boolean addFromText(List<String> text) throws IOException {
        return insertSegments(documentPipeline.loadText(text));
    }

    @Override
    public List<KnowledgebaseEntry> search(String query, int topK) throws IOException {
        if (StringUtils.isBlank(query)) {
            return List.of();
        }
        vectorStore.ensureIndex(index, embeddingModel.dimension());
        return vectorStore.search(index, embeddingModel.embed(query).content(), topK);
    }

    @Override
    public void close() throws IOException {
        vectorStore.close();
    }

    private boolean insertSegments(List<TextSegment> segments) throws IOException {
        if (segments.isEmpty()) {
            return true;
        }
        vectorStore.ensureIndex(index, embeddingModel.dimension());
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        for (int i = 0; i < segments.size(); i++) {
            TextSegment segment = segments.get(i);
            vectorStore.upsert(
                    index,
                    UUID.randomUUID().toString(),
                    segment.text(),
                    metadataToStringMap(segment.metadata().toMap()),
                    embeddings.get(i));
        }
        return true;
    }

    private Map<String, String> metadataToStringMap(Map<String, Object> sourceMetadata) {
        Map<String, String> metadata = new LinkedHashMap<>();
        sourceMetadata.forEach((key, value) -> metadata.put(key, String.valueOf(value)));
        return metadata;
    }
}
