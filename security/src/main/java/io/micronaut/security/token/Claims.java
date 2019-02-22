/*
 * Copyright 2017-2019 original authors
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

package io.micronaut.security.token;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;

/**
 * Encapsulate authentication claims.
 *
 * @since 1.1.0
 * @author Sergio del Amo
 */
public interface Claims {

    /**
     *
     * @param claimName the claim name
     * @return {@code null} if the claim not exist or the Claim value.
     */
    @Nullable
    Object get(String claimName);

    /**
     *
     * @return All claim names.
     */
    @Nonnull
    Set<String> names();

    /**
     *
     * @param claimName the claim name
     * @return {@code false} if the claim does not exist.
     */
    boolean contains(String claimName);
}
