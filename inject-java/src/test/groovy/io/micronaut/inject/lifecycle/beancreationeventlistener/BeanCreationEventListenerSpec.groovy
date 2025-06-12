/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.inject.lifecycle.beancreationeventlistener

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanContext
import io.micronaut.context.exceptions.CircularDependencyException
import io.micronaut.core.type.Argument
import io.micronaut.inject.lifecycle.beancreationeventlistener.circular.Bar
import io.micronaut.inject.lifecycle.beancreationeventlistener.circular.Foo
import spock.lang.Specification

class BeanCreationEventListenerSpec extends Specification {

    void "test bean creation listener"() {
        given:
        ACreatedListener.initialized = false
        ACreatedListener.executed = false
        BCreationListener.initialized = false
        BCreationListener.executed = false
        CCreatedListener.initialized = false
        CCreatedListener.executed = false
        NotOffendingChainListener.initialized = false
        NotOffendingChainListener.executed = false
        OffendingChainListener.initialized = false
        OffendingChainListener.executed = false
        OffendingConstructorListener.initialized = false
        OffendingConstructorListener.executed = false
        OffendingFieldListener.initialized = false
        OffendingFieldListener.executed = false
        OffendingInterfaceListener.initialized = false
        OffendingInterfaceListener.executed = false
        OffendingMethodListener.initialized = false
        OffendingMethodListener.executed = false

        when:
        ApplicationContext context = ApplicationContext.run()

        then:
        ACreatedListener.initialized == false
        ACreatedListener.executed == false
        BCreationListener.initialized == false
        BCreationListener.executed == false
        CCreatedListener.initialized == false
        CCreatedListener.executed == false
        NotOffendingChainListener.initialized == false
        NotOffendingChainListener.executed == false
        OffendingChainListener.initialized == false
        OffendingChainListener.executed == false
        OffendingConstructorListener.initialized == false
        OffendingConstructorListener.executed == false
        OffendingFieldListener.initialized == false
        OffendingFieldListener.executed == false
        OffendingInterfaceListener.initialized == false
        OffendingInterfaceListener.executed == false
        OffendingMethodListener.initialized == false
        OffendingMethodListener.executed == false

        when:
        B b = context.getBean(B)

        then:
        b instanceof ChildB
        b.name == "good"

        and:
        ACreatedListener.initialized == true
        ACreatedListener.executed == true
        BCreationListener.initialized == true
        BCreationListener.executed == true
        CCreatedListener.initialized == false
        CCreatedListener.executed == false
        NotOffendingChainListener.initialized == true
        NotOffendingChainListener.executed == true
        OffendingChainListener.initialized == true
        OffendingChainListener.executed == true
        OffendingConstructorListener.initialized == true
        OffendingConstructorListener.executed == true
        OffendingFieldListener.initialized == true
        OffendingFieldListener.executed == true
        OffendingInterfaceListener.initialized == true
        OffendingInterfaceListener.executed == true
        OffendingMethodListener.initialized == true
        OffendingMethodListener.executed == true
        AllBeansListener.executed == true

        when:
        C c = context.getBean(C)

        then:
        c != null

        cleanup:
        context.close()
    }

    void "test application bean creation listener"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        when:
        B b = context.getBean(B)

        then:
        b instanceof ChildB
        b.name == "good"

        cleanup:
        context.close()
    }

    void "test bean creation listener eager"() {
        given:
        BeanContext context = ApplicationContext.builder().eagerInitSingletons(true).start()

        when:
        B b = context.getBean(B)

        then:
        b instanceof ChildB
        b.name == "good"

        cleanup:
        context.close()
    }

    void "test recursive listeners"() {
        given:
        BeanContext context = ApplicationContext.builder().properties(["spec": "RecursiveListeners"]).start()

        when:
        context.getBean(Foo)

        then:
        thrown(CircularDependencyException)

        when:
        context.getBean(Bar)

        then:
        thrown(CircularDependencyException)

        cleanup:
        context.close()
    }

    void "test bean type in listener"() {
        when:
        ApplicationContext context = ApplicationContext.run()
        I I = context.getBean(Argument.of(I.class, "someName"))

        ICreatedListener iListener = context.getBean(ICreatedListener);
        JCreatedListener jListener = context.getBean(JCreatedListener);

        then: "Bean type passed to getBean is passed to listener"
        iListener.executed
        Argument<I> iBeanType = iListener.beanType
        iBeanType.getType() == I.class
        iBeanType.getName() == "someName"

        and: "Bean type for J listener matches injected parameter name"
        jListener.executed
        Argument<J> jBeanType = jListener.beanType
        jBeanType.getType() == J.class
        jBeanType.getName() == "someParamName"
    }
}
