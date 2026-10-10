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

import io.micronaut.context.WatchableBeanContext
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
import java.util.concurrent.ConcurrentHashMap

/**
 * A cache keyed by class, which forgets the classes a reload retires.
 */
@Requires(property = "spec.name", value = "BeanWatchSnippetsTest")
@Singleton
class TypeDescriptionCache(context: WatchableBeanContext) {

    private val cache = ConcurrentHashMap<Class<*>, String>()

    init {
        // tag::watch[]
        context.classChanges().watch { change -> cache.keys.removeIf(change::isStaleType) }
        // end::watch[]
    }

    fun describe(type: Class<*>): String = cache.computeIfAbsent(type) { it.name }

    val size: Int
        get() = cache.size
}
