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

import com.volcengine.veadk.knowledgebase.BaseKnowledgebaseService;
import com.volcengine.veadk.knowledgebase.KnowledgeBase;
import com.volcengine.veadk.knowledgebase.SearchKnowledgebaseResponse;
import io.reactivex.rxjava3.core.Single;

/**
 * @deprecated use {@link KnowledgeBase#viking(String)} or {@link KnowledgeBase#builder()} with the
 *     {@code viking} backend.
 */
@Deprecated
public class VikingKnowledgebaseService implements BaseKnowledgebaseService {

    private final KnowledgeBase knowledgeBase;

    public VikingKnowledgebaseService(String appName) {
        this.knowledgeBase =
                KnowledgeBase.builder().backend("viking").appName(appName).topK(5).build();
    }

    @Override
    public Single<SearchKnowledgebaseResponse> searchKnowledgebase(String query) {
        return knowledgeBase.searchKnowledgebase(query);
    }
}
