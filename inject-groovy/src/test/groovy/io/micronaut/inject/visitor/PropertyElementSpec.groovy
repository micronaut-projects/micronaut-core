package io.micronaut.inject.visitor

import io.micronaut.AbstractBeanDefinitionSpec
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
        buildBeanDefinition('test.TestController', '''
package test;

import io.micronaut.http.annotation.*;
import javax.inject.Inject;

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
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[1].name == 'age'
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[1].isAnnotationPresent(Get)
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[1].type.name == 'int'
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[1].isReadOnly()
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[2].name == 'name'
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[2].type.name == 'java.lang.String'
        !AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[2].isReadOnly()
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].beanProperties[0].name == 'groovyProp'
    }

    void "test simple bean properties with generics"() {
        buildBeanDefinition('test.TestController', '''
package test;

import io.micronaut.http.annotation.*;
import javax.inject.Inject;

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
import javax.inject.Inject;

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
