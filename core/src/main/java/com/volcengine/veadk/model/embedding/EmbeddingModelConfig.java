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

import org.apache.commons.lang3.StringUtils;

public class EmbeddingModelConfig {

    public static final String DEFAULT_MODEL_NAME = "doubao-embedding-vision-250615";
    public static final String DEFAULT_API_BASE = "https://ark.cn-beijing.volces.com/api/v3/";
    public static final int DEFAULT_DIMENSIONS = 2048;

    private final String modelName;
    private final int dimensions;
    private final String apiBase;
    private final String apiKey;

    public EmbeddingModelConfig(String modelName, int dimensions, String apiBase, String apiKey) {
        this.modelName = modelName;
        this.dimensions = dimensions;
        this.apiBase = apiBase;
        this.apiKey = apiKey;
    }

    public static EmbeddingModelConfig fromEnv() {
        String modelName = envOrDefault("MODEL_EMBEDDING_NAME", DEFAULT_MODEL_NAME);
        int dimensions = intEnvOrDefault("MODEL_EMBEDDING_DIM", DEFAULT_DIMENSIONS);
        String apiBase = envOrDefault("MODEL_EMBEDDING_API_BASE", DEFAULT_API_BASE);
        String apiKey = System.getenv("MODEL_EMBEDDING_API_KEY");
        if (StringUtils.isBlank(apiKey)) {
            apiKey = System.getenv("MODEL_AGENT_API_KEY");
        }
        if (StringUtils.isBlank(apiKey)) {
            throw new IllegalStateException(
                    "Missing required configuration: MODEL_EMBEDDING_API_KEY or"
                            + " MODEL_AGENT_API_KEY. Please configure the environment variable"
                            + " before startup.");
        }
        return new EmbeddingModelConfig(modelName, dimensions, apiBase, apiKey);
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return StringUtils.isBlank(value) ? defaultValue : value;
    }

    private static int intEnvOrDefault(String name, int defaultValue) {
        String value = System.getenv(name);
        if (StringUtils.isBlank(value)) {
            return defaultValue;
        }
        return Integer.parseInt(value);
    }

    public String getModelName() {
        return modelName;
    }

    public int getDimensions() {
        return dimensions;
    }

    public String getApiBase() {
        return apiBase;
    }

    public String getApiKey() {
        return apiKey;
    }
}
