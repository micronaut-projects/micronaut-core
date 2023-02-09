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
import io.micronaut.context.annotation.BeanProperties
import io.micronaut.core.annotation.Introspected
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.validation.RequiresValidation
import spock.lang.Issue

class ExecutableBeanSpec extends AbstractTypeElementSpec {

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

    @Issue('#2789')
    void "test don't generate executable methods for inherited protected or package private methods"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.MyBean','''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@jakarta.inject.Singleton
@Executable
class MyBean extends Parent {

    public int round(float num) {
        return Math.round(num);
    }
}

class Parent {
    protected void protectedMethod() {
    }

    void packagePrivateMethod() {
    }

    private void privateMethod() {
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

    void "test how annotations are preserved"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.MyBean','''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;
import javax.validation.Valid;
import java.util.List;

@jakarta.inject.Singleton
class MyBean {

    @Executable
    public void saveAll(@Valid List<io.micronaut.inject.executable.Book> books) {
    }

    @Executable
    public <T extends io.micronaut.inject.executable.Book> void saveAll2(@Valid List<? extends T> book) {
    }

    @Executable
    public <T extends io.micronaut.inject.executable.Book> void saveAll3(@Valid List<T> book) {
    }

    @Executable
    public void save2(@Valid io.micronaut.inject.executable.Book book) {
    }

    @Executable
    public <T extends io.micronaut.inject.executable.Book> void save3(@Valid T book) {
    }

    @Executable
    public io.micronaut.inject.executable.Book get() {
        return null;
    }
}

''')
        when:
            def saveAll = definition.findMethod("saveAll", List.class).get()
            def listTypeArgument = saveAll.getArguments()[0].getTypeParameters()[0]
        then:
            !saveAll.hasAnnotation(RequiresValidation)
            !saveAll.hasStereotype(RequiresValidation)
            listTypeArgument.getAnnotationMetadata().hasAnnotation(MyEntity.class)
            listTypeArgument.getAnnotationMetadata().hasAnnotation(Introspected.class)
            listTypeArgument.getAnnotationMetadata().hasStereotype(Introspected.class)
            !listTypeArgument.getAnnotationMetadata().hasAnnotation(BeanProperties.class)
            !listTypeArgument.getAnnotationMetadata().hasStereotype(BeanProperties.class)

        when:
            def saveAll2 = definition.findMethod("saveAll2", List.class).get()
            def listTypeArgument2 = saveAll2.getArguments()[0].getTypeParameters()[0]
        then:
            !saveAll2.hasAnnotation(RequiresValidation)
            !saveAll2.hasStereotype(RequiresValidation)
            listTypeArgument2.getAnnotationMetadata().hasAnnotation(MyEntity.class)
            listTypeArgument2.getAnnotationMetadata().hasAnnotation(Introspected.class)
            listTypeArgument2.getAnnotationMetadata().hasStereotype(Introspected.class)
            !listTypeArgument2.getAnnotationMetadata().hasAnnotation(BeanProperties.class)
            !listTypeArgument2.getAnnotationMetadata().hasStereotype(BeanProperties.class)

        when:
            def saveAll3 = definition.findMethod("saveAll3", List.class).get()
            def listTypeArgument3 = saveAll3.getArguments()[0].getTypeParameters()[0]
        then:
            !saveAll3.hasAnnotation(RequiresValidation)
            !saveAll3.hasStereotype(RequiresValidation)
            listTypeArgument3.getAnnotationMetadata().hasAnnotation(MyEntity.class)
            listTypeArgument3.getAnnotationMetadata().hasAnnotation(Introspected.class)
            listTypeArgument3.getAnnotationMetadata().hasStereotype(Introspected.class)
            !listTypeArgument3.getAnnotationMetadata().hasAnnotation(BeanProperties.class)
            !listTypeArgument3.getAnnotationMetadata().hasStereotype(BeanProperties.class)
// TODO: validate this behaviour
//
//        when:
//            def save2 = definition.findMethod("save2", Book.class).get()
//            def parameter2 = save2.getArguments()[0]
//        then:
//            !save2.hasAnnotation(RequiresValidation)
//            !save2.hasStereotype(RequiresValidation)
//            parameter2.getAnnotationMetadata().hasAnnotation(MyEntity.class)
//            parameter2.getAnnotationMetadata().hasAnnotation(Introspected.class)
//            parameter2.getAnnotationMetadata().hasStereotype(Introspected.class)
//            !parameter2.getAnnotationMetadata().hasAnnotation(BeanProperties.class)
//            !parameter2.getAnnotationMetadata().hasStereotype(BeanProperties.class)
//
//        when:
//            def save3 = definition.findMethod("save3", Book.class).get()
//            def parameter3 = save3.getArguments()[0]
//        then:
//            !save3.hasAnnotation(RequiresValidation)
//            !save3.hasStereotype(RequiresValidation)
//            parameter3.getAnnotationMetadata().hasAnnotation(MyEntity.class)
//            parameter3.getAnnotationMetadata().hasAnnotation(Introspected.class)
//            parameter3.getAnnotationMetadata().hasStereotype(Introspected.class)
//            !parameter3.getAnnotationMetadata().hasAnnotation(BeanProperties.class)
//            !parameter3.getAnnotationMetadata().hasStereotype(BeanProperties.class)
//
//        when:
//            def get = definition.findMethod("get").get()
//            def returnType = get.getReturnType()
//        then:
//            returnType.getAnnotationMetadata().hasAnnotation(MyEntity.class)
//            returnType.getAnnotationMetadata().hasAnnotation(Introspected.class)
//            returnType.getAnnotationMetadata().hasStereotype(Introspected.class)
//            !returnType.getAnnotationMetadata().hasAnnotation(BeanProperties.class)
//            !returnType.getAnnotationMetadata().hasStereotype(BeanProperties.class)
    }

    void "test multiple executable annotations on a method"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.MyBean','''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;
import io.micronaut.inject.executable.*;

@jakarta.inject.Singleton
class MyBean  {

    @RepeatableExecutable("a")
    @RepeatableExecutable("b")
    public void run() {

    }
}
''')
        expect:
        definition != null
        definition.findMethod("run").isPresent()
    }
}

