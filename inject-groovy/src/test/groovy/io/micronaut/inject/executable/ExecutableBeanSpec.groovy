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

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.inject.BeanDefinition

class ExecutableBeanSpec extends AbstractBeanDefinitionSpec {

    void "test executable on stereotype"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.ExecutableController','''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;
import io.micronaut.http.annotation.Get;

@jakarta.inject.Singleton
class ExecutableController {

    @Get("/round")
    public int round(float num) {
        return Math.round(num);
    }
}
''')
        expect:
        definition != null
        definition.findMethod("round", float.class).get().returnType.type == int.class
    }

    void "test executable method return types"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.ExecutableBean1','''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@jakarta.inject.Singleton
@Executable
class ExecutableBean1 {

    public int round(float num) {
        return Math.round(num);
    }
}
''')
        expect:
        definition != null
        definition.findMethod("round", float.class).get().returnType.type == int.class

    }

    void "bean definition should not be created for class with only executable methods"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.MyBean','''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

class MyBean {

    @Executable
    public int round(float num) {
        return Math.round(num);
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

import io.micronaut.inject.annotation.*
import io.micronaut.context.annotation.*
import io.micronaut.inject.executable.*

@jakarta.inject.Singleton
class MyBean  {

    @RepeatableExecutables([
        @RepeatableExecutable("a"),
        @RepeatableExecutable("b")
    ])
    void run() {
        
    }
    
       
    @RepeatableExecutable("a")
    @RepeatableExecutable("b")
    void run2() {
        
    }
}
''')
        expect:
        definition != null
        definition.findMethod("run2").isPresent()
        definition.findMethod("run").isPresent()
    }
}
