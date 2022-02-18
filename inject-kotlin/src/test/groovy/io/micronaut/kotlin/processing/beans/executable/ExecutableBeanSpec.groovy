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
package io.micronaut.kotlin.processing.beans.executable

import io.micronaut.inject.BeanDefinition
import spock.lang.Issue
import spock.lang.Specification

import static io.micronaut.kotlin.processing.KotlinCompiler.*

class ExecutableBeanSpec extends Specification {

    void "test executable method return types"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.ExecutableBean1','''\
package test

import io.micronaut.context.annotation.Executable
import kotlin.math.roundToInt

@jakarta.inject.Singleton
@Executable
class ExecutableBean1 {

    fun round(num: Float): Int {
        return num.roundToInt()
    }
}
''')
        expect:
        definition != null
        definition.findMethod("round", float.class).get().returnType.type == int.class
    }

    @Issue('#2789')
    void "test don't generate executable methods for inherited protected or package private methods"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.MyBean','''\
package test

import io.micronaut.context.annotation.Executable
import kotlin.math.roundToInt

@jakarta.inject.Singleton
@Executable
class MyBean: Parent() {

    fun round(num: Float): Int {
        return num.roundToInt()
    }
}

open class Parent {
    protected fun protectedMethod() {
    }
    
    internal fun packagePrivateMethod() {
    }
    
    private fun privateMethod() {
    }
}
''')
        expect:
        definition != null
        !definition.findMethod("privateMethod").isPresent()
        !definition.findMethod("packagePrivateMethod").isPresent()
        !definition.findMethod("protectedMethod").isPresent()
    }

    void "bean definition should not be created for class with only executable methods"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.MyBean','''\
package test

import io.micronaut.context.annotation.Executable
import kotlin.math.roundToInt

class MyBean {

    @Executable
    fun round(num: Float): Int {
        return num.roundToInt()
    }
}

''')

        expect:
        definition == null
    }

    void "test multiple executable annotations on a method"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.MyBean','''\
package test

import io.micronaut.kotlin.processing.beans.executable.RepeatableExecutable

@jakarta.inject.Singleton
class MyBean  {

    @RepeatableExecutable("a")
    @RepeatableExecutable("b")
    fun run() {
        
    }
}
''')
        expect:
        definition != null
        definition.findMethod("run").isPresent()
    }
}

