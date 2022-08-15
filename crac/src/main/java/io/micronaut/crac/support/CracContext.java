/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.crac.support;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;

/**
 * The gateway between Micronaut and the CRaC api. Takes our own internal resources, and uses them as
 * delegates to the CRaC api.
 *
 * @author Tim Yates
 * @since 3.7.0
 */
@Experimental
@FunctionalInterface
public interface CracContext {

    /**
     * Create a {@link org.crac.Resource} from the given {@link OrderedCracResource} and register it with the CRaC {@link org.crac.Context}.
     *
     * @param orderedCracResource CRaC Resource.
     */
    void register(@NonNull OrderedCracResource orderedCracResource);
}
