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
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.type.Argument;

import java.util.Collections;
import java.util.List;

/**
 * A {@link ClientArgumentRequestBinder} that is resolved based on the argument type.
 *
 * @param <T> The argument type
 * @author James Kleeh
 * @since 2.1.0
 */
@Experimental
public interface TypedClientArgumentRequestBinder<T> extends ClientArgumentRequestBinder<T> {

    /**
     * @return The argument type.
     */
    @NonNull
    Argument<T> argumentType();

    /**
     * Returns additional super types.
     *
     * @return Additional supers types
     */
    default @NonNull
    List<Class<?>> superTypes() {
        return Collections.emptyList();
    }
}
