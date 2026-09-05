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
package io.micronaut.function

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.BeanDefinition
import spock.lang.Specification

/**
 * {@code @FunctionBean} is declared on factory methods as well as on types, so the compile-time index
 * has to be written from the producing method's metadata for the index to replace the stereotype scan.
 */
class FunctionBeanIndexedLookupSpec extends Specification {

    void "test function beans declared on factory methods are enumerable through the compile-time index"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        when: 'the beans are looked up through the index'
        Collection<BeanDefinition<?>> indexed = context.getBeanDefinitions(FunctionBean)

        and: 'and by scanning every bean definition, which is what the index replaces'
        Collection<BeanDefinition<?>> scanned = context.getAllBeanDefinitions().findAll { it.hasStereotype(FunctionBean) }

        then: 'every definition names the function it declares'
        indexed.every { it.stringValue(FunctionBean).isPresent() }

        and: 'the factory-method functions are actually present'
        indexed.collect { it.stringValue(FunctionBean).get() }.toSorted().containsAll(['fullname', 'round', 'supplier', 'upper'])

        and: 'both answer the same beans'
        indexed*.beanType.name.toSorted() == scanned*.beanType.name.toSorted()

        cleanup:
        context.close()
    }
}
