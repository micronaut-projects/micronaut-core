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
package io.micronaut.docs.devmode.tck

import io.micronaut.context.BeanContext
import io.micronaut.context.WatchableBeanContext
import io.micronaut.context.annotation.Requires
import io.micronaut.inject.BeanDefinition
import jakarta.inject.Singleton

/**
 * A module's registry, which watches the definitions of [Greeting] so that it hands out the current generation's.
 */
@Requires(property = "spec.name", value = "GreetingRegistryReloadTest")
@Singleton
class GreetingRegistry(private val context: BeanContext) {

    @Volatile
    private var latest: BeanDefinition<Greeting>? = null

    init {
        if (context is WatchableBeanContext) {
            context.definitions(Greeting::class.java).watch { change -> change.current().forEach { latest = it } }
        }
    }

    fun current(): Greeting? = latest?.let { context.getBean(it) }
}
