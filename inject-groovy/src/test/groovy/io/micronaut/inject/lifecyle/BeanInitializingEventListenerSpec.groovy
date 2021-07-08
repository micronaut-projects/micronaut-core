/*
 * Copyright 2017-2019 original authors
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
import io.micronaut.context.annotation.Factory
import io.micronaut.context.event.BeanInitializedEventListener
import io.micronaut.context.event.BeanInitializingEvent
import spock.lang.Specification

import jakarta.annotation.PostConstruct
import jakarta.inject.Inject
import jakarta.inject.Provider
import jakarta.inject.Singleton

/**
 * Created by graemerocher on 26/05/2017.
 */
class BeanInitializingEventListenerSpec extends Specification {
    void "test bean initializing event listener"() {
        given:
        BeanContext context = new DefaultBeanContext().start()

        when:"A bean is retrieved where a BeanInitializedEventListener is present"
        B b= context.getBean(B)

        then:"The event is triggered prior to @PostConstruct hooks"
        b.name == "CHANGED"

    }

    static class B {
        String name
    }

    @Singleton
    static class A {
        String name = "A"
    }

    @Factory
    static class BFactory implements Provider<B> {
        String name = "original"
        boolean postConstructCalled = false
        boolean getCalled = false
        @Inject private A fieldA
        @Inject protected A anotherField
        @Inject A a
        private A methodInjected
        @Inject private injectMe(A a) {
            methodInjected = a
        }
        A getFieldA() {
            return fieldA
        }

        A getAnotherField() {
            return anotherField
        }

        A getMethodInjected() {
            return methodInjected
        }

        @PostConstruct
        void init() {
            postConstructCalled = true
            name = name.toUpperCase()
        }
        @Singleton
        B get() {
            getCalled = true
            return new B(name: name )
        }
    }

    @Singleton
    static class MyListener implements BeanInitializedEventListener<BFactory> {

        @Override
        BFactory onInitialized(BeanInitializingEvent<BFactory> event) {
            BFactory bean = event.bean
            assert bean.methodInjected
            assert bean.fieldA
            assert bean.anotherField
            assert bean.a
            assert !bean.postConstructCalled
            assert !bean.getCalled
            bean.name = "changed"
            return bean
        }

    }
}
