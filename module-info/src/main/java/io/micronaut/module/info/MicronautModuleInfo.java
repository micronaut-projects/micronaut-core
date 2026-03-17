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

import java.util.Optional;
import java.util.Set;

/**
 * Represents information about Micronaut modules which are available at runtime.
 * This can be used for diagnostics. Information is loaded via service loading.
 * Each module is recommended to extend the abstract class instead of directly implementing this interface.
 */
public interface MicronautModuleInfo {
    /**
     * The id for this module. It is recommended to
     * use groupId:artifactId
     * Do NOT include version information in the id.
     * @return the id for this module.
     */
    String getId();

    /**
     * A human readable name for this module.
     * @return the name of this module
     */
    String getName();

    /**
     * A description of this module.
     * @return the description
     */
    Optional<String> getDescription();

    /**
     * The version of this module.
     * @return the version
     */
    String getVersion();

    /**
     * Returns the Maven coordinates for this module,
     * if it can be represented so.
     * @return the Maven coordinates
     */
    Optional<MavenCoordinates> getMavenCoordinates();

    /**
     * A Micronaut module typically consists of a "main" module,
     * for example Micronaut Data, and other modules, like "Micronaut Data JDBC".
     * If this module is secondary module that should be grouped together
     * with a main module, this method should return the main module id.
     *
     * @return the parent module id, if any
     */
    Optional<String> getParentModuleId();

    /**
     * A set of tags assigned to this module.
     * @return the set of tags for this module
     */
    Set<String> getTags();
}
