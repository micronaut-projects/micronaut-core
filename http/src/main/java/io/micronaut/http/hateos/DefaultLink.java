/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.http.hateos;

import io.micronaut.http.MediaType;

import java.net.URI;
import java.util.Optional;

/**
 * Deprecated. Please use io.micronaut.http.hateoas.DefaultLink
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Deprecated
class DefaultLink extends io.micronaut.http.hateoas.DefaultLink {

    /**
     * @param uri The URI
     */
    //TODO AGB don't know how to solve this
    //protected class (not visible from another package)
    DefaultLink(URI uri) {
        this.href = uri;
    }
}
