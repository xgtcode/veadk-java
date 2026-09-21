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

import com.volcengine.veadk.knowledgebase.KnowledgebaseEntry;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface BaseKnowledgebaseBackend extends AutoCloseable {

    void precheckIndexNaming();

    default boolean addFromDirectory(Path directory) throws IOException {
        throw unsupported("addFromDirectory");
    }

    default boolean addFromFiles(List<Path> files) throws IOException {
        throw unsupported("addFromFiles");
    }

    default boolean addFromText(String text) throws IOException {
        throw unsupported("addFromText");
    }

    default boolean addFromText(List<String> text) throws IOException {
        throw unsupported("addFromText");
    }

    default boolean addDoc(String documentUri) throws IOException {
        throw unsupported("addDoc");
    }

    List<KnowledgebaseEntry> search(String query, int topK) throws IOException;

    @Override
    default void close() throws IOException {}

    private static UnsupportedOperationException unsupported(String operation) {
        return new UnsupportedOperationException(
                operation + " is not supported by this knowledgebase backend.");
    }
}
