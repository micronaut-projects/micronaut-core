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
import io.micronaut.core.order.Ordered;

/**
 * A Coordinated Restore at Checkpoint Resource that may also be Ordered.
 * This matches the {@link org.crac.Resource} interface, but cannot extend it to prevent leaking org.crac
 * into the Micronaut classpath.
 *
 * @author Tim Yates
 * @since 3.7.0
 */
@Experimental
public interface OrderedCracResource extends Ordered {

    /**
     * Invoked by a {@code Context} as a notification about checkpoint.
     *
     * @param context {@code Context} providing notification
     * @throws Exception if the method have failed
     */
    @SuppressWarnings("java:S112")
    void beforeCheckpoint(@NonNull CracContext context) throws Exception;

    /**
     * Invoked by a {@code Context} as a notification about restore.
     *
     * @param context {@code Context} providing notification
     * @throws Exception if the method have failed
     */
    @SuppressWarnings("java:S112")
    void afterRestore(@NonNull CracContext context) throws Exception;
}
