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
package io.micronaut.context

import io.micronaut.inject.QualifiedBeanType
import spock.lang.Specification

import java.util.function.Predicate

class BeansPredicateSpec extends Specification {

    void "test the beans predicate a context was built with is readable"() {
        given:
        Predicate<QualifiedBeanType<?>> predicate = (Predicate) { QualifiedBeanType<?> type -> true }
        ApplicationContext context = ApplicationContext.builder().beansPredicate(predicate).build()

        expect:
        context.getBeansPredicate().is(predicate)
        context.getContextConfiguration().beansPredicate().is(predicate)

        cleanup:
        context.close()
    }

    void "test no beans predicate is exposed when the context was not narrowed"() {
        given:
        ApplicationContext context = ApplicationContext.builder().build()

        expect:
        context.getBeansPredicate() == null

        cleanup:
        context.close()
    }

    void "test the raw references view is narrowed by the same predicate"() {
        given:
        ApplicationContext unfiltered = ApplicationContext.builder().build()
        Predicate<QualifiedBeanType<?>> acceptAll = (Predicate) { QualifiedBeanType<?> type -> true }
        ApplicationContext accepting = ApplicationContext.builder().beansPredicate(acceptAll).build()
        Predicate<QualifiedBeanType<?>> rejectAll = (Predicate) { QualifiedBeanType<?> type -> false }
        ApplicationContext rejecting = ApplicationContext.builder().beansPredicate(rejectAll).build()

        expect:
        !unfiltered.getBeanDefinitionReferences().isEmpty()
        accepting.getBeanDefinitionReferences().size() == unfiltered.getBeanDefinitionReferences().size()
        rejecting.getBeanDefinitionReferences().isEmpty()

        cleanup:
        unfiltered.close()
        accepting.close()
        rejecting.close()
    }
}
