/*
 * Copyright 2017 Eclipse Foundation
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jnosql.diana.elasticsearch.document;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.get.MultiGetItemResponse;
import org.elasticsearch.action.get.MultiGetResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.jnosql.diana.api.ExecuteAsyncQueryException;
import org.jnosql.diana.api.document.DocumentEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.util.Collections.synchronizedList;
import static java.util.stream.StreamSupport.stream;

class FindAsyncListener {

    private final Consumer<List<DocumentEntity>> callBack;

    private final String collection;

    private final List<DocumentEntity> entities = synchronizedList(new ArrayList<>());

    private AtomicBoolean ids = new AtomicBoolean(true);

    private AtomicBoolean query = new AtomicBoolean(true);

    FindAsyncListener(Consumer<List<DocumentEntity>> callBack, String collection) {
        this.callBack = callBack;
        this.collection = collection;
    }

    ActionListener<MultiGetResponse> getIds() {
        ids.set(false);
        return new ActionListener<MultiGetResponse>() {
            @Override
            public void onResponse(MultiGetResponse multiGetItemResponses) {
                ids.set(true);

                Stream.of(multiGetItemResponses.getResponses())
                        .map(MultiGetItemResponse::getResponse)
                        .map(h -> new ElasticsearchEntry(h.getId(), collection, h.getSourceAsMap()))
                        .filter(ElasticsearchEntry::isNotEmpty)
                        .map(ElasticsearchEntry::toEntity)
                        .forEach(entities::add);
                if (ids.get() && query.get()) {
                    callBack.accept(entities);
                }
            }

            @Override
            public void onFailure(Exception e) {
                throw new ExecuteAsyncQueryException("An error when execute query", e);
            }
        };
    }

    ActionListener<SearchResponse> getSearch() {
        query.set(false);
        return new ActionListener<SearchResponse>() {
            @Override
            public void onResponse(SearchResponse searchResponse) {
                query.set(true);
                stream(searchResponse.getHits().spliterator(), false)
                        .map(h -> new ElasticsearchEntry(h.getId(), collection, h.sourceAsMap()))
                        .filter(ElasticsearchEntry::isNotEmpty)
                        .map(ElasticsearchEntry::toEntity)
                        .forEach(entities::add);
                if (ids.get() && query.get()) {
                    callBack.accept(entities);
                }
            }

            @Override
            public void onFailure(Exception e) {
                throw new ExecuteAsyncQueryException("An error when execute query", e);
            }
        };
    }

}
