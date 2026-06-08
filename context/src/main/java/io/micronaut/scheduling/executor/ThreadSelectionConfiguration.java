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
package io.micronaut.scheduling.executor;

/**
 * Configuration properties for controller and filter thread selection.
 *
 * @since 5.0.0
 * @author Jonas Konrad
 */
public interface ThreadSelectionConfiguration {
    /**
     * The target executor.
     *
     * @return The target executor
     */
    ThreadSelection getThreadSelection();

    /**
     * Whether executor redispatch should only happen on non-blocking threads.
     *
     * @return {@code true} if redispatch is restricted to non-blocking threads
     */
    boolean isRedispatchNonBlockingOnly();
}
