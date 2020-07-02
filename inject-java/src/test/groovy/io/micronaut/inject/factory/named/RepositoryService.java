/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.inject.factory.named;

import com.github.benmanes.caffeine.cache.Cache;
import io.reactivex.Flowable;
import io.reactivex.Maybe;

import javax.inject.Named;
import javax.inject.Singleton;

@Singleton
public class RepositoryService {
    private final Cache<String, Flowable<String>> orgRepositoryCache;
    private final Cache<String, Maybe<String>> repositoryCache;

    public RepositoryService(
            @Named("orgRepositoryCache") Cache<String, Flowable<String>> orgRepositoryCache,
            @Named("repositoryCache") Cache<String, Maybe<String>> repositoryCache) {
        this.orgRepositoryCache = orgRepositoryCache;
        this.repositoryCache = repositoryCache;
    }

    public Cache<String, Flowable<String>> getOrgRepositoryCache() {
        return orgRepositoryCache;
    }

    public Cache<String, Maybe<String>> getRepositoryCache() {
        return repositoryCache;
    }
}
