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

import org.apache.commons.lang3.StringUtils;

public class OpensearchConfig {

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final boolean useSsl;
    private final String certPath;

    public OpensearchConfig(
            String host,
            int port,
            String username,
            String password,
            boolean useSsl,
            String certPath) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.useSsl = useSsl;
        this.certPath = certPath;
    }

    public static OpensearchConfig fromEnv() {
        return new OpensearchConfig(
                envOrDefault("DATABASE_OPENSEARCH_HOST", ""),
                intEnvOrDefault("DATABASE_OPENSEARCH_PORT", 9200),
                envOrDefault("DATABASE_OPENSEARCH_USERNAME", ""),
                envOrDefault("DATABASE_OPENSEARCH_PASSWORD", ""),
                boolEnvOrDefault("DATABASE_OPENSEARCH_USE_SSL", true),
                envOrDefault("DATABASE_OPENSEARCH_CERT_PATH", ""));
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return StringUtils.isBlank(value) ? defaultValue : value;
    }

    private static int intEnvOrDefault(String name, int defaultValue) {
        String value = System.getenv(name);
        return StringUtils.isBlank(value) ? defaultValue : Integer.parseInt(value);
    }

    private static boolean boolEnvOrDefault(String name, boolean defaultValue) {
        String value = System.getenv(name);
        return StringUtils.isBlank(value) ? defaultValue : Boolean.parseBoolean(value);
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public boolean isUseSsl() {
        return useSsl;
    }

    public String getCertPath() {
        return certPath;
    }
}
