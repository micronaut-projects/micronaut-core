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
package io.micronaut.web.router.spi;

import io.micronaut.core.annotation.Experimental;

/**
 * Receives the slots of a {@link RoutePlan} whose templates match a path.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
@FunctionalInterface
public interface RouteCandidateSink {

    /**
     * A slot matches the path.
     *
     * @param slot  The slot number
     * @param path  The matched path
     * @param spans The captured path variables of the slot, in the order of {@link RouteSlot#captures()}:
     *              the start of the i-th value at {@code 2 * i} and its end at {@code 2 * i + 1}. The
     *              array is reused by the parser: copy what you keep
     */
    void candidate(int slot, String path, int[] spans);
}
