/*
 * Copyright 2003-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.python.cli.commands;

import java.nio.file.Path;
import java.util.concurrent.Callable;

abstract class BaseCommand implements Callable<Integer> {
    protected Path pyronautVenvCacheDir() {
        var virtualEnv = System.getenv("VIRTUAL_ENV");
        if (virtualEnv != null) {
            return Path.of(virtualEnv).resolve("lib/pyronaut");
        }
        throw new IllegalStateException("Virtual env not found.");
    }

    protected Path annotationProcessorDependenciesDir() {
        return pyronautVenvCacheDir().resolve("dependencies/annotationProcessor");
    }

    protected Path compileDependenciesDir() {
        return pyronautVenvCacheDir().resolve("dependencies/compile");
    }
}
