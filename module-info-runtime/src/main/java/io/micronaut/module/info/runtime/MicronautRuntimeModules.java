/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.module.info.runtime;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Provides information about the Micronaut modules which are found at runtime.
 * The modules are organized in a hierarchy (for example, `micronaut-data-jdbc`
 * would be a child of `micronaut-data`).
 */
@Singleton
@Experimental
@NonNull
public final class MicronautRuntimeModules {
    private final MicronautRuntimeModule root;

    public MicronautRuntimeModules(List<MicronautRuntimeModule> modules) {
        var roots = modules.stream()
            .map(MicronautRuntimeModule::getRoot)
            .distinct()
            .toList();
        if (roots.size() != 1) {
            throw new IllegalStateException("Expected to find a single root but got: " + roots.size());
        }
        this.root = roots.getFirst();
    }

    /**
     * The root of the Micronaut modules hierarchy.
     * That node doesn't correspond to any real Micronaut module, it's
     * a virtual node which gives access to the children modules.
     * @return the root node.
     */
    public MicronautRuntimeModule getRoot() {
        return root;
    }
}
