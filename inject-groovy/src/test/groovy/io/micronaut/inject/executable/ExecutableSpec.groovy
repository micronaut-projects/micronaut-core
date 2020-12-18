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
package io.micronaut.inject.executable

import io.micronaut.context.AbstractExecutableMethod
import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.inject.ExecutionHandle
import io.micronaut.inject.ExecutableMethod
import io.micronaut.inject.MethodExecutionHandle
import io.micronaut.context.annotation.Executable
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Singleton
/**
 * @author Graeme Rocher
 * @since 1.0
 */
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
        e.message == 'Invalid type [java.lang.String] for argument [Long id] of method: String show(Long id)'

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

@Singleton
class BookService {}

@Executable
class BookController {
    @Inject
    BookService bookService

    @Executable
    String show(Long id) {
        return "$id - The Stand"
    }

    @Executable
    String showArray(Long[] id) {
        return "${id[0]} - The Stand"
    }

    @Executable
    String showPrimitive(long id) {
        return "$id - The Stand"
    }

    @Executable
    String showPrimitiveArray(long[] id) {
        return "${id[0]} - The Stand"
    }

    @Executable
    void showVoidReturn(List<? extends String> jobNames) {
        jobNames.add("test")
    }

    @Executable
    int showPrimitiveReturn(int[] values) {
        return values[0]
    }
}