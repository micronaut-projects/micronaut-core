/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.core.value.OptionalMultiValues;
import io.micronaut.http.hateoas.Link;

/**
 * Deprecated. Please use io.micronaut.http.hateoas.Resource
 *
 * @author Graeme Rocher
 * @since 1.0
 * @deprecated Use {@link io.micronaut.http.hateoas.Resource} instead
 */
@Deprecated
public interface Resource extends io.micronaut.http.hateoas.Resource {
    @SuppressWarnings("unchecked")
    @Override
    default OptionalMultiValues<Link> getLinks() {
        return OptionalMultiValues.EMPTY_VALUES;
    }

    @SuppressWarnings("unchecked")
    @Override
    default OptionalMultiValues<Resource> getEmbedded() {
        return OptionalMultiValues.EMPTY_VALUES;
    }
}
