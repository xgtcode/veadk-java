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
package com.volcengine.veadk.knowledgebase.backends.viking;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.volcengine.veadk.utils.EnvUtil;
import org.apache.commons.lang3.StringUtils;

public class VikingKnowledgebaseConfig {

    public static final boolean DEFAULT_RERANK = true;
    public static final int DEFAULT_CHUNK_DIFFUSION_COUNT = 3;

    private final String accessKey;
    private final String secretKey;
    private final String apiKey;
    private final boolean rerank;
    private final int chunkDiffusionCount;

    public VikingKnowledgebaseConfig(
            String accessKey, String secretKey, boolean rerank, int chunkDiffusionCount) {
        this(accessKey, secretKey, null, rerank, chunkDiffusionCount);
    }

    public VikingKnowledgebaseConfig(
            String accessKey,
            String secretKey,
            String apiKey,
            boolean rerank,
            int chunkDiffusionCount) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.apiKey = apiKey;
        this.rerank = rerank;
        this.chunkDiffusionCount = chunkDiffusionCount;
    }

    public static VikingKnowledgebaseConfig fromEnv() {
        return fromEnv(null);
    }

    public static VikingKnowledgebaseConfig fromEnv(String explicitApiKey) {
        String apiKey = EnvUtil.resolveVikingApiKey(explicitApiKey);
        String accessKey = EnvUtil.getOptionalAccessKey();
        String secretKey = EnvUtil.getOptionalSecretKey();
        if (apiKey == null
                && !(StringUtils.isNotBlank(accessKey) && StringUtils.isNotBlank(secretKey))) {
            throw new IllegalStateException(
                    "Viking KnowledgeBase requires DATABASE_VIKING_API_KEY or both"
                            + " VOLCENGINE_ACCESS_KEY and VOLCENGINE_SECRET_KEY.");
        }
        return new VikingKnowledgebaseConfig(
                accessKey, secretKey, apiKey, DEFAULT_RERANK, DEFAULT_CHUNK_DIFFUSION_COUNT);
    }

    @JsonIgnore
    public String getAccessKey() {
        return accessKey;
    }

    @JsonIgnore
    public String getSecretKey() {
        return secretKey;
    }

    @JsonIgnore
    public String getApiKey() {
        return apiKey;
    }

    @JsonIgnore
    public boolean hasCompleteAkSk() {
        return StringUtils.isNotBlank(accessKey) && StringUtils.isNotBlank(secretKey);
    }

    public boolean isRerank() {
        return rerank;
    }

    public int getChunkDiffusionCount() {
        return chunkDiffusionCount;
    }
}
