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
package com.volcengine.veadk.model.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import java.util.List;
import java.util.Objects;

public class LangChain4jEmbeddingModelAdapter
        implements dev.langchain4j.model.embedding.EmbeddingModel {

    private final EmbeddingModel embeddingModel;

    public LangChain4jEmbeddingModelAdapter(EmbeddingModel embeddingModel) {
        this.embeddingModel =
                Objects.requireNonNull(embeddingModel, "embeddingModel must not be null.");
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        List<String> texts = textSegments.stream().map(TextSegment::text).toList();
        List<Embedding> embeddings =
                embeddingModel.embedAll(texts).stream()
                        .map(LangChain4jEmbeddingModelAdapter::toEmbedding)
                        .toList();
        return Response.from(embeddings);
    }

    @Override
    public int dimension() {
        return embeddingModel.dimensions();
    }

    private static Embedding toEmbedding(List<Double> vector) {
        return Embedding.from(vector.stream().map(Double::floatValue).toList());
    }
}
