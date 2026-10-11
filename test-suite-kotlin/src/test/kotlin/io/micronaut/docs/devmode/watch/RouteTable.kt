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
import io.micronaut.context.watch.BeanDefinitionChange
import io.micronaut.context.watch.BeanDefinitionWatcher
import io.micronaut.inject.BeanDefinition
import io.micronaut.web.router.RouteBuilder
import jakarta.inject.Singleton
// end::imports[]

@Requires(property = "spec.name", value = "BeanWatchSnippetsTest")
// tag::class[]
@Singleton
class RouteTable(context: WatchableBeanContext) : BeanDefinitionWatcher<RouteBuilder> {

    @Volatile
    var builders: List<BeanDefinition<RouteBuilder>> = listOf()
        private set

    init {
        context.definitions(RouteBuilder::class.java).watch(this) // <1>
    }

    override fun onChange(change: BeanDefinitionChange<RouteBuilder>) {
        rebuild(change.current()) // <2>
    }

    private fun rebuild(current: Collection<BeanDefinition<RouteBuilder>>) {
        builders = current.toList()
    }
}
// end::class[]
