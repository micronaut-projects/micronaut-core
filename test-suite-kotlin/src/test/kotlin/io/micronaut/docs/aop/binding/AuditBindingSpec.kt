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
package io.micronaut.docs.aop.binding

import io.kotest.core.spec.style.AnnotationSpec
import io.kotest.matchers.shouldBe
import io.micronaut.context.ApplicationContext

class AuditBindingSpec : AnnotationSpec() {

    // tag::test[]
    @Test
    fun testEveryOccurrenceMustMatch() {
        ApplicationContext.run().use { applicationContext ->
            val paymentService = applicationContext.getBean(PaymentService::class.java)
            paymentService.pay()
            paymentService.refund()

            val interceptor = applicationContext.getBean(PaymentAuditInterceptor::class.java)
            interceptor.audited shouldBe listOf("pay")
        }
    }
    // end::test[]
}
