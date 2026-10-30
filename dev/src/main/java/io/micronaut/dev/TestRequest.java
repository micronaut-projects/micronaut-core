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
package io.micronaut.dev;

import io.micronaut.core.annotation.Experimental;

/**
 * A run of the tests asked for by a person or a tool rather than by a change, in test mode.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
public enum TestRequest {
    /**
     * The last run's tests again, with those that failed since and those the changes made while watching was off affected.
     */
    RERUN,
    /**
     * Every test.
     */
    ALL,
    /**
     * The test classes that failed in their last run.
     */
    FAILED
}
