package com.volcengine.veadk.model.embedding;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import java.util.List;
import org.junit.jupiter.api.Test;

class LangChain4jEmbeddingModelAdapterTest {

    @Test
    void embedAll_convertsVeadkEmbeddingsToLangChain4jEmbeddings() {
        LangChain4jEmbeddingModelAdapter adapter =
                new LangChain4jEmbeddingModelAdapter(new FakeEmbeddingModel());

        Response<List<Embedding>> response =
                adapter.embedAll(List.of(TextSegment.from("alpha"), TextSegment.from("beta")));

        assertEquals(2, response.content().size());
        assertEquals(List.of(5.0f, 6.0f), response.content().get(0).vectorAsList());
        assertEquals(2, adapter.dimension());
    }

    private static class FakeEmbeddingModel implements EmbeddingModel {

        @Override
        public List<Double> embed(String text) {
            return List.of((double) text.length(), 6.0);
        }

        @Override
        public int dimensions() {
            return 2;
        }
    }
}
