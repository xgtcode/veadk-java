package com.volcengine.veadk.memory.mem0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.volcengine.mem0.Mem0Api;
import com.volcengine.mem0.model.APIKeyInfoForDescribeMemoryProjectDetailOutput;
import com.volcengine.mem0.model.DescribeAPIKeyDetailRequest;
import com.volcengine.mem0.model.DescribeAPIKeyDetailResponse;
import com.volcengine.mem0.model.DescribeMemoryProjectDetailRequest;
import com.volcengine.mem0.model.DescribeMemoryProjectDetailResponse;
import com.volcengine.mem0.model.VisitAddrForDescribeMemoryProjectDetailOutput;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class Mem0AuthClientTest {

    @Test
    void resolve_withDirectApiKey_shouldSkipSdk() {
        Mem0Api api = Mockito.mock(Mem0Api.class);
        Mem0AuthClient client = new Mem0AuthClient(api);

        ResolvedMem0Credential credential =
                client.resolve(
                        Mem0Config.builder()
                                .apiKey("direct-key")
                                .baseUrl("https://mem0.example.com/v1/")
                                .build());

        assertThat(credential.getApiKey()).isEqualTo("direct-key");
        assertThat(credential.getBaseUrl()).isEqualTo("https://mem0.example.com");
        verifyNoInteractions(api);
    }

    @Test
    void resolve_withApiKeyId_shouldDescribeApiKey() throws Exception {
        Mem0Api api = Mockito.mock(Mem0Api.class);
        when(api.describeAPIKeyDetail(any()))
                .thenReturn(new DescribeAPIKeyDetailResponse().apIKeyValue("resolved-key"));
        Mem0AuthClient client = new Mem0AuthClient(api);

        ResolvedMem0Credential credential =
                client.resolve(
                        Mem0Config.builder()
                                .apiKeyId("api-key-id")
                                .projectId("project-id")
                                .baseUrl("https://mem0.example.com")
                                .build());

        ArgumentCaptor<DescribeAPIKeyDetailRequest> captor =
                ArgumentCaptor.forClass(DescribeAPIKeyDetailRequest.class);
        verify(api).describeAPIKeyDetail(captor.capture());
        assertThat(captor.getValue().getApIKeyId()).isEqualTo("api-key-id");
        assertThat(captor.getValue().getMemoryProjectId()).isEqualTo("project-id");
        assertThat(credential.getApiKey()).isEqualTo("resolved-key");
    }

    @Test
    void resolve_withProjectId_shouldSelectApiKeyAndBaseUrlFromProject() throws Exception {
        Mem0Api api = Mockito.mock(Mem0Api.class);
        when(api.describeMemoryProjectDetail(any()))
                .thenReturn(
                        new DescribeMemoryProjectDetailResponse()
                                .apIKeyInfos(
                                        List.of(
                                                new APIKeyInfoForDescribeMemoryProjectDetailOutput()
                                                        .apIKeyId("inactive")
                                                        .status("Disabled"),
                                                new APIKeyInfoForDescribeMemoryProjectDetailOutput()
                                                        .apIKeyId("active")
                                                        .status("Active")))
                                .visitAddrs(
                                        List.of(
                                                new VisitAddrForDescribeMemoryProjectDetailOutput()
                                                        .addrType("Private")
                                                        .address("private.example.com"),
                                                new VisitAddrForDescribeMemoryProjectDetailOutput()
                                                        .addrType("Public")
                                                        .address("public.example.com")
                                                        .port("443"))));
        when(api.describeAPIKeyDetail(any()))
                .thenReturn(new DescribeAPIKeyDetailResponse().apIKeyValue("resolved-key"));
        Mem0AuthClient client = new Mem0AuthClient(api);

        ResolvedMem0Credential credential =
                client.resolve(Mem0Config.builder().projectId("project-id").build());

        ArgumentCaptor<DescribeMemoryProjectDetailRequest> projectCaptor =
                ArgumentCaptor.forClass(DescribeMemoryProjectDetailRequest.class);
        ArgumentCaptor<DescribeAPIKeyDetailRequest> keyCaptor =
                ArgumentCaptor.forClass(DescribeAPIKeyDetailRequest.class);
        verify(api).describeMemoryProjectDetail(projectCaptor.capture());
        verify(api).describeAPIKeyDetail(keyCaptor.capture());

        assertThat(projectCaptor.getValue().getMemoryProjectId()).isEqualTo("project-id");
        assertThat(keyCaptor.getValue().getApIKeyId()).isEqualTo("active");
        assertThat(credential.getApiKey()).isEqualTo("resolved-key");
        assertThat(credential.getBaseUrl()).isEqualTo("https://public.example.com");
    }

    @Test
    void resolve_withoutAnyCredential_shouldThrow() {
        Mem0AuthClient client = new Mem0AuthClient(Mockito.mock(Mem0Api.class));

        assertThatThrownBy(() -> client.resolve(Mem0Config.builder().build()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
