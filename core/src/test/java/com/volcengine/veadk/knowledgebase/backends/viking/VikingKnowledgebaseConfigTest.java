package com.volcengine.veadk.knowledgebase.backends.viking;

import static org.assertj.core.api.Assertions.assertThat;

import com.volcengine.veadk.utils.JSONUtil;
import org.junit.jupiter.api.Test;

class VikingKnowledgebaseConfigTest {
    @Test
    void serializationAndToStringDoNotExposeCredentials() {
        String accessKey = "fake-access-key-for-test";
        String secretKey = "fake-secret-key-for-test";
        String apiKey = "fake-api-key-for-test";
        VikingKnowledgebaseConfig config =
                new VikingKnowledgebaseConfig(accessKey, secretKey, apiKey, true, 3);

        String json = JSONUtil.toJson(config);
        String text = config.toString();

        assertThat(json)
                .doesNotContain(accessKey, secretKey, apiKey, "accessKey", "secretKey", "apiKey");
        assertThat(text).doesNotContain(accessKey, secretKey, apiKey);
        assertThat(json).contains("rerank", "chunkDiffusionCount");
    }
}
