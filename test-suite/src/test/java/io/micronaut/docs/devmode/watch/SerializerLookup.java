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
package io.micronaut.docs.devmode.watch;

import io.micronaut.context.annotation.Requires;
// tag::imports[]
import io.micronaut.context.WatchableBeanContext;
import jakarta.inject.Singleton;

import java.util.List;
// end::imports[]

@Requires(property = "spec.name", value = "BeanWatchSnippetsTest")
// tag::class[]
@Singleton
public final class SerializerLookup {

    private final List<Serializer> serializers;

    public SerializerLookup(List<Serializer> serializers, WatchableBeanContext context) {
        this.serializers = serializers;
        context.classChanges().watch(change -> {
            if (serializers.stream().anyMatch(change::isStaleInstance)) {
                context.recreate(this); // <1>
            }
        });
    }

    public Serializer serializerFor(Object value) {
        return serializers.stream()
            .filter(serializer -> serializer.supports(value))
            .findFirst()
            .orElseThrow();
    }
}
// end::class[]
