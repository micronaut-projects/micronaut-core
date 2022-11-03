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
package io.micronaut.retry;

/**
 * State for Circuit breaker phases.
 *
 * @author graemerocher
 * @since 1.0
 */
public enum CircuitState {

    /**
     * The circuit is open and downstream logic should not be invoked.
     */
    OPEN,

    /**
     * The circuit is closed and downstream logic should proceed as normal.
     */
    CLOSED,

    /**
     * The circuit has just closed to allow a single downstream call to check if it is backup.
     */
    HALF_OPEN
}
