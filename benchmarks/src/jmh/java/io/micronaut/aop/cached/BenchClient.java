/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.aop.cached;

import io.micronaut.core.version.annotation.Version;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.annotation.Client;

/**
 * Answered by {@link BenchClientFilter} without a connection.
 */
@Client("http://localhost:9/bench")
public interface BenchClient {

    @Get("/items/{id}")
    String item(String id);

    @Get("/headers")
    @Header(name = "X-Bench", value = "1")
    String headers();

    @Get("/versioned")
    @Version("2")
    String versioned();

    @Get("/search")
    String search(@QueryValue String q, @QueryValue int page, @QueryValue int size, @Header("X-Token") String token);
}
