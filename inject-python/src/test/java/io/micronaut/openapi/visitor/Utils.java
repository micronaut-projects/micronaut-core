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
package io.micronaut.openapi.visitor;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stands in for the state holder of the OpenAPI visitor, which this module does not depend on.
 */
public final class Utils {
    public static final AtomicInteger CLEANED = new AtomicInteger();

    private Utils() {
    }

    public static void clean() {
        CLEANED.incrementAndGet();
    }
}
