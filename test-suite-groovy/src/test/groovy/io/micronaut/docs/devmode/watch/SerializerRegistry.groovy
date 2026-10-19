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
package io.micronaut.docs.devmode.watch

import io.micronaut.context.annotation.Requires
// tag::imports[]
import io.micronaut.context.WatchableBeanContext
import io.micronaut.inject.BeanDefinition
import jakarta.inject.Singleton
// end::imports[]

@Requires(property = "spec.name", value = "BeanWatchSnippetsSpec")
// tag::class[]
@Singleton
final class SerializerRegistry {

    private volatile List<BeanDefinition<Serializer>> serializers = []

    SerializerRegistry(WatchableBeanContext context) {
        // no read of its own: the first batch is the read, and every change after it follows
        context.definitions(Serializer).watch { change -> serializers = sort(change.current()) }
    }

    List<BeanDefinition<Serializer>> serializers() {
        serializers
    }

    private static List<BeanDefinition<Serializer>> sort(Collection<BeanDefinition<Serializer>> definitions) {
        definitions.toSorted { it.beanType.name }
    }
}
// end::class[]
