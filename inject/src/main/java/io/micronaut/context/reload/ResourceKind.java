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
package io.micronaut.context.reload;

import io.micronaut.core.annotation.Experimental;

/**
 * The kinds of resource roots a development launcher distinguishes, and a resource watch selects by.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
public enum ResourceKind {
    /**
     * Configuration files such as {@code application.yml}.
     */
    CONFIG,
    /**
     * View templates.
     */
    VIEWS,
    /**
     * Static files served as they are.
     */
    STATIC,
    /**
     * Message bundles.
     */
    I18N,
    /**
     * Any other resource.
     */
    OTHER
}
