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
package io.micronaut.python.annotation.processing.test.junit;

import io.micronaut.context.DefaultApplicationContextBuilder;

/**
 * An application context builder for {@code @MicronautTest(contextBuilder = ...)} that loads the
 * beans of the class loader holding the compiled Python test classes (the context class loader of
 * the thread running the tests).
 */
public class CompiledTestContextBuilder extends DefaultApplicationContextBuilder {

    public CompiledTestContextBuilder() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader != null) {
            classLoader(classLoader);
        }
    }
}
