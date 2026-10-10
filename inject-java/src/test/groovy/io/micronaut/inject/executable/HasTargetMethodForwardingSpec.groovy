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
package io.micronaut.inject.executable

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.DelegatingExecutableMethod
import io.micronaut.inject.ExecutableMethod
import io.micronaut.inject.ExecutionHandle

/**
 * A reference that wraps another answers whether there is a target method as the wrapped one does.
 */
class HasTargetMethodForwardingSpec extends AbstractTypeElementSpec {

    private static ExecutableMethod<Object, Object> noTargetMethod() {
        [
                getDeclaringType: { Object },
                getMethodName   : { 'none' },
                getArguments    : { Argument.ZERO_ARGUMENTS },
                getTargetMethod : { throw new AssertionError('not looked up') },
                hasTargetMethod : { false }
        ] as ExecutableMethod<Object, Object>
    }

    void "an execution handle of a method answers as the method does"() {
        expect:
        !ExecutionHandle.of(new Object(), noTargetMethod()).hasTargetMethod()
    }

    void "a delegating executable method answers as its target does"() {
        given:
        ExecutableMethod<Object, Object> target = noTargetMethod()
        DelegatingExecutableMethod<Object, Object> delegating = { target } as DelegatingExecutableMethod<Object, Object>

        expect:
        !delegating.hasTargetMethod()
    }

    void "the execution handles of the bean context answer as their method does"() {
        given:
        ApplicationContext context = buildContext('''
package forwarding;

import io.micronaut.context.annotation.Executable;
import jakarta.inject.Singleton;

@Singleton
class Service {
    @Executable
    String run() {
        return "ran";
    }
}
''')
        Class<?> serviceType = context.classLoader.loadClass('forwarding.Service')
        BeanDefinition<?> definition = context.getBeanDefinition(serviceType)

        expect: 'a handle found for a bean method stands for that method'
        context.findExecutionHandle(serviceType, 'run').get().hasTargetMethod()

        and: 'a handle created for a method answers as the method does'
        !context.createExecutionHandle(definition, noTargetMethod()).hasTargetMethod()
        context.createExecutionHandle(definition, definition.getRequiredMethod('run') as ExecutableMethod<Object, ?>).hasTargetMethod()

        cleanup:
        context.close()
    }
}
