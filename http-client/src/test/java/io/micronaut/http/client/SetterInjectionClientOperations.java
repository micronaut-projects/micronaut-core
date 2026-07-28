/*
 * Copyright 2026 original authors
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
package io.micronaut.http.client;

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.discovery.DiscoveryClient;
import io.micronaut.http.annotation.Get;
import io.micronaut.retry.annotation.Retryable;
import jakarta.validation.constraints.NotNull;
import org.reactivestreams.Publisher;

import java.util.Collections;
import java.util.List;

interface SetterInjectionClientOperations extends DiscoveryClient {

    @Override
    default Publisher<List<String>> getServiceIds() {
        return Publishers.just(Collections.emptyList());
    }

    @Get
    @Retryable
    Publisher<List<String>> index(@NotNull String value);
}
