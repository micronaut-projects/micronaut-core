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
package io.micronaut.docs.aop.binding;

import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuditBindingSpec {

    // tag::test[]
    @Test
    void testEveryOccurrenceMustMatch() {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
            var paymentService = applicationContext.getBean(PaymentService.class);
            paymentService.pay();
            paymentService.refund();

            var interceptor = applicationContext.getBean(PaymentAuditInterceptor.class);
            assertEquals(List.of("pay"), interceptor.getAudited());
        }
    }
    // end::test[]
}
