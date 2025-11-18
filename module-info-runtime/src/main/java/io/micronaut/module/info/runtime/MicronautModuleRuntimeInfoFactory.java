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

import io.micronaut.context.annotation.Factory;
import io.micronaut.core.annotation.Internal;
import io.micronaut.module.info.AbstractMicronautModuleInfo;
import io.micronaut.module.info.MicronautModuleInfo;
import io.micronaut.module.info.MicronautModuleInfoLoader;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Factory that creates runtime bean definitions for {@link MicronautRuntimeModule} instances.
 * This factory loads all {@link MicronautModuleInfo} instances and converts them to
 * resolved {@link MicronautRuntimeModule} instances with proper parent/child relationships.
 */
@Internal
@Factory
final class MicronautModuleRuntimeInfoFactory {

    @Singleton
    public List<MicronautRuntimeModule> getAllModules(List<MicronautModuleInfo> moduleInfos) {
        var serviceLoaded = MicronautModuleInfoLoader.getAllModules();
        var allModules = new ArrayList<>(moduleInfos);
        allModules.addAll(serviceLoaded);

        // Build lookup map
        Map<String, MicronautModuleInfo> infoById = new HashMap<>();
        for (var mi : allModules) {
            infoById.put(mi.getId(), mi);
        }

        // Create a single shared virtual root with a mutable children list we keep a reference to
        List<MicronautRuntimeModule> rootChildren = new ArrayList<>();
        var virtualRoot = new MicronautRuntimeModule(
            new AbstractMicronautModuleInfo("root:root", "root", null, "root", null, null, Set.of()) { },
            null,
            rootChildren
        );

        // Memoized maps of resolved runtime modules and their mutable children lists
        Map<String, MicronautRuntimeModule> runtimeById = new HashMap<>();
        Map<String, List<MicronautRuntimeModule>> childrenLists = new HashMap<>();

        // Resolve all modules (parents first) and wire up children lists
        for (var mi : allModules) {
            buildModule(mi.getId(), infoById, runtimeById, childrenLists, virtualRoot, rootChildren);
        }

        // Return the full, resolved list in the same order as discovered
        return allModules.stream()
            .map(mi -> runtimeById.get(mi.getId()))
            .toList();
    }

    private MicronautRuntimeModule buildModule(String id,
                                               Map<String, MicronautModuleInfo> infoById,
                                               Map<String, MicronautRuntimeModule> runtimeById,
                                               Map<String, List<MicronautRuntimeModule>> childrenLists,
                                               MicronautRuntimeModule virtualRoot,
                                               List<MicronautRuntimeModule> rootChildren) {
        var existing = runtimeById.get(id);
        if (existing != null) {
            return existing;
        }

        var info = infoById.get(id);
        if (info == null) {
            // Unknown module id -> attach to root
            return virtualRoot;
        }

        // Resolve parent first
        MicronautRuntimeModule parent = info.getParentModuleId()
            .map(pid -> buildModule(pid, infoById, runtimeById, childrenLists, virtualRoot, rootChildren))
            .orElse(virtualRoot);

        // Create this module with an empty, mutable children list and keep a reference to it
        var children = new ArrayList<MicronautRuntimeModule>();
        var module = new MicronautRuntimeModule(info, parent, children);

        // Memoize module and its children list
        runtimeById.put(id, module);
        childrenLists.put(id, children);

        // Register module as a child of its parent using the parent's mutable list reference
        if (info.getParentModuleId().isPresent()) {
            var parentId = info.getParentModuleId().get();
            var parentChildren = childrenLists.get(parentId);
            if (parentChildren != null) {
                parentChildren.add(module);
            } else {
                // Parent is the virtual root or missing, attach to root
                rootChildren.add(module);
            }
        } else {
            // Top-level module, attach to virtual root
            rootChildren.add(module);
        }

        return module;
    }
}
