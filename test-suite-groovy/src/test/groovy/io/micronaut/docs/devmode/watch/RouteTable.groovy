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

@Requires(property = "spec.name", value = "BeanWatchSnippetsSpec")
// tag::class[]
@Singleton
final class RouteTable implements BeanDefinitionWatcher<RouteBuilder> {

    private volatile List<BeanDefinition<RouteBuilder>> builders = []

    RouteTable(WatchableBeanContext context) {
        context.definitions(RouteBuilder).watch(this) // <1>
    }

    @Override
    void onChange(BeanDefinitionChange<RouteBuilder> change) {
        rebuild(change.current()) // <2>
    }

    private void rebuild(Collection<BeanDefinition<RouteBuilder>> current) {
        builders = List.copyOf(current)
    }

    List<BeanDefinition<RouteBuilder>> builders() {
        builders
    }
}
// end::class[]
