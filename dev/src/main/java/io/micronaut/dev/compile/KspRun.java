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
package io.micronaut.dev.compile;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.NullMarked;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * One KSP run, as the Kotlin compilation sees it: no KSP type is referenced here.
 *
 * @param succeeded Whether it succeeded
 * @param work Its working directory, beside the class output: {@code classes}, {@code kotlin}, {@code java} and {@code resources} under it hold what the processors wrote
 * @param originsByOutput The files the processors created in this run, by absolute path, each with the sources it was created for
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@NullMarked
record KspRun(boolean succeeded, Path work, Map<Path, Set<Path>> originsByOutput) {

    /**
     * The files created under a directory of the working directory.
     *
     * @param name {@code classes}, {@code kotlin}, {@code java} or {@code resources}
     * @return The files, with their origins
     */
    Map<Path, Set<Path>> under(String name) {
        Path directory = work.resolve(name);
        Map<Path, Set<Path>> selected = new LinkedHashMap<>();
        for (Map.Entry<Path, Set<Path>> entry : originsByOutput.entrySet()) {
            if (entry.getKey().startsWith(directory)) {
                selected.put(entry.getKey(), entry.getValue());
            }
        }
        return selected;
    }
}
