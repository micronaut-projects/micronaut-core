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
package io.micronaut.inject.visitor

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.ast.groovy.TypeElementVisitorStart
import io.micronaut.http.annotation.Get

class PropertyElementSpec extends AbstractBeanDefinitionSpec {

    def setup() {
        System.setProperty(TypeElementVisitorStart.ELEMENT_VISITORS_PROPERTY, AllElementsVisitor.name)
    }

    def cleanup() {
        AllElementsVisitor.clearVisited()
    }

    void "test simple bean properties"() {
        buildBeanDefinition('propelem1.TestController', '''
package propelem1;

import io.micronaut.http.annotation.*;
import jakarta.inject.Inject;

@Controller("/test")
public class TestController {

    private int age;
    private String name;
    String groovyProp


    /**
     * The age
     */
    @Get("/getMethod")
    public int getAge() {
        return age;
    }

    public String getName() {
        return name;
    }

    public void setName(String n) {
        name = n;
    }
}
''')
        expect:
        AllElementsVisitor.VISITED_CLASS_ELEMENTS.size() == 1
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties.size() == 3
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[0].name == 'age'
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[0].isAnnotationPresent(Get)
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[0].type.name == 'int'
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[0].isReadOnly()
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[1].name == 'name'
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[1].type.name == 'java.lang.String'
        !AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[1].isReadOnly()
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[2].name == 'groovyProp'
    }

    void "test simple bean properties with generics"() {
        buildBeanDefinition('propelem2.TestController', '''
package propelem2;

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
        buildBeanDefinition('propelem3.TestController', '''
package propelem3;

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
    private T r;
    public T getResult() { return r; }
}
''')
        expect:
        AllElementsVisitor.VISITED_CLASS_ELEMENTS.size() == 1
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties.size() == 1
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[0].name == 'age'
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[0].type.name == 'propelem3.Response'
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[0].isReadOnly()
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[0].type.typeArguments.size() == 1
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[0].type.typeArguments.values().first().name == 'java.lang.Integer'
        AllElementsVisitor.VISITED_METHOD_ELEMENTS.size() == 2
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[1].name == 'update'
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[1].returnType.name == 'propelem3.Response'
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[1].returnType.typeArguments.size() == 1
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[1].returnType.typeArguments.values().first().name == 'java.lang.Integer'
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[1].returnType.beanProperties.size() == 1
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[1].returnType.beanProperties[0].type.name == 'java.lang.Integer'
    }

    void "test get annotations from type after bean properties "() {
        buildBeanDefinition('test.TestController', '''
package test;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;

@Controller
class TestController {

    @Post("/path")
    public void processSync(@Body MyDto dto) {
    }
}

class MyDto {

    private Parameters parameters;

    public Parameters getParameters() {
        return parameters;
    }

    public void setParameters(Parameters parameters) {
        this.parameters = parameters;
    }
}

@Introspected
class Parameters {

    private Integer stampWidth;
    private Integer stampHeight;
    private int pageNumber;

    public Integer getStampWidth() {
        return stampWidth;
    }

    public void setStampWidth(Integer stampWidth) {
        this.stampWidth = stampWidth;
    }

    public Integer getStampHeight() {
        return stampHeight;
    }

    public void setStampHeight(Integer stampHeight) {
        this.stampHeight = stampHeight;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
    }
}
''')
        expect:
            AllElementsVisitor.VISITED_CLASS_ELEMENTS.size() == 1

            def method = AllElementsVisitor.VISITED_METHOD_ELEMENTS[0]
            def parameter = method.parameters[0]

            def beanProperty = parameter.type.beanProperties.get(0)
            beanProperty.type.annotationNames.sort() == [
                    'io.micronaut.context.annotation.BeanProperties',
                    'io.micronaut.core.annotation.Introspected'
            ]
            beanProperty.field.get().type.annotationNames.sort() == [
                    'io.micronaut.context.annotation.BeanProperties',
                    'io.micronaut.core.annotation.Introspected'
            ]
            beanProperty.readMethod.get().returnType.annotationNames.sort() == [
                    'io.micronaut.context.annotation.BeanProperties',
                    'io.micronaut.core.annotation.Introspected'
            ]
            beanProperty.writeMethod.get().parameters[0].type.annotationNames.sort() == [
                    'io.micronaut.context.annotation.BeanProperties',
                    'io.micronaut.core.annotation.Introspected'
            ]
    }

    void "test get annotations from type after bean properties for field access"() {
        buildBeanDefinition('test.TestController', '''
package test;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;

@Controller
class TestController {

    @Post("/path")
    public void processSync(@Body MyDto dto) {
    }
}

@Introspected(accessKind = Introspected.AccessKind.FIELD)
class MyDto {

    public Parameters parameters;
}

@Introspected
class Parameters {

    private Integer stampWidth;
    private Integer stampHeight;
    private int pageNumber;

    public Integer getStampWidth() {
        return stampWidth;
    }

    public void setStampWidth(Integer stampWidth) {
        this.stampWidth = stampWidth;
    }

    public Integer getStampHeight() {
        return stampHeight;
    }

    public void setStampHeight(Integer stampHeight) {
        this.stampHeight = stampHeight;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
    }
}
''')
        expect:
            AllElementsVisitor.VISITED_CLASS_ELEMENTS.size() == 1

            def method = AllElementsVisitor.VISITED_METHOD_ELEMENTS[0]
            def parameter = method.parameters[0]

            def beanProperty = parameter.type.beanProperties.get(0)
            beanProperty.type.annotationNames.sort() == [
                    'io.micronaut.context.annotation.BeanProperties',
                    'io.micronaut.core.annotation.Introspected'
            ]
            beanProperty.field.get().type.annotationNames.sort() == [
                    'io.micronaut.context.annotation.BeanProperties',
                    'io.micronaut.core.annotation.Introspected'
            ]
            beanProperty.field.get().genericType.annotationNames.sort() == [
                    'io.micronaut.context.annotation.BeanProperties',
                    'io.micronaut.core.annotation.Introspected'
            ]
    }
}
