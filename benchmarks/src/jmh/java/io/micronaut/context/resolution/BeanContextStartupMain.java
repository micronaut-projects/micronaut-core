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
package io.micronaut.context.resolution;

import io.micronaut.context.ApplicationContext;

import java.util.Map;

/**
 * A single context lifecycle for startup CPU and allocation profiling from JVM launch.
 */
public final class BeanContextStartupMain {
    private BeanContextStartupMain() {
    }

    /**
     * Starts the fixture context and resolves its provider holder.
     *
     * @param args Unused
     */
    public static void main(String[] args) {
        long start = System.nanoTime();
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", Fixtures.SPEC))) {
            context.getBean(Fixtures.ProviderHolder.class);
            System.out.println("contextStartupNs=" + (System.nanoTime() - start));
        }
    }
}
