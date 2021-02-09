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
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementModifier
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.EnumElement
import io.micronaut.inject.ast.MethodElement
import spock.util.environment.RestoreSystemProperties

import java.util.function.Supplier

@RestoreSystemProperties
class ClassElementSpec extends AbstractBeanDefinitionSpec {

    def setup() {
        System.setProperty(TypeElementVisitorStart.ELEMENT_VISITORS_PROPERTY, AllElementsVisitor.name)
    }

    def cleanup() {
        AllElementsVisitor.clearVisited()
    }

    void 'test find matching methods on abstract class'() {
        given:
        ClassElement classElement = buildClassElement('''
package elementquery;

abstract class Test extends SuperType implements AnotherInterface, SomeInt {

    protected boolean t1;
    private boolean t2;
    
    private boolean privateMethod() {
        return true;
    }
    
    boolean packagePrivateMethod() {
        return true;
    }
    
    @java.lang.Override
    public boolean publicMethod() {
        return true;
    }
    
    static boolean staticMethod() {
        return true;
    }
    
    abstract boolean unimplementedMethod();
}

abstract class SuperType {
    protected boolean s1;
    private boolean s2;
    private boolean privateMethod() {
        return true;
    }
    
    public boolean publicMethod() {
        return true;
    }
    
    public boolean otherSuper() {
        return true;
    }
    
    abstract boolean unimplementedSuperMethod();
}

interface SomeInt {
    default boolean itfeMethod() {
        return true;
    }
    
    boolean publicMethod();
}

interface AnotherInterface {
    boolean publicMethod();
    
    boolean unimplementedItfeMethod();
}
''')
        when:"all methods are retrieved"
        def allMethods = classElement.getEnclosedElements(ElementQuery.ALL_METHODS)

        then:"All methods, including non-accessible are returned but not overridden"
        // slightly different result to java since groovy hides private methods
        allMethods.size() == 9

        when:"only abstract methods are requested"
        def abstractMethods = classElement.getEnclosedElements(ElementQuery.ALL_METHODS.onlyAbstract())

        then:"The result is correct"
        abstractMethods*.name as Set == ['unimplementedItfeMethod', 'unimplementedSuperMethod', 'unimplementedMethod'] as Set

        when:"only concrete methods are requested"
        def concrete = classElement.getEnclosedElements(ElementQuery.ALL_METHODS.onlyConcrete().onlyAccessible())

        then:"The result is correct"
        concrete*.name as Set == ['packagePrivateMethod', 'publicMethod', 'staticMethod', 'otherSuper', 'itfeMethod'] as Set
    }

    void "test find matching methods using ElementQuery"() {
        given:
        ClassElement classElement = buildClassElement('''
package elementquery;

class Test extends SuperType implements AnotherInterface, SomeInt {

    protected boolean t1;
    private boolean t2;
    
    private boolean privateMethod() {
        return true;
    }
    
    boolean packagePrivateMethod() {
        return true;
    }
    
    public boolean publicMethod() {
        return true;
    }
    
    static boolean staticMethod() {
        return true;
    }
}

class SuperType {
    protected boolean s1;
    private boolean s2;
    private boolean privateMethod() {
        return true;
    }
    
    public boolean publicMethod() {
        return true;
    }
    
    public boolean otherSuper() {
        return true;
    }
}

interface SomeInt {
    default boolean itfeMethod() {
        return true;
    }
    
    boolean publicMethod();
}

interface AnotherInterface {
    boolean publicMethod();
}
''')
        when:"all methods are retrieved"
        def allMethods = classElement.getEnclosedElements(ElementQuery.ALL_METHODS)

        then:"All methods, including non-accessible are returned but not overridden"
        allMethods.size() == 6 // slightly different result to java since groovy hides private methods
        allMethods.find { it.name == 'publicMethod'}.declaringType.simpleName == 'Test'
        allMethods.find { it.name == 'otherSuper'}.declaringType.simpleName == 'SuperType'

        when:"obtaining only the declared methods"
        def declared = classElement.getEnclosedElements(ElementQuery.of(MethodElement).onlyDeclared())

        then:"The declared are correct"
        // this method differs for Groovy because for some reason default interface methods become
        // part of the methods declared by classNode.getMethods() and there is no way to distinguish them
        declared*.name as Set == ['privateMethod', 'packagePrivateMethod', 'publicMethod', 'staticMethod', 'itfeMethod'] as Set

        when:"Accessible methods are retrieved"
        def accessible = classElement.getEnclosedElements(ElementQuery.of(MethodElement).onlyAccessible())

        then:"Only accessible methods, excluding those that require reflection"
        accessible*.name as Set == ['otherSuper', 'itfeMethod', 'publicMethod', 'packagePrivateMethod', 'staticMethod'] as Set

        when:"static methods are resolved"
        def staticMethods = classElement.getEnclosedElements(ElementQuery.ALL_METHODS.modifiers({
            it.contains(ElementModifier.STATIC)
        }))

        then:"We only get statics"
        staticMethods.size() == 1
        staticMethods.first().name == 'staticMethod'

        when:"All fields are retrieved"
        def allFields = classElement.getEnclosedElements(ElementQuery.ALL_FIELDS)

        then:"we get everything"
        allFields.size() == 4

        when:"Accessible fields are retrieved"
        def accessibleFields = classElement.getEnclosedElements(ElementQuery.ALL_FIELDS.onlyAccessible())

        then:"we get everything"
        accessibleFields.size() == 2
        accessibleFields*.name as Set == ['s1', 't1'] as Set
    }

    void "test resolve generic type using getTypeArguments"() {
        buildBeanDefinition('test.TestController', '''
package test;

import io.micronaut.http.annotation.*;
import javax.inject.Inject;

@Controller("/test")
public class TestController implements java.util.function.Supplier<String> {
    
    @Get("/getMethod")
    public String get() {
        return null;
    }
    

}

''')
        expect:
        AllElementsVisitor.VISITED_CLASS_ELEMENTS.size() == 1
        AllElementsVisitor.VISITED_METHOD_ELEMENTS.size() == 1
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].getTypeArguments(Supplier).get("T").name == String.name
    }

    void "test class is visited by custom visitor"() {
        buildBeanDefinition('test.TestController', '''
package test;

import io.micronaut.http.annotation.*;
import javax.inject.Inject;

@Controller("/test")
public class TestController {
    
    @Get("/getMethod")
    public String[] getMethod(int[] argument) {
        return null;
    }
    

}
''')
        expect:
        AllElementsVisitor.VISITED_CLASS_ELEMENTS.size() == 1
        AllElementsVisitor.VISITED_METHOD_ELEMENTS.size() == 1
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].returnType.isArray()
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].parameters.size() == 1
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].parameters[0].type.isArray()
    }


    void "test visit methods that take and return enums"() {
        buildBeanDefinition('test.TestController', '''
package test;

import io.micronaut.http.annotation.*;
import io.micronaut.http.*;
import javax.inject.Inject;

@Controller("/test")
public class TestController {
    
    @Get("/getMethod")
    public HttpMethod getMethod(HttpMethod argument) {
        return null;
    }
    

}
''')
        expect:
        AllElementsVisitor.VISITED_CLASS_ELEMENTS.size() == 1
        AllElementsVisitor.VISITED_METHOD_ELEMENTS.size() == 1
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].returnType instanceof EnumElement
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].returnType.values().contains("GET")
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].parameters.size() == 1
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].parameters[0].type instanceof EnumElement
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].parameters[0].type.values().contains("POST")
    }

    void "test primitive types"() {
        buildBeanDefinition('test.TestController', '''
package test;

import io.micronaut.http.annotation.*;
import javax.inject.Inject;

@Controller("/test")
public class TestController {
    
    @Get("/getMethod")
    public int getMethod(long argument) {
        return 0;
    }
    

}
''')
        expect:
        AllElementsVisitor.VISITED_CLASS_ELEMENTS.size() == 1
        AllElementsVisitor.VISITED_METHOD_ELEMENTS.size() == 1
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].returnType.name == 'int'
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].parameters.size() == 1
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].parameters[0].type.name == 'long'
    }

    void "test generic types at type level"() {
        buildBeanDefinition('test.TestController', '''
package test;

import io.micronaut.http.annotation.*;
import javax.inject.Inject;

@Controller("/test")
public class TestController<T extends Foo> {
    
    @Get("/getMethod")
    public T getMethod(T argument) {
        return null;
    }
    

}

class Foo {}
''')
        expect:
        AllElementsVisitor.VISITED_CLASS_ELEMENTS.size() == 1
        AllElementsVisitor.VISITED_METHOD_ELEMENTS.size() == 1
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].returnType.name == 'test.Foo'
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].parameters.size() == 1
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].parameters[0].type.name == 'test.Foo'
    }

    void "test array generic types at type level"() {
        buildBeanDefinition('test.TestController', '''
package test;

import io.micronaut.http.annotation.*;
import javax.inject.Inject;

@Controller("/test")
public class TestController<T extends Foo> {
    
    @Get("/getMethod")
    public T[] getMethod(T[] argument) {
        return null;
    }
    

}

class Foo {}
''')
        expect:
        AllElementsVisitor.VISITED_CLASS_ELEMENTS.size() == 1
        AllElementsVisitor.VISITED_METHOD_ELEMENTS.size() == 1
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].returnType.name == 'test.Foo'
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].returnType.isArray()
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].parameters.size() == 1
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].parameters[0].type.name == 'test.Foo'
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].parameters[0].type.isArray()
    }

    void "test generic types at method level"() {
        buildBeanDefinition('test.TestController', '''
package test;

import io.micronaut.http.annotation.*;
import javax.inject.Inject;

@Controller("/test")
public class TestController {
    
    @Get("/getMethod")
    public <T extends Foo> T getMethod(T argument) {
        return null;
    }
    

}

class Foo {}
''')
        expect:
        AllElementsVisitor.VISITED_CLASS_ELEMENTS.size() == 1
        AllElementsVisitor.VISITED_METHOD_ELEMENTS.size() == 1
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].returnType.name == 'test.Foo'
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].parameters.size() == 1
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].parameters[0].type.name == 'test.Foo'
    }

    void "test generic types at type level used as type arguments"() {
        buildBeanDefinition('test.TestController', '''
package test;

import io.micronaut.http.annotation.*;
import javax.inject.Inject;

@Controller("/test")
public class TestController<T extends Foo> {
    
    @Get("/getMethod")
    public java.util.List<T> getMethod(java.util.Set<T> argument) {
        return null;
    }
    

}

class Foo {}
''')
        expect:
        AllElementsVisitor.VISITED_CLASS_ELEMENTS.size() == 1
        AllElementsVisitor.VISITED_METHOD_ELEMENTS.size() == 1
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].returnType.name == 'java.util.List'
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].returnType.typeArguments.size() == 1
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].returnType.typeArguments.get("E").name == 'test.Foo'
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].parameters.size() == 1
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].parameters[0].type.name == 'java.util.Set'
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].parameters[0].type.typeArguments.get("E").name == 'test.Foo'
    }
}
