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

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod
import io.micronaut.inject.ExecutionHandle
import io.micronaut.inject.MethodExecutionHandle

class ExecutableSpec extends AbstractTypeElementSpec {

    void "test executable compile spec"() {
        given:"A bean that defines no explicit scope"
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.context.annotation.*;

@Executable
class MyBean {
    public String methodOne(@jakarta.inject.Named("foo") String one) {
        return "good";
    }

    public String methodTwo(String one, String two) {
        return "good";
    }

    public String methodZero() {
        return "good";
    }
}


''')
        then:"the default scope is singleton"
        beanDefinition.executableMethods.size() == 3
        beanDefinition.executableMethods[0].methodName == 'methodOne'
        beanDefinition.executableMethods[0].getArguments()[0].getAnnotationMetadata().stringValue(AnnotationUtil.NAMED).get() == 'foo'
    }

    void "test static method"() {
        given:"A bean that defines no explicit scope"
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.context.annotation.*;

@Executable
class MyBean {

    @Executable
    public static String methodOne(@jakarta.inject.Named("foo") String one) {
        return "good" + one;
    }

    @Executable
    public static String methodTwo(String one, String two) {
        return "good" + one + two;
    }

    @Executable
    public static String methodZero() {
        return "good";
    }
}

''')
        then:
        beanDefinition.executableMethods.size() == 3
        beanDefinition.executableMethods[0].methodName == 'methodOne'
        beanDefinition.executableMethods[0].getArguments()[0].getAnnotationMetadata().stringValue(AnnotationUtil.NAMED).get() == 'foo'
        beanDefinition.executableMethods[0].invoke(null, "abc") == "goodabc"
        beanDefinition.executableMethods[1].methodName == 'methodTwo'
        beanDefinition.executableMethods[1].getArguments()[0].name == 'one'
        beanDefinition.executableMethods[1].getArguments()[1].name == 'two'
        beanDefinition.executableMethods[1].invoke(null, "abc", "xyz") == "goodabcxyz"
        beanDefinition.executableMethods[2].methodName == 'methodZero'
        beanDefinition.executableMethods[2].getArguments().length == 0
        beanDefinition.executableMethods[2].invoke(null) == "good"
    }

    void "test static method protected/private require explicit @Executable"() {
        given:"A bean that defines no explicit scope"
        when:
            BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.context.annotation.*;
import io.micronaut.core.annotation.ReflectiveAccess;

@Executable
class MyBean {

    private static String methodOne(@jakarta.inject.Named("foo") String one) {
        return "good" + one;
    }

    protected static String methodTwo(String one, String two) {
        return "good" + one + two;
    }

    static String methodZero() {
        return "good";
    }

    public static String methodThree() {
        return "good";
    }
}

''')
        then:
            beanDefinition.executableMethods.size() == 0
    }

    void "test static method protected/private"() {
        given:"A bean that defines no explicit scope"
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.context.annotation.*;
import io.micronaut.core.annotation.ReflectiveAccess;

@Executable
class MyBean {

    @ReflectiveAccess
    @Executable
    private static String methodOne(@jakarta.inject.Named("foo") String one) {
        return "good" + one;
    }

    @ReflectiveAccess
    @Executable
    protected static String methodTwo(String one, String two) {
        return "good" + one + two;
    }

    @ReflectiveAccess
    @Executable
    static String methodZero() {
        return "good";

    }
}

''')
        then:
        beanDefinition.executableMethods.size() == 3
        beanDefinition.executableMethods[0].methodName == 'methodOne'
        beanDefinition.executableMethods[0].getArguments()[0].getAnnotationMetadata().stringValue(AnnotationUtil.NAMED).get() == 'foo'
        beanDefinition.executableMethods[0].invoke(null, "abc") == "goodabc"
        beanDefinition.executableMethods[1].methodName == 'methodTwo'
        beanDefinition.executableMethods[1].getArguments()[0].name == 'one'
        beanDefinition.executableMethods[1].getArguments()[1].name == 'two'
        beanDefinition.executableMethods[1].invoke(null, "abc", "xyz") == "goodabcxyz"
        beanDefinition.executableMethods[2].methodName == 'methodZero'
        beanDefinition.executableMethods[2].getArguments().length == 0
        beanDefinition.executableMethods[2].invoke(null) == "good"
    }

    void "test executable metadata"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.builder("test").build().start()

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

        when:
        executionHandle.invoke("bad")

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Invalid type [java.lang.String] for argument [Long id] of method: String show(Long id)'

    }

    void "test executable responses"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.builder("test").build().start()

        expect:
        applicationContext.findExecutionHandle(BookController, methodName, argTypes as Class[]).isPresent()
        ExecutionHandle method = applicationContext.findExecutionHandle(BookController, methodName, argTypes as Class[]).get()
        method.invoke(args as Object[]) == result

        where:
        methodName                            | argTypes         | args             | result
        "show"                                | [Long]           | [1L]             | "1 - The Stand"
        "showArray"                           | [Long[].class]   | [[1L] as Long[]] | "1 - The Stand"
        "showPrimitive"                       | [long.class]     | [1L as long]     | "1 - The Stand"
        "showPrimitiveArray"                  | [long[].class]   | [[1L] as long[]] | "1 - The Stand"
        "showVoidReturn"                      | [Iterable.class] | [['test']]       | null
        "showPrimitiveReturn"                 | [int[].class]    | [[1] as int[]]   | 1
        "showStatic"                          | [Long]           | [1L]             | "1 - The Stand"
        "showArrayStatic"                     | [Long[].class]   | [[1L] as Long[]] | "1 - The Stand"
        "showPrimitiveStatic"                 | [long.class]     | [1L as long]     | "1 - The Stand"
        "showPrimitiveArrayStatic"            | [long[].class]   | [[1L] as long[]] | "1 - The Stand"
        "showVoidReturnStatic"                | [Iterable.class] | [['test']]       | null
        "showPrimitiveReturnStatic"           | [int[].class]    | [[1] as int[]]   | 1
        "showProtectedReflectiveAccess"       | [Long]           | [1L]             | "1 - The Stand"
        "showPrivateReflectiveAccess"         | [Long]           | [1L]             | "1 - The Stand"
        "showProtectedReflectiveAccessStatic" | [Long]           | [1L]             | "1 - The Stand"
        "showPrivateReflectiveAccessStatic"   | [Long]           | [1L]             | "1 - The Stand"
        "showProtected"                       | [Long]           | [1L]             | "1 - The Stand"
        "showPackageProtected"                | [Long]           | [1L]             | "1 - The Stand"
        "showPackageProtectedStatic"          | [Long]           | [1L]             | "1 - The Stand"
        "showProtectedStatic"                 | [Long]           | [1L]             | "1 - The Stand"
    }

    void "test executable missing methods"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.builder("test").build().start()

        expect:
        !applicationContext.findExecutionHandle(BookController, methodName, argTypes as Class[]).isPresent()

        where:
        methodName                   | argTypes | args | result
        "showPrivate"                | [Long]   | [1L] | "1 - The Stand"
        "showPrivateStatic"          | [Long]   | [1L] | "1 - The Stand"
    }
}
