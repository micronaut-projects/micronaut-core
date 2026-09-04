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
package io.micronaut.messaging

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.BeanDefinition
import io.micronaut.messaging.annotation.MessageListener
import spock.lang.Specification

import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

/**
 * {@code @MessageListener} is the stereotype the transport specific listener annotations are built on,
 * so indexing it by itself indexes every listener bean without those annotations doing anything.
 */
class MessageListenerIndexedLookupSpec extends Specification {

    @Retention(RetentionPolicy.RUNTIME)
    @MessageListener
    @interface TransportListener {
    }

    @TransportListener
    static class OneListener {
    }

    @TransportListener
    static class TwoListener {
    }

    void "test beans carrying a transport annotation built on @MessageListener are indexed by it"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        when: 'the listeners are looked up through the index'
        Collection<BeanDefinition<?>> indexed = context.getBeanDefinitions(MessageListener)

        and: 'and by scanning every bean definition, which is what the index replaces'
        Collection<BeanDefinition<?>> scanned = context.getAllBeanDefinitions().findAll { it.hasStereotype(MessageListener) }

        then: 'the listeners are found through an annotation that never mentions @Indexed itself'
        indexed*.beanType.name.containsAll([OneListener.name, TwoListener.name])

        and: 'both answer the same beans, in the same order'
        indexed*.beanType.name == scanned*.beanType.name

        cleanup:
        context.close()
    }
}
