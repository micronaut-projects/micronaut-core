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
package io.micronaut.visitors

import io.micronaut.http.annotation.Get
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.ast.ClassElement
import spock.lang.IgnoreIf
import spock.util.environment.Jvm

import javax.annotation.Nullable
import javax.validation.constraints.NotBlank

class PropertyElementSpec extends AbstractTypeElementSpec {
    @IgnoreIf({ !jvm.isJava14Compatible() })
    void 'test bean properties work for records'() {
        given:
        ClassElement classElement = buildClassElement('''
package test;

record Book( @javax.validation.constraints.NotBlank String title, int pages) {}
''')
        def beanProperties = classElement.getBeanProperties()
        def titleProp = beanProperties.find { it.name == 'title' }
        expect:
        classElement.isRecord()
        beanProperties.size() == 2
        titleProp != null
        titleProp.type.name == String.name
        titleProp.hasAnnotation(NotBlank)
        beanProperties.every { it.readOnly }
    }

    // Java 9+ doesn't allow resolving elements was the compiler
    // is finished being used so this test cannot be made to work beyond Java 8 the way it is currently written
    @IgnoreIf({ Jvm.current.isJava9Compatible() })
    void "test simple bean properties"() {
        buildBeanDefinition('test.TestController', '''
package test;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

@Controller("/test")
public class TestController {

    private int age;
    @javax.annotation.Nullable
    private String name;
    @javax.annotation.Nullable
    private String description;

    /**
     * The age
     */
    @Get("/getMethod")
    public int getAge() {
        return age;
    }

    /**
     * The age
     */
    @Get("/getMethod/{age}")
    public int getAge( @javax.validation.constraints.NotBlank int age) {
        return age;
    }

    public String getName() {
        return name;
    }

    @javax.validation.constraints.NotBlank
    public void setName(@javax.validation.constraints.NotBlank String n) {
        name = n;
    }

    /**
     * The Description
     */
    public String getDescription() {
        return description;
    }

    public void setDescription(@javax.validation.constraints.NotBlank  String description) {
        this.description = description;
    }
}
''')
        expect:
        AllElementsVisitor.VISITED_CLASS_ELEMENTS.size() == 1
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties.size() == 3
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties.size() == 3
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[0].name == 'age'
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[0].isAnnotationPresent(Get)
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[0].type.name == 'int'
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[0].isReadOnly()
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[1].name == 'name'
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[1].isAnnotationPresent(Nullable)
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[1].type.name == 'java.lang.String'
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[2].name == 'description'
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[2].isAnnotationPresent(Nullable)
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[2].type.name == 'java.lang.String'
        AllElementsVisitor
        !AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[1].isReadOnly()
    }

    void "test simple bean properties with generics"() {
        buildBeanDefinition('test.TestController', '''
package test;

import io.micronaut.http.annotation.*;
import jakarta.inject.Inject;

@Controller("/test")
public class TestController<T extends CharSequence> {

    private int age;
    private T name;

    public int getAge() {
        return age;
    }

    public T getName() {
        return name;
    }

    public void setName(T n) {
        name = n;
    }
}
''')
        expect:
        AllElementsVisitor.VISITED_CLASS_ELEMENTS.size() == 1
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties.size() == 2
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[0].name == 'age'
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[0].type.name == 'int'
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[0].isReadOnly()
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[1].name == 'name'
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[1].type.name == 'java.lang.CharSequence'
        !AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[1].isReadOnly()
    }


    void "test simple bean properties with generics on property"() {
        buildBeanDefinition('test.TestController', '''
package test;

import io.micronaut.http.annotation.*;
import jakarta.inject.Inject;

@Controller("/test")
public class TestController {

    private Response<Integer> age;

    public Response<Integer> getAge() {
        return age;
    }

    @Put("/")
    public Response<Integer> update() {
        return null;
    }
}

class Response<T> {
    T r;
    public T getResult() { return r; }
}
''')
        expect:
        AllElementsVisitor.VISITED_CLASS_ELEMENTS.size() == 1
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties.size() == 1
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[0].name == 'age'
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[0].type.name == 'test.Response'
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[0].isReadOnly()
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[0].type.typeArguments.size() == 1
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[0].type.typeArguments.values().first().name == 'java.lang.Integer'
        AllElementsVisitor.VISITED_METHOD_ELEMENTS.size() == 2
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[1].name == 'update'
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[1].returnType.name == 'test.Response'
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[1].returnType.typeArguments.size() == 1
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[1].returnType.typeArguments.values().first().name == 'java.lang.Integer'
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[1].returnType.beanProperties.size() == 1
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[1].returnType.beanProperties[0].type.name == 'java.lang.Integer'
    }
}
