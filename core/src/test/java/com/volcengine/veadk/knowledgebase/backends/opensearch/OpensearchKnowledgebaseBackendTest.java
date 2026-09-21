package com.volcengine.veadk.knowledgebase.backends.opensearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.volcengine.veadk.knowledgebase.KnowledgebaseEntry;
import com.volcengine.veadk.knowledgebase.backends.KnowledgebaseDocumentPipeline;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpensearchKnowledgebaseBackendTest {

    @Test
    void constructor_rejectsInvalidOpenSearchIndexNames() {
        FakeVectorStore vectorStore = new FakeVectorStore();
        FakeEmbeddingModel embeddingModel = new FakeEmbeddingModel();

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new OpensearchKnowledgebaseBackend(
                                "BadIndex", vectorStore, embeddingModel, pipeline()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new OpensearchKnowledgebaseBackend(
                                "_hidden", vectorStore, embeddingModel, pipeline()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new OpensearchKnowledgebaseBackend(
                                "-bad", vectorStore, embeddingModel, pipeline()));
    }

    @Test
    void addFromText_embedsAndUpsertsChunks() throws IOException {
        FakeVectorStore vectorStore = new FakeVectorStore();
        OpensearchKnowledgebaseBackend backend =
                new OpensearchKnowledgebaseBackend(
                        "kb_demo", vectorStore, new FakeEmbeddingModel(), pipeline());

        assertTrue(backend.addFromText("alpha beta gamma"));

        assertEquals(List.of("kb_demo:3"), vectorStore.ensureCalls);
        assertTrue(vectorStore.upserts.size() > 1);
        assertEquals("kb_demo", vectorStore.upserts.get(0).index);
        assertEquals("0", vectorStore.upserts.get(0).metadata.get("chunk_index"));
        assertEquals(
                List.of(1.0f, 2.0f, 3.0f), vectorStore.upserts.get(0).embedding.vectorAsList());
    }

    @Test
    void addFromFiles_readsUtf8FilesAndKeepsFilePathMetadata() throws IOException {
        Path tempFile = Files.createTempFile("veadk-kb", ".md");
        Files.writeString(tempFile, "hello from file");
        FakeVectorStore vectorStore = new FakeVectorStore();
        OpensearchKnowledgebaseBackend backend =
                new OpensearchKnowledgebaseBackend(
                        "kb_demo", vectorStore, new FakeEmbeddingModel(), pipeline());

        assertTrue(backend.addFromFiles(List.of(tempFile)));

        assertEquals(tempFile.toString(), vectorStore.upserts.get(0).metadata.get("file_path"));
        assertEquals("0", vectorStore.upserts.get(0).metadata.get("chunk_index"));
    }

    @Test
    void search_embedsQueryAndReturnsEntries() throws IOException {
        FakeVectorStore vectorStore = new FakeVectorStore();
        vectorStore.searchResults =
                List.of(new KnowledgebaseEntry("matched text", Map.of("source", "unit")));
        OpensearchKnowledgebaseBackend backend =
                new OpensearchKnowledgebaseBackend(
                        "kb_demo", vectorStore, new FakeEmbeddingModel(), pipeline());

        List<KnowledgebaseEntry> entries = backend.search("question", 2);

        assertEquals(List.of("kb_demo:3"), vectorStore.ensureCalls);
        assertEquals("kb_demo", vectorStore.searchIndex);
        assertEquals(2, vectorStore.searchTopK);
        assertEquals(List.of(1.0f, 2.0f, 3.0f), vectorStore.searchEmbedding.vectorAsList());
        assertEquals("matched text", entries.get(0).getContent());
    }

    @Test
    void close_closesVectorStore() throws IOException {
        FakeVectorStore vectorStore = new FakeVectorStore();
        OpensearchKnowledgebaseBackend backend =
                new OpensearchKnowledgebaseBackend(
                        "kb_demo", vectorStore, new FakeEmbeddingModel(), pipeline());

        backend.close();

        assertTrue(vectorStore.closed);
    }

    @Test
    void sdkVectorStore_doesNotCreateClientUntilFirstRequest() {
        new OpensearchSdkVectorStore(new OpensearchConfig("", 9200, "", "", true, ""));
    }

    private static KnowledgebaseDocumentPipeline pipeline() {
        return new KnowledgebaseDocumentPipeline(
                new TextDocumentParser(), DocumentSplitters.recursive(8, 2));
    }

    private static class FakeEmbeddingModel
            implements dev.langchain4j.model.embedding.EmbeddingModel {
        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
            return Response.from(
                    textSegments.stream()
                            .map(ignored -> Embedding.from(List.of(1.0f, 2.0f, 3.0f)))
                            .toList());
        }

        @Override
        public int dimension() {
            return 3;
        }
    }

    private static class FakeVectorStore implements OpensearchVectorStore {
        private final List<String> ensureCalls = new ArrayList<>();
        private final List<UpsertCall> upserts = new ArrayList<>();
        private List<KnowledgebaseEntry> searchResults = List.of();
        private String searchIndex;
        private Embedding searchEmbedding;
        private int searchTopK;
        private boolean closed;

        @Override
        public void ensureIndex(String index, int dimensions) {
            ensureCalls.add(index + ":" + dimensions);
        }

        @Override
        public void upsert(
                String index,
                String id,
                String text,
                Map<String, String> metadata,
                Embedding embedding) {
            upserts.add(new UpsertCall(index, id, text, metadata, embedding));
        }

        @Override
        public List<KnowledgebaseEntry> search(String index, Embedding embedding, int topK) {
            this.searchIndex = index;
            this.searchEmbedding = embedding;
            this.searchTopK = topK;
            return searchResults;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private record UpsertCall(
            String index,
            String id,
            String text,
            Map<String, String> metadata,
            Embedding embedding) {}
}
