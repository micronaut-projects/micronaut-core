/*
 * Copyright 2017 original authors
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
package org.particleframework.inject.executable

import org.particleframework.context.AbstractExecutableMethod
import org.particleframework.context.ApplicationContext
import org.particleframework.context.DefaultApplicationContext
import org.particleframework.inject.ExecutionHandle
import org.particleframework.inject.ExecutableMethod
import org.particleframework.inject.MethodExecutionHandle
import spock.lang.Specification

class ExecutableSpec extends Specification {

    void "test executable metadata"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test").start()

        when:
        Optional<MethodExecutionHandle> method = applicationContext.findExecutionHandle(BookController, "show", Long)
        ExecutableMethod executableMethod = applicationContext.findBeanDefinition(BookController).get().findMethod("show", Long).get()

        then:
        method.isPresent()

        when:
        MethodExecutionHandle executionHandle = method.get()

        then:
        executionHandle.returnType.type == String
        executionHandle.invoke(1L) == "1 - The Stand"
        executableMethod.getClass().getSuperclass() == AbstractExecutableMethod

        when:
        executionHandle.invoke("bad")

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Invalid type [java.lang.String] for argument [Long id] of method: show'

    }

    void "test executable responses"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test").start()
        ExecutionHandle method = applicationContext.findExecutionHandle(BookController, methodName, argTypes as Class[]).get()

        expect:
        method.invoke(args as Object[]) == result

        where:
        methodName            | argTypes         | args             | result
        "show"                | [Long]           | [1L]             | "1 - The Stand"
        "showArray"           | [Long[].class]   | [[1L] as Long[]] | "1 - The Stand"
        "showPrimitive"       | [long.class]     | [1L as long]     | "1 - The Stand"
        "showPrimitiveArray"  | [long[].class]   | [[1L] as long[]] | "1 - The Stand"
        "showVoidReturn"      | [Iterable.class] | [['test']]       | null
        "showPrimitiveReturn" | [int[].class]    | [[1] as int[]]   | 1
    }
}