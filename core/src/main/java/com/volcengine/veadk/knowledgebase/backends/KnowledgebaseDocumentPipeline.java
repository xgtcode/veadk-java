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
package com.volcengine.veadk.knowledgebase.backends;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

public class KnowledgebaseDocumentPipeline {

    private static final int DEFAULT_CHUNK_SIZE = 512;
    private static final int DEFAULT_CHUNK_OVERLAP = 50;

    private final DocumentParser documentParser;
    private final DocumentSplitter documentSplitter;

    public KnowledgebaseDocumentPipeline() {
        this(
                new ApacheTikaDocumentParser(),
                DocumentSplitters.recursive(DEFAULT_CHUNK_SIZE, DEFAULT_CHUNK_OVERLAP));
    }

    public KnowledgebaseDocumentPipeline(
            DocumentParser documentParser, DocumentSplitter documentSplitter) {
        this.documentParser = documentParser;
        this.documentSplitter = documentSplitter;
    }

    public List<TextSegment> loadDirectory(Path directory) throws IOException {
        if (directory == null || !Files.isDirectory(directory)) {
            throw new IllegalArgumentException("directory must be an existing directory.");
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(directory)) {
            return loadFiles(paths.filter(Files::isRegularFile).toList());
        }
    }

    public List<TextSegment> loadFiles(List<Path> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        List<Document> documents = new ArrayList<>();
        for (Path file : files) {
            Document document = FileSystemDocumentLoader.loadDocument(file, documentParser);
            document.metadata().put("file_path", file.toString());
            documents.add(document);
        }
        return splitDocuments(documents);
    }

    public List<TextSegment> loadText(String text) {
        if (StringUtils.isBlank(text)) {
            return List.of();
        }
        return splitDocuments(List.of(Document.from(text)));
    }

    public List<TextSegment> loadText(List<String> text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        List<Document> documents =
                text.stream().filter(StringUtils::isNotBlank).map(Document::from).toList();
        return splitDocuments(documents);
    }

    private List<TextSegment> splitDocuments(List<Document> documents) {
        List<TextSegment> segments = new ArrayList<>();
        for (Document document : documents) {
            List<TextSegment> splitSegments = documentSplitter.split(document);
            for (int i = 0; i < splitSegments.size(); i++) {
                TextSegment segment = splitSegments.get(i);
                Metadata metadata = segment.metadata().copy().put("chunk_index", i);
                segments.add(TextSegment.from(segment.text(), metadata));
            }
        }
        return segments;
    }
}
