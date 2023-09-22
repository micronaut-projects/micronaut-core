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
import io.micronaut.context.annotation.BeanProperties
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.type.Argument
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.validation.RequiresValidation
import spock.lang.PendingFeature

class ExecutableBeanSpec extends AbstractBeanDefinitionSpec {

    void "test executable and aop"() {
        given:
        BeanDefinition definition = buildInterceptedBeanDefinition('test.Test$ExecutableController','''\
package test

import io.micronaut.aop.interceptors.Mutating
import io.micronaut.context.annotation.Executable
import io.micronaut.inject.annotation.*
import io.micronaut.inject.executable.StartupExecutable
import io.micronaut.retry.annotation.Retryable
import io.micronaut.scheduling.annotation.Scheduled

class Test {
    @jakarta.inject.Singleton
    static class ExecutableController {
        String foo

        @Scheduled(fixedDelay = "10ms")
        @Retryable
         String round(String foo) {
            return foo
        }
    }
}

''')
        expect:
        definition != null
        definition.executableMethods.size() == 1
        definition.requiresMethodProcessing()
    }

    void "test executable at class level"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.ExecutableController','''\
package test

import io.micronaut.context.annotation.Executable
import io.micronaut.inject.annotation.*

@jakarta.inject.Singleton
@Executable
class ExecutableController {
    String foo

    @Executable
     int round(float num) {
        return Math.round(num)
    }

    @Executable
     int sum(int a, int b) {
        return doSum()
    }

    private int doSum() {
        return a + b
    }
}
''')
        expect:
        definition != null
        definition.executableMethods.size() == 2
        definition.executableMethods*.name.toSorted() == ['round', 'sum'].toSorted()
    }

    void "test executable at class level 2"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.ExecutableController','''\
package test

import io.micronaut.context.annotation.Executable
import io.micronaut.inject.annotation.*

@jakarta.inject.Singleton
@Executable
class ExecutableController {
    String foo

    int round(float num) {
        return Math.round(num)
    }

    int sum(int a, int b) {
        return doSum()
    }

    private int doSum() {
        return a + b
    }
}
''')
        expect:
        definition != null
        definition.executableMethods.size() == 2
        definition.executableMethods*.name.toSorted() == ['round', 'sum'].toSorted()
    }

    void "test executable on stereotype"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.ExecutableController','''\
package test

import io.micronaut.inject.annotation.*
import io.micronaut.context.annotation.*
import io.micronaut.http.annotation.Get

@jakarta.inject.Singleton
class ExecutableController {

    @Get("/round")
     int round(float num) {
        return Math.round(num)
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
package test

import io.micronaut.inject.annotation.*
import io.micronaut.context.annotation.*

@jakarta.inject.Singleton
@Executable
class ExecutableBean1 {

    int round(float num) {
        return Math.round(num)
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
package test

import io.micronaut.inject.annotation.*
import io.micronaut.context.annotation.*

class MyBean {

    @Executable
     int round(float num) {
        return Math.round(num)
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

    void "test how annotations are preserved"() {
        given:
            BeanDefinition definition = buildBeanDefinition('test.MyBean','''\
package test

import io.micronaut.inject.annotation.*
import io.micronaut.context.annotation.*
import jakarta.validation.Valid
import java.util.List
import io.micronaut.inject.executable.Book

@jakarta.inject.Singleton
class MyBean {

    @Executable
     void saveAll(@Valid List<Book> books) {
    }

    @Executable
     <T extends Book> void saveAll2(@Valid List<? extends T> book) {
    }

    @Executable
     <T extends Book> void saveAll3(@Valid List<T> book) {
    }

    @Executable
     void save2(@Valid Book book) {
    }

    @Executable
     <T extends Book> void save3(@Valid T book) {
    }

    @Executable
     Book get() {
        return null
    }
}

''')
        when:
            def saveAll = definition.findMethod("saveAll", List.class).get()
            def listTypeArgument = saveAll.getArguments()[0].getTypeParameters()[0]
        then:
            !saveAll.hasAnnotation(RequiresValidation)
            !saveAll.hasStereotype(RequiresValidation)
            !listTypeArgument.getAnnotationMetadata().hasAnnotation(MyEntity.class)
            !listTypeArgument.getAnnotationMetadata().hasAnnotation(Introspected.class)
            !listTypeArgument.getAnnotationMetadata().hasStereotype(Introspected.class)
            !listTypeArgument.getAnnotationMetadata().hasAnnotation(BeanProperties.class)
            !listTypeArgument.getAnnotationMetadata().hasStereotype(BeanProperties.class)

        when:
            def saveAll2 = definition.findMethod("saveAll2", List.class).get()
            def listTypeArgument2 = saveAll2.getArguments()[0].getTypeParameters()[0]
        then:
            !saveAll2.hasAnnotation(RequiresValidation)
            !saveAll2.hasStereotype(RequiresValidation)
            !listTypeArgument2.getAnnotationMetadata().hasAnnotation(MyEntity.class)
            !listTypeArgument2.getAnnotationMetadata().hasAnnotation(Introspected.class)
            !listTypeArgument2.getAnnotationMetadata().hasStereotype(Introspected.class)
            !listTypeArgument2.getAnnotationMetadata().hasAnnotation(BeanProperties.class)
            !listTypeArgument2.getAnnotationMetadata().hasStereotype(BeanProperties.class)

        when:
            def saveAll3 = definition.findMethod("saveAll3", List.class).get()
            def listTypeArgument3 = saveAll3.getArguments()[0].getTypeParameters()[0]
        then:
            !saveAll3.hasAnnotation(RequiresValidation)
            !saveAll3.hasStereotype(RequiresValidation)
            !listTypeArgument3.getAnnotationMetadata().hasAnnotation(MyEntity.class)
            !listTypeArgument3.getAnnotationMetadata().hasAnnotation(Introspected.class)
            !listTypeArgument3.getAnnotationMetadata().hasStereotype(Introspected.class)
            !listTypeArgument3.getAnnotationMetadata().hasAnnotation(BeanProperties.class)
            !listTypeArgument3.getAnnotationMetadata().hasStereotype(BeanProperties.class)

        when:
            def save2 = definition.findMethod("save2", Book.class).get()
            def parameter2 = save2.getArguments()[0]
        then:
            !save2.hasAnnotation(RequiresValidation)
            !save2.hasStereotype(RequiresValidation)
            !parameter2.getAnnotationMetadata().hasAnnotation(MyEntity.class)
            !parameter2.getAnnotationMetadata().hasAnnotation(Introspected.class)
            !parameter2.getAnnotationMetadata().hasStereotype(Introspected.class)
            !parameter2.getAnnotationMetadata().hasAnnotation(BeanProperties.class)
            !parameter2.getAnnotationMetadata().hasStereotype(BeanProperties.class)

        when:
            def save3 = definition.findMethod("save3", Book.class).get()
            def parameter3 = save3.getArguments()[0]
        then:
            !save3.hasAnnotation(RequiresValidation)
            !save3.hasStereotype(RequiresValidation)
            !parameter3.getAnnotationMetadata().hasAnnotation(MyEntity.class)
            !parameter3.getAnnotationMetadata().hasAnnotation(Introspected.class)
            !parameter3.getAnnotationMetadata().hasStereotype(Introspected.class)
            !parameter3.getAnnotationMetadata().hasAnnotation(BeanProperties.class)
            !parameter3.getAnnotationMetadata().hasStereotype(BeanProperties.class)

        when:
            def get = definition.findMethod("get").get()
            def returnType = get.getReturnType()
        then:
            !returnType.getAnnotationMetadata().hasAnnotation(MyEntity.class)
            !returnType.getAnnotationMetadata().hasAnnotation(Introspected.class)
            !returnType.getAnnotationMetadata().hasStereotype(Introspected.class)
            !returnType.getAnnotationMetadata().hasAnnotation(BeanProperties.class)
            !returnType.getAnnotationMetadata().hasStereotype(BeanProperties.class)
    }

    void "test how the type annotations from the type are preserved 2"() {
        given:
            BeanDefinition bd = buildBeanDefinition('test.MyBean', '''\
package test

import io.micronaut.inject.annotation.*
import io.micronaut.context.annotation.*
import java.util.List
import io.micronaut.inject.executable.Book
import io.micronaut.inject.executable.TypeUseRuntimeAnn

@jakarta.inject.Singleton
class MyBean {

    @Executable
     void saveAll(List<@TypeUseRuntimeAnn Book> books) {
    }

    @Executable
     <@TypeUseRuntimeAnn T extends Book> void saveAll2(List<? extends T> book) {
    }

    @Executable
     <@TypeUseRuntimeAnn T extends Book> void saveAll3(List<T> book) {
    }

    @Executable
     <T extends Book> void saveAll4(List<@TypeUseRuntimeAnn ? extends T> book) {
    }

    @Executable
     <T extends Book> void saveAll5(List<? extends @TypeUseRuntimeAnn T> book) {
    }

    @Executable
     void save2(@TypeUseRuntimeAnn Book book) {
    }

    @Executable
     <@TypeUseRuntimeAnn T extends Book> void save3(T book) {
    }

    @Executable
     <T extends @TypeUseRuntimeAnn Book> void save4(T book) {
    }

    @Executable
     <T extends Book> void save5(@TypeUseRuntimeAnn T book) {
    }

    @TypeUseRuntimeAnn
    @Executable
     Book get() {
        return null
    }
}

''')
        when:
            def saveAll = bd.findMethod("saveAll", List).get()
            def listTypeArgument = saveAll.getArguments()[0].getTypeParameters()[0]
        then:
            validateBookArgument(listTypeArgument)

        when:
            def saveAll2 = bd.findMethod("saveAll2", List).get()
            def listTypeArgument2 = saveAll2.getArguments()[0].getTypeParameters()[0]
        then:
            validateBookArgument(listTypeArgument2)

//        when:
//            def saveAll3 = bd.findMethod("saveAll3", List).get()
//            def listTypeArgument3 = saveAll3.getArguments()[0].getTypeParameters()[0]
//        then:
//            validateBookArgument(listTypeArgument3)

        when:
            def saveAll4 = bd.findMethod("saveAll4", List).get()
            def listTypeArgument4 = saveAll4.getArguments()[0].getTypeParameters()[0]
        then:
            validateBookArgument(listTypeArgument4)

//        when:
//            def saveAll5 = bd.findMethod("saveAll5", List).get()
//            def listTypeArgument5 = saveAll5.getArguments()[0].getTypeParameters()[0]
//        then:
//            validateBookArgument(listTypeArgument5)

        when:
            def save2 = bd.findMethod("save2", Book).get()
            def parameter2 = save2.getArguments()[0]
        then:
            validateBookArgument(parameter2)

        when:
            def save3 = bd.findMethod("save3", Book).get()
            def parameter3 = save3.getArguments()[0]
        then:
            validateBookArgument(parameter3)

        when:
            def save4 = bd.findMethod("save4", Book).get()
            def parameter4 = save4.getArguments()[0]
        then:
            validateBookArgument(parameter4)

//        when:
//            def save5 = bd.findMethod("save5", Book).get()
//            def parameter5 = save5.getArguments()[0]
//        then:
//            validateBookArgument(parameter5)

//        when:
//            def get = bd.findMethod("get").get()
//            def returnType = get.getReturnType().asArgument()
//        then:
//            validateBookArgument(returnType)
    }

    @PendingFeature
    void "test how the type annotations from the type are preserved - not working case"() {
        given:
            BeanDefinition bd = buildBeanDefinition('test.MyBean', '''\
package test

import io.micronaut.inject.annotation.*
import io.micronaut.context.annotation.*
import java.util.List
import io.micronaut.inject.executable.Book
import io.micronaut.inject.executable.TypeUseRuntimeAnn

@jakarta.inject.Singleton
class MyBean {

    @Executable
     <@TypeUseRuntimeAnn T extends Book> void saveAll3(List<T> book) {
    }
}

''')
        when:
            def saveAll3 = bd.findMethod("saveAll3", List).get()
            def listTypeArgument3 = saveAll3.getArguments()[0].getTypeParameters()[0]
        then:
            validateBookArgument(listTypeArgument3)
    }

    @PendingFeature // Groovy doesn't add the annotation to the return type
    void "test how the type annotations are preserved on a method"() {
        given:
            BeanDefinition bd = buildBeanDefinition('test.MyBean', '''\
package test

import io.micronaut.inject.annotation.*
import io.micronaut.context.annotation.*
import java.util.List
import io.micronaut.inject.executable.Book
import io.micronaut.inject.executable.TypeUseRuntimeAnn

@jakarta.inject.Singleton
class MyBean {

    @TypeUseRuntimeAnn
    @Executable
     Book get() {
        return null
    }
}

''')

        when:
            def get = bd.findMethod("get").get()
            def returnType = get.getReturnType().asArgument()
        then:
            validateBookArgument(returnType)
    }

    @PendingFeature // The actual placeholder with annotations is replaced by typeArguments one
    void "test how the type annotations from the type are preserved 3"() {
        given:
            BeanDefinition bd = buildBeanDefinition('test.MyBean', '''\
package test

import io.micronaut.inject.annotation.*
import io.micronaut.context.annotation.*
import java.util.List
import io.micronaut.inject.executable.Book
import io.micronaut.inject.executable.TypeUseRuntimeAnn

@jakarta.inject.Singleton
class MyBean {

    @Executable
     <T extends Book> void saveAll5(List<? extends @TypeUseRuntimeAnn T> book) {
    }

    @Executable
     <T extends Book> void save5(@TypeUseRuntimeAnn T book) {
    }

}

''')
        when:
            def saveAll5 = bd.findMethod("saveAll5", List).get()
            def listTypeArgument5 = saveAll5.getArguments()[0].getTypeParameters()[0]
        then:
            validateBookArgument(listTypeArgument5)

        when:
            def save5 = bd.findMethod("save5", Book).get()
            def parameter5 = save5.getArguments()[0]
        then:
            validateBookArgument(parameter5)
    }

    void validateBookArgument(Argument argument) {
        // The argument should only have type annotations
        def am = argument.getAnnotationMetadata()
        assert am.hasAnnotation(TypeUseRuntimeAnn.class)
        assert !am.hasAnnotation(MyEntity.class)
        assert !am.hasAnnotation(Introspected.class)
        assert am.getAnnotationNames().size() == 1
    }
}
