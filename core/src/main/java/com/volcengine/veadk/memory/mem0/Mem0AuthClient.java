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

import com.volcengine.ApiClient;
import com.volcengine.ApiException;
import com.volcengine.mem0.Mem0Api;
import com.volcengine.mem0.model.APIKeyInfoForDescribeMemoryProjectDetailOutput;
import com.volcengine.mem0.model.DescribeAPIKeyDetailRequest;
import com.volcengine.mem0.model.DescribeAPIKeyDetailResponse;
import com.volcengine.mem0.model.DescribeMemoryProjectDetailRequest;
import com.volcengine.mem0.model.DescribeMemoryProjectDetailResponse;
import com.volcengine.mem0.model.VisitAddrForDescribeMemoryProjectDetailOutput;
import com.volcengine.sign.Credentials;
import com.volcengine.veadk.utils.EnvUtil;
import java.util.Comparator;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

public class Mem0AuthClient {

    private final Mem0Api mem0Api;

    public Mem0AuthClient() {
        this.mem0Api = null;
    }

    Mem0AuthClient(Mem0Api mem0Api) {
        this.mem0Api = mem0Api;
    }

    public ResolvedMem0Credential resolve(Mem0Config config) {
        String apiKey = StringUtils.trimToNull(config.getApiKey());
        String apiKeyId = StringUtils.trimToNull(config.getApiKeyId());
        String projectId = StringUtils.trimToNull(config.getProjectId());
        String baseUrl = Mem0RuntimeClient.normalizeBaseUrl(config.getBaseUrl());

        DescribeMemoryProjectDetailResponse projectDetail = null;
        if (StringUtils.isBlank(apiKey)) {
            if (StringUtils.isBlank(apiKeyId) && StringUtils.isBlank(projectId)) {
                throw new IllegalArgumentException(
                        "Missing required Mem0 configuration. Configure DATABASE_MEM0_API_KEY, or"
                            + " configure DATABASE_MEM0_API_KEY_ID / DATABASE_MEM0_PROJECT_ID.");
            }
            if (StringUtils.isBlank(apiKeyId)) {
                projectDetail = describeMemoryProjectDetail(config, projectId);
                apiKeyId = selectApiKeyId(projectDetail.getApIKeyInfos());
            }
            apiKey = describeAPIKeyDetail(config, apiKeyId, projectId).getApIKeyValue();
        }

        if (StringUtils.isBlank(apiKey)) {
            throw new IllegalStateException("Failed to resolve Mem0 API key.");
        }

        if (StringUtils.isBlank(baseUrl) && StringUtils.isNotBlank(projectId)) {
            if (projectDetail == null) {
                projectDetail = describeMemoryProjectDetail(config, projectId);
            }
            baseUrl =
                    Mem0RuntimeClient.normalizeBaseUrl(
                            selectBaseUrl(projectDetail.getVisitAddrs()));
        }

        if (StringUtils.isBlank(baseUrl)) {
            baseUrl = EnvUtil.getDefaultMem0BaseUrl();
        }

        return new ResolvedMem0Credential(apiKey, apiKeyId, projectId, baseUrl);
    }

    DescribeMemoryProjectDetailResponse describeMemoryProjectDetail(
            Mem0Config config, String projectId) {
        try {
            DescribeMemoryProjectDetailRequest request =
                    new DescribeMemoryProjectDetailRequest().memoryProjectId(projectId);
            return getMem0Api(config).describeMemoryProjectDetail(request);
        } catch (ApiException e) {
            throw new IllegalStateException("Failed to describe Mem0 memory project.", e);
        }
    }

    DescribeAPIKeyDetailResponse describeAPIKeyDetail(
            Mem0Config config, String apiKeyId, String projectId) {
        try {
            DescribeAPIKeyDetailRequest request =
                    new DescribeAPIKeyDetailRequest().apIKeyId(apiKeyId).memoryProjectId(projectId);
            return getMem0Api(config).describeAPIKeyDetail(request);
        } catch (ApiException e) {
            throw new IllegalStateException("Failed to describe Mem0 API key.", e);
        }
    }

    private Mem0Api getMem0Api(Mem0Config config) {
        if (mem0Api != null) {
            return mem0Api;
        }

        String accessKey =
                StringUtils.defaultIfBlank(config.getAccessKey(), EnvUtil.getAccessKey());
        String secretKey =
                StringUtils.defaultIfBlank(config.getSecretKey(), EnvUtil.getSecretKey());
        String region = StringUtils.defaultIfBlank(config.getRegion(), EnvUtil.getMem0Region());

        ApiClient apiClient =
                new ApiClient()
                        .setCredentials(Credentials.getCredentials(accessKey, secretKey))
                        .setRegion(region);
        return new Mem0Api(apiClient);
    }

    private String selectApiKeyId(
            List<APIKeyInfoForDescribeMemoryProjectDetailOutput> apiKeyInfos) {
        if (apiKeyInfos == null || apiKeyInfos.isEmpty()) {
            throw new IllegalStateException("No Mem0 API key found in memory project detail.");
        }
        return apiKeyInfos.stream()
                .filter(info -> StringUtils.isNotBlank(info.getApIKeyId()))
                .sorted(Comparator.comparing(this::activeKeyRank))
                .map(APIKeyInfoForDescribeMemoryProjectDetailOutput::getApIKeyId)
                .findFirst()
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "No valid Mem0 API key id found in memory project"
                                                + " detail."));
    }

    private int activeKeyRank(APIKeyInfoForDescribeMemoryProjectDetailOutput info) {
        String status = StringUtils.defaultString(info.getStatus());
        return "Active".equalsIgnoreCase(status) || "Enabled".equalsIgnoreCase(status) ? 0 : 1;
    }

    private String selectBaseUrl(List<VisitAddrForDescribeMemoryProjectDetailOutput> visitAddrs) {
        if (visitAddrs == null || visitAddrs.isEmpty()) {
            return null;
        }
        VisitAddrForDescribeMemoryProjectDetailOutput selected =
                visitAddrs.stream()
                        .filter(addr -> StringUtils.isNotBlank(addr.getAddress()))
                        .sorted(Comparator.comparing(this::publicAddrRank))
                        .findFirst()
                        .orElse(null);
        if (selected == null) {
            return null;
        }

        String address = StringUtils.trimToNull(selected.getAddress());
        if (address == null) {
            return null;
        }
        if (address.startsWith("http://") || address.startsWith("https://")) {
            return address;
        }
        String port = StringUtils.trimToNull(selected.getPort());
        if (port == null || "80".equals(port) || "443".equals(port)) {
            return "https://" + address;
        }
        return "https://" + address + ":" + port;
    }

    private int publicAddrRank(VisitAddrForDescribeMemoryProjectDetailOutput addr) {
        String addrType = StringUtils.defaultString(addr.getAddrType());
        return "Public".equalsIgnoreCase(addrType) || "public".equalsIgnoreCase(addrType) ? 0 : 1;
    }
}
