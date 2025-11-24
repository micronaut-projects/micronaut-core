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
package io.micronaut.module.info;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Base class for Micronaut module information.
 * It is recommended, though not required, to extend this class.
 */
public abstract class AbstractMicronautModuleInfo implements MicronautModuleInfo {
    private final String id;
    private final String name;
    private final String description;
    private final String version;
    private final MavenCoordinates mavenCoordinates;
    private final String parentModuleId;
    private final Set<String> tags;

    public AbstractMicronautModuleInfo(String id,
                                       String name,
                                       String description,
                                       String version,
                                       MavenCoordinates mavenCoordinates,
                                       String parentModuleId,
                                       Set<String> tags) {
        this.id = validateId(id);
        this.name = name;
        this.description = description;
        this.version = version;
        this.mavenCoordinates = mavenCoordinates;
        this.parentModuleId = parentModuleId;
        this.tags = tags == null ? Set.of() : Set.copyOf(tags);
    }

    private static String validateId(String id) {
        var parts = id.split(":");
        if (parts.length != 2 || Arrays.stream(parts).map(String::trim).anyMatch(String::isEmpty)) {
            throw new IllegalArgumentException("Id must be of the form 'groupId:artifactId'");
        }
        return id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public Optional<MavenCoordinates> getMavenCoordinates() {
        return Optional.ofNullable(mavenCoordinates);
    }

    @Override
    public Optional<String> getParentModuleId() {
        return Optional.ofNullable(parentModuleId);
    }

    @Override
    public Set<String> getTags() {
        return tags;
    }

    @Override
    public String toString() {
        return id;
    }
}
