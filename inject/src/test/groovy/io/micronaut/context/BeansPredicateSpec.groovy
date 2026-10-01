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
import jakarta.inject.Named
import jakarta.inject.Singleton
import spock.lang.Specification

import java.util.function.Predicate

class BeansPredicateSpec extends Specification {

    private static final String NARROWED = "beansPredicateSpecNarrowed"

    private static final Predicate<QualifiedBeanType<?>> ACCEPT_ALL =
            { QualifiedBeanType<?> type -> true } as Predicate<QualifiedBeanType<?>>

    private static final Predicate<QualifiedBeanType<?>> REJECT_ALL =
            { QualifiedBeanType<?> type -> false } as Predicate<QualifiedBeanType<?>>

    /**
     * Matched on the annotation metadata the reference carries rather than on {@code getBeanType()}, so the
     * predicate never loads a bean class: the test classpath carries references whose bean type is not
     * resolvable, and narrowing has to be decided without touching them.
     */
    private static final Predicate<QualifiedBeanType<?>> ONLY_NARROWED_BEAN =
            { QualifiedBeanType<?> type -> type.stringValue(Named).orElse(null) == NARROWED } as Predicate<QualifiedBeanType<?>>

    void "test the beans predicate a context was built with is readable"() {
        given:
        ApplicationContext context = ApplicationContext.builder().beansPredicate(ACCEPT_ALL).build()

        expect:
        context.getBeansPredicate().is(ACCEPT_ALL)
        context.getContextConfiguration().beansPredicate().is(ACCEPT_ALL)

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
        ApplicationContext narrowed = ApplicationContext.builder().beansPredicate(ONLY_NARROWED_BEAN).build()

        expect: "only the one definition the predicate accepts is reported"
        narrowed.getBeanDefinitionReferences().size() == 1

        and: "the resolving views agree with the raw one"
        narrowed.getBeanDefinition(NarrowedBean) != null
        !narrowed.findBeanDefinition(OtherBean).isPresent()

        cleanup:
        narrowed.close()
    }

    void "test an accept-all predicate narrows nothing and a reject-all predicate narrows everything"() {
        given:
        ApplicationContext unfiltered = ApplicationContext.builder().build()
        ApplicationContext accepting = ApplicationContext.builder().beansPredicate(ACCEPT_ALL).build()
        ApplicationContext rejecting = ApplicationContext.builder().beansPredicate(REJECT_ALL).build()

        expect:
        unfiltered.findBeanDefinition(NarrowedBean).isPresent()
        accepting.getBeanDefinitionReferences().size() == unfiltered.getBeanDefinitionReferences().size()
        rejecting.getBeanDefinitionReferences().isEmpty()

        cleanup:
        unfiltered.close()
        accepting.close()
        rejecting.close()
    }

    @Singleton
    @Named(NARROWED)
    static class NarrowedBean {
    }

    @Singleton
    static class OtherBean {
    }
}
