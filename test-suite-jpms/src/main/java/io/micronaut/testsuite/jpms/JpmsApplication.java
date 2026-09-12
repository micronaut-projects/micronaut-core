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
package io.micronaut.testsuite.jpms;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.optim.StaticOptimizations;
import io.micronaut.core.type.TypeInformationProvider;

import java.util.ServiceLoader;

/**
 * Runs dependency injection and service loading from a named module.
 */
public final class JpmsApplication {

    private static final String MODULE_NAME = "io.micronaut.testsuite.jpms";

    private JpmsApplication() {
    }

    /**
     * Runs the smoke test.
     *
     * @param args ignored command line arguments
     */
    public static void main(String[] args) {
        Module applicationModule = JpmsApplication.class.getModule();
        if (!applicationModule.isNamed() || !MODULE_NAME.equals(applicationModule.getName())) {
            throw new AssertionError("Application was not launched as module " + MODULE_NAME);
        }

        try (ApplicationContext context = ApplicationContext.run()) {
            String greeting = context.getBean(GreetingService.class).greet();
            if (!"Hello, JPMS!".equals(greeting)) {
                throw new AssertionError("Unexpected greeting: " + greeting);
            }
        }

        boolean injectProviderFound = ServiceLoader.load(TypeInformationProvider.class)
            .stream()
            .anyMatch(provider -> "io.micronaut.inject".equals(provider.type().getModule().getName()));
        if (!injectProviderFound) {
            throw new AssertionError("Micronaut inject service provider was not found");
        }

        TestStaticOptimization optimization = StaticOptimizations.get(TestStaticOptimization.class).orElseThrow();
        if (!"loaded".equals(optimization.value())) {
            throw new AssertionError("Micronaut core service consumer did not load the optimization");
        }
    }
}
