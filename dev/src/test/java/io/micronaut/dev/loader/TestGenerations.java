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
package io.micronaut.dev.loader;

import java.nio.file.Path;
import java.util.List;

/**
 * Generations for the tests of other packages, whose constructor is package-private.
 */
public final class TestGenerations {

    private TestGenerations() {
    }

    /**
     * @param generation The number of the generation
     * @param roots The directories, read as they are
     * @return A generation over the directories, without a snapshot and without a parent
     */
    public static GenerationClassLoader over(int generation, List<Path> roots) {
        return new GenerationClassLoader(generation, roots, null);
    }
}
