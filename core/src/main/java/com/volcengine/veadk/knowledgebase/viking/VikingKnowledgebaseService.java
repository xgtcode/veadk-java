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
package com.volcengine.veadk.knowledgebase.viking;

import com.volcengine.veadk.integration.vikingknowledgebase.VikingKnowledgebaseWrapper;
import com.volcengine.veadk.knowledgebase.BaseKnowledgebaseService;
import com.volcengine.veadk.knowledgebase.KnowledgebaseEntry;
import com.volcengine.veadk.knowledgebase.SearchKnowledgebaseResponse;
import com.volcengine.veadk.utils.EnvUtil;
import io.reactivex.rxjava3.core.Single;
import java.util.List;

public class VikingKnowledgebaseService implements BaseKnowledgebaseService {

    private VikingKnowledgebaseWrapper wrapper;
    private String appName;

    public VikingKnowledgebaseService(String appName) {
        if (null != appName && !appName.matches("^[a-zA-Z][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException(
                    "appName can only contain English letters, numbers, and underscores, and must"
                            + " start with an English letter.");
        }
        wrapper = new VikingKnowledgebaseWrapper(EnvUtil.getAccessKey(), EnvUtil.getSecretKey());
        this.appName = appName;
        if (!wrapper.isCollectionExists(appName)) {
            wrapper.createCollection(appName);
        }
    }

    @Override
    public Single<SearchKnowledgebaseResponse> searchKnowledgebase(String query) {
        return Single.fromCallable(
                () -> {
                    List<? extends KnowledgebaseEntry> entries =
                            wrapper.searchKnowledge(this.appName, query, 5, null, true, 3);
                    SearchKnowledgebaseResponse response = new SearchKnowledgebaseResponse();
                    response.setKnowledgebaseEntries(entries);
                    return response;
                });
    }
}
