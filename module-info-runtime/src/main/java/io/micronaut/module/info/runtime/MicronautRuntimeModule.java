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
import io.micronaut.module.info.MavenCoordinates;
import io.micronaut.module.info.MicronautModuleInfo;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Represents a resolved Micronaut module at runtime with parent and children references.
 * Instead of IDs, this runtime bean uses references to model the hierarchy and replaces
 * MicronautModuleInfo which uses IDs for hierarchical relationships.
 */
@Experimental
@NonNull
public final class MicronautRuntimeModule {
    private final MicronautModuleInfo info;
    private final MicronautRuntimeModule parent;
    private final List<MicronautRuntimeModule> children;

    public MicronautRuntimeModule(MicronautModuleInfo info,
                                  MicronautRuntimeModule parent,
                                  List<MicronautRuntimeModule> children) {
        this.info = info;
        this.parent = parent;
        this.children = children;
    }

    public MicronautModuleInfo getInfo() {
        return info;
    }

    /**
     * The id for this module. It is recommended to use "groupId:artifactId".
     * Do NOT include version information in the id.
     * @return the id for this module.
     */
    public String getId() {
        return info.getId();
    }

    /**
     * A human-readable name for this module.
     * @return the name of this module
     */
    public String getName() {
        return info.getName();
    }

    /**
     * A description of this module.
     * @return the description
     */
    public Optional<String> getDescription() {
        return info.getDescription();
    }

    /**
     * The version of this module.
     * @return the version
     */
    public String getVersion() {
        return info.getVersion();
    }

    /**
     * Returns the Maven coordinates for this module,
     * if it can be represented so.
     * @return the Maven coordinates
     */
    public Optional<MavenCoordinates> getMavenCoordinates() {
        return info.getMavenCoordinates();
    }

    /**
     * Returns the parent module, if any.
     * @return the parent module
     */
    public Optional<MicronautRuntimeModule> getParent() {
        return Optional.ofNullable(parent);
    }

    /**
     * Returns the child modules.
     *
     * @return the child modules
     */
    public List<MicronautRuntimeModule> getChildren() {
        return Collections.unmodifiableList(children);
    }

    /**
     * A set of tags assigned to this module.
     * @return the set of tags for this module
     */
    public Set<String> getTags() {
        return info.getTags();
    }

    /**
     * Returns the root module descriptor.
     * @return the root descriptor
     */
    public MicronautRuntimeModule getRoot() {
        if (parent == null) {
            return this;
        }
        return parent.getRoot();
    }

    @Override
    public String toString() {
        return getId();
    }
}
