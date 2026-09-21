package com.volcengine.veadk.knowledgebase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.volcengine.veadk.knowledgebase.backends.BaseKnowledgebaseBackend;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KnowledgeBaseTest {

    @Test
    void builder_requiresIndexOrAppNameForNamedBackend() {
        assertThrows(IllegalArgumentException.class, () -> KnowledgeBase.builder().build());
    }

    @Test
    void addAndSearch_delegateToBackendInstance() throws IOException {
        FakeBackend backend = new FakeBackend();
        KnowledgeBase knowledgeBase =
                KnowledgeBase.builder().backendInstance(backend).topK(3).build();

        knowledgeBase.addFromText("hello");
        List<KnowledgebaseEntry> entries = knowledgeBase.search("question");

        assertEquals(List.of("hello"), backend.texts);
        assertEquals("question", backend.query);
        assertEquals(3, backend.topK);
        assertEquals("answer", entries.get(0).getContent());
    }

    @Test
    void asyncMethods_keepToolLayerCompatibility() {
        FakeBackend backend = new FakeBackend();
        KnowledgeBase knowledgeBase =
                KnowledgeBase.builder().backendInstance(backend).topK(3).build();

        knowledgeBase.addFromTextAsync("hello").blockingAwait();
        SearchKnowledgebaseResponse response =
                knowledgeBase.searchKnowledgebase("question").blockingGet();

        assertEquals(List.of("hello"), backend.texts);
        assertEquals("question", backend.query);
        assertEquals(3, backend.topK);
        assertEquals("answer", response.getKnowledgebaseEntries().get(0).getContent());
    }

    @Test
    void close_delegatesToBackend() throws IOException {
        FakeBackend backend = new FakeBackend();
        KnowledgeBase knowledgeBase = KnowledgeBase.builder().backendInstance(backend).build();

        knowledgeBase.close();

        assertTrue(backend.closed);
    }

    @Test
    void builderAcceptsAppNameAsIndexFallback() throws IOException {
        KnowledgeBase knowledgeBase =
                KnowledgeBase.builder()
                        .backendInstance(new FakeBackend())
                        .appName("ignored_for_instance")
                        .build();

        assertTrue(knowledgeBase.search("q").size() == 1);
    }

    private static class FakeBackend implements BaseKnowledgebaseBackend {
        private final List<String> texts = new java.util.ArrayList<>();
        private String query;
        private int topK;
        private boolean closed;

        @Override
        public void precheckIndexNaming() {}

        @Override
        public boolean addFromDirectory(Path directory) {
            return true;
        }

        @Override
        public boolean addFromFiles(List<Path> files) {
            return true;
        }

        @Override
        public boolean addFromText(String text) {
            texts.add(text);
            return true;
        }

        @Override
        public boolean addFromText(List<String> text) {
            texts.addAll(text);
            return true;
        }

        @Override
        public List<KnowledgebaseEntry> search(String query, int topK) throws IOException {
            this.query = query;
            this.topK = topK;
            return List.of(new KnowledgebaseEntry("answer", Map.of()));
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
