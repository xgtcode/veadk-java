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
package com.volcengine.veadk.memory.mem0;

import com.volcengine.veadk.utils.EnvUtil;

public class Mem0Config {

    private final String apiKey;
    private final String apiKeyId;
    private final String projectId;
    private final String baseUrl;
    private final String region;
    private final String accessKey;
    private final String secretKey;

    private Mem0Config(Builder builder) {
        this.apiKey = builder.apiKey;
        this.apiKeyId = builder.apiKeyId;
        this.projectId = builder.projectId;
        this.baseUrl = builder.baseUrl;
        this.region = builder.region;
        this.accessKey = builder.accessKey;
        this.secretKey = builder.secretKey;
    }

    public static Mem0Config fromEnv() {
        return builder()
                .apiKey(EnvUtil.getMem0ApiKey())
                .apiKeyId(EnvUtil.getMem0ApiKeyId())
                .projectId(EnvUtil.getMem0ProjectId())
                .baseUrl(EnvUtil.getMem0BaseUrl())
                .region(EnvUtil.getMem0Region())
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getApiKeyId() {
        return apiKeyId;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getRegion() {
        return region;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public static class Builder {
        private String apiKey;
        private String apiKeyId;
        private String projectId;
        private String baseUrl;
        private String region;
        private String accessKey;
        private String secretKey;

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder apiKeyId(String apiKeyId) {
            this.apiKeyId = apiKeyId;
            return this;
        }

        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder region(String region) {
            this.region = region;
            return this;
        }

        public Builder accessKey(String accessKey) {
            this.accessKey = accessKey;
            return this;
        }

        public Builder secretKey(String secretKey) {
            this.secretKey = secretKey;
            return this;
        }

        public Mem0Config build() {
            return new Mem0Config(this);
        }
    }
}
