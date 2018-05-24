/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.inject.lifecyle

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import spock.lang.Specification

import javax.inject.Singleton

/**
 * Created by graemerocher on 26/05/2017.
 */
class BeanCreationEventListenerSpec extends Specification {

    void "test bean creation listener"() {
        given:
        BeanContext context = new DefaultBeanContext().start()

        when:
        B b= context.getBean(B)

        then:
        b instanceof ChildB
        b.name == "good"

    }

    @Singleton
    static class B {
        String name
    }

    static class ChildB extends B {
        B original

        ChildB(B original) {
            this.original = original
        }
    }

    @Singleton
    static class BCreationListener implements BeanCreatedEventListener<B> {

        @Override
        B onCreated(BeanCreatedEvent<B> event) {
            ChildB childB = new ChildB(event.bean)
            childB.name = "good"
            return childB
        }
    }
}
