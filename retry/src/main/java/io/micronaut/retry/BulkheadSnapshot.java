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
package io.micronaut.retry;

import io.micronaut.core.annotation.Experimental;

/**
 * How full a bulkhead is at one moment.
 *
 * @param name The name of the bulkhead
 * @param maxConcurrent How many calls it runs at the same time
 * @param active How many calls hold a place
 * @param waiting How many calls wait for one
 * @author Graeme Rocher
 * @since 5.3.0
 */
@Experimental
public record BulkheadSnapshot(String name, int maxConcurrent, int active, int waiting) {
}
