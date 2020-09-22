/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http.client.bind;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.type.Argument;

import java.util.Optional;

/**
 * A registry of {@link ClientArgumentRequestBinder} instances.
 *
 * @author James Kleeh
 * @since 2.1.0
 */
@Experimental
@BootstrapContextCompatible
public interface HttpClientBinderRegistry {

    /**
     * Locate an {@link ClientArgumentRequestBinder} for the given argument.
     *
     * @param argument The argument
     * @param <T>      The argument type
     * @return An {@link Optional} of {@link ClientArgumentRequestBinder}
     */
    <T> Optional<ClientArgumentRequestBinder<T>> findArgumentBinder(@NonNull Argument<T> argument);

}
