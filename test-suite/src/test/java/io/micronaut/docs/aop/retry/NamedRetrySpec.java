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
package io.micronaut.docs.aop.retry;

import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;

import java.io.UncheckedIOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NamedRetrySpec {

    @Test
    void testNamedRetryPolicy() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "spec.name", "NamedRetrySpec",
            "micronaut.retry.policies.books.attempts", 5,
            "micronaut.retry.policies.books.delay", "1ms"
        ))) {
            NamedRetryBookService service = context.getBean(NamedRetryBookService.class);

            assertEquals("The Stand", service.findBook("The Stand").getTitle());
            assertEquals(5, service.reset());

            assertThrows(UncheckedIOException.class, () -> service.getBook("The Stand"));
            assertEquals(2, service.reset());
        }
    }
}
