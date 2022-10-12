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
import io.micronaut.context.exceptions.BeanContextException
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementModifier
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.EnumElement
import io.micronaut.inject.ast.FieldElement
import io.micronaut.inject.ast.MemberElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.PackageElement
import io.micronaut.inject.ast.PropertyElement
import io.micronaut.inject.ast.TypedElement
import spock.lang.Issue
import spock.lang.Unroll
import spock.util.environment.RestoreSystemProperties

import java.sql.SQLException
import java.util.function.Supplier
import java.util.stream.Collectors

@RestoreSystemProperties
class ClassElementSpec extends AbstractBeanDefinitionSpec {

    def setup() {
        System.setProperty(TypeElementVisitorStart.ELEMENT_VISITORS_PROPERTY, AllElementsVisitor.name)
    }

    def cleanup() {
        AllElementsVisitor.clearVisited()
    }

    @Issue("https://github.com/micronaut-projects/micronaut-openapi/issues/670")
    void "test correct properties decaliring class with inheritance"() {
        given:
        def controller = buildBeanDefinition('test.TestController', '''
package test

import groovy.transform.CompileStatic
import io.micronaut.core.annotation.Introspected
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

import javax.validation.constraints.NotNull

@Controller
class TestController {

    @Get
    PessoaFisicaDto test() {
        return null
    }
}

@Introspected
@CompileStatic
abstract class EntidadeDto {

    @NotNull
    UUID id
    Long tenantId
    Integer version
    @NotNull
    Date criadoEm
    @NotNull
    Date atualizadoEm
}

@Introspected
@CompileStatic
class PessoaFisicaDto extends EntidadeDto {

    String nome
    String nomeMae
    String nomePai
    String CPF
}

''')
        expect:
        controller
        AllElementsVisitor.VISITED_METHOD_ELEMENTS
        when:
        def method = AllElementsVisitor.VISITED_METHOD_ELEMENTS[0]
        def type = method.genericReturnType;
        List<PropertyElement> beanProperties = type.getBeanProperties().stream().filter(p -> !"groovy.lang.MetaClass".equals(p.getType().getName())).collect(Collectors.toList());

        List<TypedElement> childFieldsOwned = new ArrayList<>();
        List<TypedElement> childClassFields = new ArrayList<>();
        for (TypedElement publicField : beanProperties) {
            if (publicField instanceof MemberElement) {

                MemberElement memberEl = (MemberElement) publicField;
                if (memberEl.getDeclaringType().getType().getName() == type.getName()) {
                    childClassFields.add(publicField)
                }
                if (memberEl.getOwningType().getType().getName() == type.getName()) {
                    childFieldsOwned.add(publicField)
                }
            }
        }

        then:
        childClassFields
        childClassFields.size() == 4
        childFieldsOwned
        childFieldsOwned.size() == 9
    }

    void "test interface bean properties"() {
        given:
        def element = buildClassElement("""
package test

interface HealthResult {

    String getName();

    Object getStatus();

    Object getDetails();
}
""")

        List<PropertyElement> properties = element.getBeanProperties()
        expect:
        properties
        properties.size() == 3
    }

    @Unroll
    void "test throws declarations on method with generics"() {
        given:
        def element = buildClassElement("""
package throwstest;

import io.micronaut.context.exceptions.BeanContextException;

class Test extends Parent<BeanContextException> {}

class Parent<T extends RuntimeException> {
    void test() throws ${types.join(',')}{}
}
""")

        MethodElement methodElement = element.getEnclosedElement(ElementQuery.ALL_METHODS)
                .get()
        expect:
        methodElement.thrownTypes.size() == types.size()
        methodElement.thrownTypes*.name == expected

        where:
        types                                          | expected
        [SQLException.name]                            | [SQLException.name]
        [SQLException.name, BeanContextException.name] | [SQLException.name, BeanContextException.name]
        [SQLException.name, "T"]                       | [SQLException.name, BeanContextException.name]
    }

    @Unroll
    void "test throws declarations on method"() {
        given:
        def element = buildClassElement("""
package throwstest;

class Test<T extends RuntimeException> {
    void test() throws ${types.join(',')}{}
}
""")

        MethodElement methodElement = element.getEnclosedElement(ElementQuery.ALL_METHODS)
                .get()
        expect:
        methodElement.thrownTypes.size() == types.size()
        methodElement.thrownTypes*.name == expected

        where:
        types                                          | expected
        [SQLException.name]                            | [SQLException.name]
        [SQLException.name, BeanContextException.name] | [SQLException.name, BeanContextException.name]
        [SQLException.name, "T"]                       | [SQLException.name, RuntimeException.name]
    }

    void "test modifiers #modifiers"() {
        given:
        def element = buildClassElement("""
package modtest;

class Test {
    ${modifiers*.toString().join(' ')} String test = "test";

    ${modifiers*.toString().join(' ')} void test() {};
}
""")

        expect:
        element.getEnclosedElement(ElementQuery.ALL_FIELDS).get().modifiers == modifiers
        element.getEnclosedElement(ElementQuery.ALL_METHODS).get().modifiers == modifiers

        where:
        modifiers << [
                [ElementModifier.PUBLIC] as Set,
                [ElementModifier.PUBLIC, ElementModifier.STATIC] as Set,
                [ElementModifier.PUBLIC, ElementModifier.STATIC, ElementModifier.FINAL] as Set,
        ]
    }

    void "test get package element"() {
        given:
        def element = buildClassElement('''
package pkgeltest;

class PckElementTest {

}
''')
        PackageElement pe = element.getPackage()

        expect:
        pe.name == 'pkgeltest'
        pe.simpleName == 'pkgeltest'
        pe.getClass().name.contains("GroovyPackageElement")
    }

    void "test get full package element"() {
        given:
        def element = buildClassElement('''
package abc.my.pkgeltest;

class PckElementTest {

}
''')
        PackageElement pe = element.getPackage()

        expect:
        pe.name == 'abc.my.pkgeltest'
        pe.simpleName == 'pkgeltest'
        pe.getClass().name.contains("GroovyPackageElement")
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
        allMethods.size() == 10

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
        allMethods.size() == 7
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
        buildBeanDefinition('clselem1.TestController', '''
package clselem1;

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
        buildBeanDefinition('clselem2.TestController', '''
package clselem2;

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
        buildBeanDefinition('clselem3.TestController', '''
package clselem3;

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
        buildBeanDefinition('clselem4.TestController', '''
package clselem4;

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
        buildBeanDefinition('clselem5.TestController', '''
package clselem5;

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
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].returnType.name == 'clselem5.Foo'
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].parameters.size() == 1
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].parameters[0].type.name == 'clselem5.Foo'
    }

    void "test array generic types at type level"() {
        buildBeanDefinition('clselem6.TestController', '''
package clselem6;

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
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].returnType.name == 'clselem6.Foo'
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].returnType.isArray()
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].parameters.size() == 1
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].parameters[0].type.name == 'clselem6.Foo'
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].parameters[0].type.isArray()
    }

    void "test generic types at method level"() {
        buildBeanDefinition('clselem7.TestController', '''
package clselem7;

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
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].returnType.name == 'clselem7.Foo'
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].parameters.size() == 1
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].parameters[0].type.name == 'clselem7.Foo'
    }

    void "test generic types at type level used as type arguments"() {
        buildBeanDefinition('clselem8.TestController', '''
package clselem8;

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
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].returnType.typeArguments.get("E").name == 'clselem8.Foo'
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].parameters.size() == 1
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].parameters[0].type.name == 'java.util.Set'
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].parameters[0].type.typeArguments.get("E").name == 'clselem8.Foo'
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/6430")
    void "test property inheritance type annotation metadata"() {
        given:
        def classElement = buildClassElement("""
package io.micronaut.inject.visitor

@SomeAnn
class DiscountEO {
}
abstract class TransactionPO {
    DiscountEO discount
}
class InvoicePO extends TransactionPO {
}
""")

        expect:
        classElement.getBeanProperties().find { it.name == "discount" }.getType().hasAnnotation(SomeAnn)
    }

    void "test find enum fields using ElementQuery"() {
        given:
            ClassElement classElement = buildClassElement('''
package elementquery;

enum Test {

    A, B, C;

    public static final String publicStaticFinalField = "";
    public static String publicStaticField;
    public final String publicFinalField = "";
    public String publicField;

    protected static final String protectedStaticFinalField = "";
    protected static String protectedStaticField;
    protected final String protectedFinalField = "";
    protected String protectedField;

    static final String packagePrivateStaticFinalField = "";
    static String packagePrivateStaticField;
    final String packagePrivateFinalField = "";
    String packagePrivateField;

    private static final String privateStaticFinalField = "";
    private static String privateStaticField;
    private final String privateFinalField = "";
    private String privateField;
}
''')
        when:
        List<FieldElement> allFields = classElement.getEnclosedElements(ElementQuery.ALL_FIELDS)
        List<String> expected = [
                'A',
                'B',
                'C',
                'publicStaticFinalField',
                'publicStaticField',
                'publicFinalField',
                'publicField',
                'protectedStaticFinalField',
                'protectedStaticField',
                'protectedFinalField',
                'protectedField',
                'packagePrivateStaticFinalField',
                'packagePrivateStaticField',
                'packagePrivateFinalField',
                'packagePrivateField',
                'privateStaticFinalField',
                'privateStaticField',
                'privateFinalField',
                'privateField',
                'MIN_VALUE',
                'MAX_VALUE',
                'name',
                'ordinal',
        ]

        then:
        for (String name : allFields*.name) {
            assert expected.contains(name)
        }
        allFields.size() == expected.size()

        when:
        allFields = classElement.getEnclosedElements(ElementQuery.ALL_FIELDS.includeEnumConstants())

        then:
        for (String name : allFields*.name) {
            assert expected.contains(name)
        }
        allFields.size() == expected.size()
    }

    void "test unrecognized default method"() {
        given:
            ClassElement classElement = buildClassElement('elementquery.MyBean', '''
package elementquery;

class Generic {
}
class Specific extends Generic {
}
interface GenericInterface {
    Generic getObject()
}
interface SpecificInterface {
    Specific getObject()
}

interface MyBean extends GenericInterface, SpecificInterface {

    default Specific getObject() {
        return null
    }

}

''')
        when:
            def allMethods = classElement.getEnclosedElements(ElementQuery.ALL_METHODS)
        then:
            allMethods.size() == 1
        when:
            def declaredMethods = classElement.getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared())
        then:
            declaredMethods.size() == 1
            declaredMethods.get(0).isAbstract() == true
            declaredMethods.get(0).isDefault() == false
    }

    // Groovy bug?
    void "test unrecognized default method 2"() {
        given:
            ClassElement classElement = buildClassElement('elementquery.MyBean', '''
package elementquery;

interface MyBean {

    default String getObject() {
        return null
    }

}

''')
        when:
            def allMethods = classElement.getEnclosedElements(ElementQuery.ALL_METHODS)
        then:
            allMethods.size() == 1
            allMethods.get(0).isAbstract() == true
            allMethods.get(0).isDefault() == false
    }

    // Groovy bug?
    void "test default method"() {
        given:
            ClassElement classElement = buildClassElement('elementquery.MyBean', '''
package elementquery;

interface MyInt {

    default String getObject() {
        return null
    }

}

class MyBean implements MyInt {
}

''')
        when:
            def allMethods = classElement.getEnclosedElements(ElementQuery.ALL_METHODS)
        then:
            // In this case the default method is not abstract but still not default
            allMethods.size() == 1
            allMethods.get(0).isAbstract() == false
            allMethods.get(0).isDefault() == false
    }
}
