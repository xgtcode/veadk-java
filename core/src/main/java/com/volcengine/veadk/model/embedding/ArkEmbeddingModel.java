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

import com.volcengine.ark.runtime.model.embeddings.EmbeddingRequest;
import com.volcengine.ark.runtime.model.embeddings.EmbeddingResult;
import com.volcengine.ark.runtime.model.multimodalembeddings.MultimodalEmbeddingInput;
import com.volcengine.ark.runtime.model.multimodalembeddings.MultimodalEmbeddingRequest;
import com.volcengine.ark.runtime.model.multimodalembeddings.MultimodalEmbeddingResult;
import com.volcengine.ark.runtime.service.ArkService;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

public class ArkEmbeddingModel implements EmbeddingModel {

    private final ArkService arkService;
    private final EmbeddingModelConfig config;

    public ArkEmbeddingModel() {
        this(EmbeddingModelConfig.fromEnv());
    }

    public ArkEmbeddingModel(EmbeddingModelConfig config) {
        this(config, createArkService(config));
    }

    ArkEmbeddingModel(EmbeddingModelConfig config, ArkService arkService) {
        this.config = Objects.requireNonNull(config, "config must not be null.");
        this.arkService = Objects.requireNonNull(arkService, "arkService must not be null.");
    }

    @Override
    public List<Double> embed(String text) {
        if (StringUtils.isBlank(text)) {
            throw new IllegalArgumentException("Embedding text must not be blank.");
        }
        if (usesMultiModalEmbeddingEndpoint()) {
            return embedWithMultiModalEndpoint(text);
        }
        return embedWithTextEndpoint(text);
    }

    @Override
    public List<List<Double>> embedAll(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }
        if (usesMultiModalEmbeddingEndpoint()) {
            return EmbeddingModel.super.embedAll(texts);
        }

        EmbeddingRequest request =
                EmbeddingRequest.builder().model(config.getModelName()).input(texts).build();
        EmbeddingResult result = arkService.createEmbeddings(request);
        if (result == null || result.getData() == null) {
            return Collections.emptyList();
        }
        return result.getData().stream().map(item -> item.getEmbedding()).toList();
    }

    @Override
    public int dimensions() {
        return config.getDimensions();
    }

    private List<Double> embedWithTextEndpoint(String text) {
        EmbeddingRequest request =
                EmbeddingRequest.builder()
                        .model(config.getModelName())
                        .input(Collections.singletonList(text))
                        .build();
        EmbeddingResult result = arkService.createEmbeddings(request);
        if (result == null || result.getData() == null || result.getData().isEmpty()) {
            throw new IllegalStateException("Ark embedding response is empty.");
        }
        return result.getData().get(0).getEmbedding();
    }

    private List<Double> embedWithMultiModalEndpoint(String text) {
        MultimodalEmbeddingInput input =
                MultimodalEmbeddingInput.builder().type("text").text(text).build();
        MultimodalEmbeddingRequest request =
                MultimodalEmbeddingRequest.builder()
                        .model(config.getModelName())
                        .dimensions(config.getDimensions())
                        .input(Collections.singletonList(input))
                        .build();
        MultimodalEmbeddingResult result = arkService.createMultiModalEmbeddings(request);
        if (result == null || result.getData() == null || result.getData().getEmbedding() == null) {
            throw new IllegalStateException("Ark multimodal embedding response is empty.");
        }
        return result.getData().getEmbedding();
    }

    private boolean usesMultiModalEmbeddingEndpoint() {
        return config.getModelName().contains("vision");
    }

    private static ArkService createArkService(EmbeddingModelConfig config) {
        return ArkService.builder().apiKey(config.getApiKey()).baseUrl(config.getApiBase()).build();
    }
}
