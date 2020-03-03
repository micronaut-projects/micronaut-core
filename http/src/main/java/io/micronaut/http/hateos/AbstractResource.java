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

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Produces;

/**
 * Deprecated. Please use io.micronaut.http.hateoas.AbstractResource
 *
 * @author Graeme Rocher
 * @since 1.0
 * @param <Impl> The implementation class.
 * @deprecated Use {@link io.micronaut.http.hateoas.AbstractResource} instead
 */
@Produces(MediaType.APPLICATION_HAL_JSON)
@Deprecated
public abstract class AbstractResource<Impl extends AbstractResource> extends io.micronaut.http.hateoas.AbstractResource<Impl> {

}
