package com.volcengine.veadk.knowledgebase.backends;

import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.langchain4j.data.segment.TextSegment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgebaseDocumentPipelineTest {

    @Test
    void loadFiles_defaultPipelineParsesMarkdown() throws IOException {
        Path tempFile = Files.createTempFile("veadk-opensearch-kb", ".md");
        Files.writeString(
                tempFile, "# Test Q&A\n\nA P0 ticket should be acknowledged in 15 minutes.");

        List<TextSegment> segments =
                new KnowledgebaseDocumentPipeline().loadFiles(List.of(tempFile));

        assertFalse(segments.isEmpty());
    }
}
