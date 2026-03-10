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
package io.micronaut.docs.context.env;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.io.ResourceLoadStrategy;
import io.micronaut.core.io.ResourceLoadStrategyType;
import io.micronaut.runtime.Micronaut;

final class ConfigurationLoadStrategySnippet {

    private ConfigurationLoadStrategySnippet() {
    }

    void firstMatch(String[] args) {
        // tag::firstMatch[]
        Micronaut.build(args)
            .configurationLoadingStrategy(ResourceLoadStrategy.builder()
                .type(ResourceLoadStrategyType.FIRST_MATCH)
                .warnOnDuplicates(true))
            .start();
        // end::firstMatch[]
    }

    void mergeAll() {
        // tag::mergeAll[]
        ApplicationContext ctx = ApplicationContext.builder()
            .configurationLoadingStrategy(ResourceLoadStrategy.builder()
                .type(ResourceLoadStrategyType.MERGE_ALL))
            .start();
        // end::mergeAll[]

        ctx.close();
    }

    void mergeOrder() {
        // tag::mergeOrder[]
        ApplicationContext ctx = ApplicationContext.builder()
            .configurationLoadingStrategy(ResourceLoadStrategy.builder()
                .type(ResourceLoadStrategyType.MERGE_ALL)
                .mergeOrder("lib-.*\\.jar", "app-.*\\.jar"))
            .start();
        // end::mergeOrder[]

        ctx.close();
    }

    void restoreFirstMatch() {
        // tag::restoreFirstMatch[]
        ApplicationContext ctx = ApplicationContext.builder()
            .configurationLoadingStrategy(ResourceLoadStrategy.builder()
                .type(ResourceLoadStrategyType.FIRST_MATCH))
            .start();
        // end::restoreFirstMatch[]

        ctx.close();
    }
}
