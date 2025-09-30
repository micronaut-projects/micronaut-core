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

import io.micronaut.ast.groovy.TypeElementVisitorStart
import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.exceptions.BeanContextException
import io.micronaut.core.annotation.Introspected
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementModifier
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.EnumElement
import io.micronaut.inject.ast.FieldElement
import io.micronaut.inject.ast.GenericPlaceholderElement
import io.micronaut.inject.ast.MemberElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.PackageElement
import io.micronaut.inject.ast.PrimitiveElement
import io.micronaut.inject.ast.PropertyElement
import io.micronaut.inject.ast.TypedElement
import io.micronaut.inject.ast.WildcardElement
import jakarta.validation.Valid
import spock.lang.Issue
import spock.lang.PendingFeature
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

    void "test no package"() {
        given:
            def element = buildClassElement("""

class Test {
}
""")

        expect:
            element.getPackage().getName() == ""
            element.getPackageName() == ""
    }

    void "test equals with primitive"() {
        given:
            def element = buildClassElement("""
package test

class Test {

boolean test1

}
""")

        expect:
            element != PrimitiveElement.BOOLEAN
            element != PrimitiveElement.VOID
            element != PrimitiveElement.BOOLEAN.withArrayDimensions(4)
            PrimitiveElement.VOID != element
            PrimitiveElement.INT != element
            PrimitiveElement.INT.withArrayDimensions(2) != element
            element.getFields().get(0).getType() == PrimitiveElement.BOOLEAN
            PrimitiveElement.BOOLEAN == element.getFields().get(0).getType()
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

import jakarta.validation.constraints.NotNull

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
package elementquery

import groovy.transform.PackageScope;

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
    @PackageScope
    boolean s1;
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
        when: "all methods are retrieved"
        def allMethods = classElement.getEnclosedElements(ElementQuery.ALL_METHODS)

        then: "All methods, including non-accessible are returned but not overridden"
        allMethods.size() == 7
        allMethods.find { it.name == 'publicMethod' }.declaringType.simpleName == 'Test'
        allMethods.find { it.name == 'otherSuper' }.declaringType.simpleName == 'SuperType'

        when: "obtaining only the declared methods"
        def declared = classElement.getEnclosedElements(ElementQuery.of(MethodElement).onlyDeclared())

        then:"The declared are correct"
        // this method differs for Groovy because for some reason default interface methods become
        // part of the methods declared by classNode.getMethods() and there is no way to distinguish them
        declared*.name as Set == ['privateMethod', 'packagePrivateMethod', 'publicMethod', 'staticMethod', 'itfeMethod'] as Set

        when: "Accessible methods are retrieved"
        def accessible = classElement.getEnclosedElements(ElementQuery.of(MethodElement).onlyAccessible())

        then: "Only accessible methods, excluding those that require reflection"
        accessible.size() == 5
        accessible*.name as Set == ['otherSuper', 'itfeMethod', 'publicMethod', 'packagePrivateMethod', 'staticMethod'] as Set

        when: "static methods are resolved"
        def staticMethods = classElement.getEnclosedElements(ElementQuery.ALL_METHODS.modifiers({
            it.contains(ElementModifier.STATIC)
        }))

        then: "We only get statics"
        staticMethods.size() == 1
        staticMethods.first().name == 'staticMethod'

        when: "All fields are retrieved"
        def allFields = classElement.getEnclosedElements(ElementQuery.ALL_FIELDS)

        then: "we get everything"
        allFields.size() == 4

        when: "Accessible fields are retrieved"
        def accessibleFields = classElement.getEnclosedElements(ElementQuery.ALL_FIELDS.onlyAccessible())

        then: "we get everything"
        accessibleFields.size() == 2
        accessibleFields*.name as Set == ['s1', 't1'] as Set
    }

    void "test resolve generic type using getTypeArguments"() {
        buildBeanDefinition('clselem1.TestController', '''
package clselem1;

import io.micronaut.http.annotation.*;
import jakarta.inject.Inject;

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
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].getAllTypeArguments().get(Supplier.class.name).get("T").name == String.name
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].getTypeArguments(Supplier).get("T").name == String.name
    }

    void "test class is visited by custom visitor"() {
        buildBeanDefinition('clselem2.TestController', '''
package clselem2;

import io.micronaut.http.annotation.*;
import jakarta.inject.Inject;

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
import jakarta.inject.Inject;

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
import jakarta.inject.Inject;

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
import jakarta.inject.Inject;

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
import jakarta.inject.Inject;

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
import jakarta.inject.Inject;

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
import jakarta.inject.Inject;

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

        def type = AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].returnType
        type.name == 'java.util.List'
        type.typeArguments.size() == 1
        type.typeArguments.get("E").name == 'clselem8.Foo'
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
                'MIN_VALUE', // Extra field in Groovy
                'MAX_VALUE' // Extra field in Groovy
        ]

        then:
        for (String name : allFields*.name) {
            assert expected.contains(name)
        }
        allFields.size() == expected.size()

        when:
        allFields = classElement.getEnclosedElements(ElementQuery.ALL_FIELDS.includeEnumConstants())
        expected = ['A', 'B', 'C'] + expected

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
            allMethods.size() == 2
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

    void "test synthetic properties aren't removed"() {
        given:
            ClassElement classElement = buildClassElement('elementquery.SuccessfulTest', '''
package elementquery

import io.micronaut.context.ApplicationContext;
import spock.lang.Shared
import spock.lang.Specification

import jakarta.inject.Inject

class AbstractExample extends Specification {

    @Inject
    @Shared
    ApplicationContext sharedCtx

    @Inject
    ApplicationContext ctx

}

class FailingTest extends AbstractExample {

    def 'injection is not null'() {
        expect:
        ctx != null
    }

    def 'shared injection is not null'() {
        expect:
        sharedCtx != null
    }
}

class SuccessfulTest extends AbstractExample {

    @Shared
    @Inject
    ApplicationContext dummy

    def 'injection is not null'() {
        expect:
        ctx != null
    }

    def 'shared injection is not null'() {
        expect:
        sharedCtx != null
    }
}


''')
        when:
            def props = classElement.getSyntheticBeanProperties()
            def allFields = classElement.getEnclosedElements(ElementQuery.ALL_FIELDS)
        then:
            props.size() == 3
            props[0].name == '$spock_sharedField_sharedCtx'
            props[1].name == "ctx"
            props[2].name.contains "dummy"
            allFields.size() == 3
            allFields[0].name == '$spock_sharedField_sharedCtx'
            allFields[1].name == "ctx"
            allFields[2].name.contains "dummy"
    }

    void "test fields selection"() {
        given:
            ClassElement classElement = buildClassElement('test.PetOperations', '''
package test

import groovy.transform.PackageScope;
import io.micronaut.http.annotation.*;
import jakarta.inject.Inject;

@Controller("/pets")
interface PetOperations<T extends Pet> {

    @Post("/")
    T save(String name, int age);
}

class Pet {
    public int pub;

    private String prvn;

    protected String protectme;

    @PackageScope
    String packprivme;

    public static String PUB_CONST;

    private static String PRV_CONST;

    protected static String PROT_CONST;

    @PackageScope
    static String PACK_PRV_CONST;
}

''')
        when:
            List<FieldElement> publicFields = classElement.getFirstTypeArgument()
                    .get()
                    .getEnclosedElements(ElementQuery.ALL_FIELDS.modifiers(mods -> mods.contains(ElementModifier.PUBLIC) && mods.size() == 1))
        then:
            publicFields.size() == 1
            publicFields.stream().map(FieldElement::getName).toList() == ["pub"]

        when:
            List<FieldElement> publicFields2 = classElement.getFirstTypeArgument()
                    .get()
                    .getEnclosedElements(ElementQuery.ALL_FIELDS.filter(e -> e.isPublic()))
        then:
            publicFields2.size() == 2
            publicFields2.stream().map(FieldElement::getName).toList() == ["pub", "PUB_CONST"]
        when:
            List<FieldElement> protectedFields = classElement.getFirstTypeArgument()
                    .get()
                    .getEnclosedElements(ElementQuery.ALL_FIELDS.filter(e -> e.isProtected()))
        then:
            protectedFields.size() == 2
            protectedFields.stream().map(FieldElement::getName).toList() == ["protectme", "PROT_CONST"]
        when:
            List<FieldElement> privateFields = classElement.getFirstTypeArgument()
                    .get()
                    .getEnclosedElements(ElementQuery.ALL_FIELDS.filter(e -> e.isPrivate()))
        then:
            privateFields.size() == 2
            privateFields.stream().map(FieldElement::getName).toList() == ["prvn", "PRV_CONST"]
        when:
            List<FieldElement> packPrvFields = classElement.getFirstTypeArgument()
                    .get()
                    .getEnclosedElements(ElementQuery.ALL_FIELDS.filter(e -> e.isPackagePrivate()))
        then:
            packPrvFields.size() == 2
            packPrvFields.stream().map(FieldElement::getName).toList() == ["packprivme", "PACK_PRV_CONST"]
    }

    void "test annotations on generic type"() {
        given:
            ClassElement classElement = buildClassElement('test.PetOperations', '''
package test

import io.micronaut.core.annotation.Introspected
import io.micronaut.http.annotation.*
import jakarta.inject.Inject

@Controller("/pets")
interface PetOperations<T extends Pet> {

    @Post("/")
    T save(String name, int age)
}

@Introspected
class Pet {
}

''')
        when:
            def method = classElement.getEnclosedElements(ElementQuery.ALL_METHODS.named("save")).get(0)
            def returnType = method.getReturnType()
            def genericReturnType = method.getGenericReturnType()

        then:
            returnType.hasAnnotation(Introspected)
            genericReturnType.hasAnnotation(Introspected)
    }

    void "test annotation metadata present on deep type parameters for field"() {
        ClassElement ce = buildClassElement('''
package test;
import io.micronaut.core.annotation.*;
import jakarta.validation.constraints.*;
import java.util.List;

class Test {
    List<@Size(min=1, max=2) List<@NotEmpty List<@NotNull String>>> deepList;
}
''')
        expect:
            def field = ce.getFields().find { it.name == "deepList"}
            def fieldType = field.getGenericType()

            fieldType.getAnnotationMetadata().getAnnotationNames().size() == 0

            assertListGenericArgument(fieldType, { ClassElement listArg1 ->
                assert listArg1.getAnnotationMetadata().getAnnotationNames().asList() == ['jakarta.validation.constraints.Size$List']
                assertListGenericArgument(listArg1, { ClassElement listArg2 ->
                    assert listArg2.getAnnotationMetadata().getAnnotationNames().asList() == ['jakarta.validation.constraints.NotEmpty$List']
                    assertListGenericArgument(listArg2, { ClassElement listArg3 ->
                        assert listArg3.getAnnotationMetadata().getAnnotationNames().asList() == ['jakarta.validation.constraints.NotNull$List']
                    })
                })
            })

            def level1 = fieldType.getTypeArguments()["E"]
            level1.getAnnotationMetadata().getAnnotationNames().asList() == ['jakarta.validation.constraints.Size$List']
            def level2 = level1.getTypeArguments()["E"]
            level2.getAnnotationMetadata().getAnnotationNames().asList() == ['jakarta.validation.constraints.NotEmpty$List']
            def level3 = level2.getTypeArguments()["E"]
            level3.getAnnotationMetadata().getAnnotationNames().asList() == ['jakarta.validation.constraints.NotNull$List']
    }

    void "test annotation metadata present on deep type parameters for method"() {
        ClassElement ce = buildClassElement('''
package test;
import io.micronaut.core.annotation.*;
import jakarta.validation.constraints.*;
import java.util.List;

class Test {
    List<@Size(min=1, max=2) List<@NotEmpty List<@NotNull String>>> deepList() {
        return null;
    }
}
''')
        expect:
            def method = ce.getEnclosedElement(ElementQuery.ALL_METHODS.named("deepList")).get()
            def theType = method.getGenericReturnType()

            theType.getAnnotationMetadata().getAnnotationNames().size() == 0

            assertListGenericArgument(theType, { ClassElement listArg1 ->
                assert listArg1.getAnnotationMetadata().getAnnotationNames().asList() == ['jakarta.validation.constraints.Size$List']
                assertListGenericArgument(listArg1, { ClassElement listArg2 ->
                    assert listArg2.getAnnotationMetadata().getAnnotationNames().asList() == ['jakarta.validation.constraints.NotEmpty$List']
                    assertListGenericArgument(listArg2, { ClassElement listArg3 ->
                        assert listArg3.getAnnotationMetadata().getAnnotationNames().asList() == ['jakarta.validation.constraints.NotNull$List']
                    })
                })
            })

            def level1 = theType.getTypeArguments()["E"]
            level1.getAnnotationMetadata().getAnnotationNames().asList() == ['jakarta.validation.constraints.Size$List']
            def level2 = level1.getTypeArguments()["E"]
            level2.getAnnotationMetadata().getAnnotationNames().asList() == ['jakarta.validation.constraints.NotEmpty$List']
            def level3 = level2.getTypeArguments()["E"]
            level3.getAnnotationMetadata().getAnnotationNames().asList() == ['jakarta.validation.constraints.NotNull$List']
    }

    void "test annotation metadata present on deep type parameters for method 2"() {
        ClassElement ce = buildClassElement('''
package test;
import io.micronaut.core.annotation.*;
import jakarta.validation.constraints.*;
import java.util.List;

class Test {
    List<List<List<@io.micronaut.inject.visitor.TypeUseRuntimeAnn @io.micronaut.inject.visitor.TypeUseClassAnn String>>> deepList() {
        return null;
    }
}
''')
        expect:
            def method = ce.getEnclosedElement(ElementQuery.ALL_METHODS.named("deepList")).get()
            def theType = method.getGenericReturnType()

            theType.getAnnotationMetadata().getAnnotationNames().size() == 0

            assertListGenericArgument(theType, { ClassElement listArg1 ->
                assertListGenericArgument(listArg1, { ClassElement listArg2 ->
                    assertListGenericArgument(listArg2, { ClassElement listArg3 ->
                        assert listArg3.getAnnotationMetadata().getAnnotationNames().asList() == ['io.micronaut.inject.visitor.TypeUseRuntimeAnn', 'io.micronaut.inject.visitor.TypeUseClassAnn']
                    })
                })
            })

            def level1 = theType.getTypeArguments()["E"]
            def level2 = level1.getTypeArguments()["E"]
            def level3 = level2.getTypeArguments()["E"]
            level3.getAnnotationMetadata().getAnnotationNames().asList() == ['io.micronaut.inject.visitor.TypeUseRuntimeAnn', 'io.micronaut.inject.visitor.TypeUseClassAnn' ]
    }

    void "test annotations on recursive generic type parameter 1"() {
        given:
            ClassElement ce = buildClassElement('''\
package test;

final class TrackedSortedSet<T extends @io.micronaut.inject.visitor.TypeUseRuntimeAnn java.lang.Comparable<? super T>> {
}

''')
        expect:
            def typeArguments = ce.getTypeArguments()
            typeArguments.size() == 1
            def typeArgument = typeArguments.get("T")
            typeArgument.name == "java.lang.Comparable"
            typeArgument.getAnnotationNames().asList() == ['io.micronaut.inject.visitor.TypeUseRuntimeAnn']
    }

    @PendingFeature
    void "test annotations on recursive generic type parameter 2"() {
        given:
            ClassElement ce = buildClassElement('''\
package test;

final class TrackedSortedSet<T extends java.lang.Comparable<? super @io.micronaut.inject.visitor.TypeUseRuntimeAnn T>> {
}

''')
        expect:
            def typeArguments = ce.getTypeArguments()
            typeArguments.size() == 1
            def typeArgument = typeArguments.get("T")
            typeArgument.name == "java.lang.Comparable"
            def nextTypeArguments = typeArgument.getTypeArguments()
            def nextTypeArgument = nextTypeArguments.get("T")
            nextTypeArgument.name == "java.lang.Comparable"
            nextTypeArgument.getAnnotationNames().asList() == ['io.micronaut.inject.visitor.TypeUseRuntimeAnn']
    }

    void "test recursive generic type parameter"() {
        given:
            ClassElement ce = buildClassElement('''\
package test;

final class TrackedSortedSet<T extends java.lang.Comparable<? super T>> {
}

''')
        expect:
            def typeArguments = ce.getTypeArguments()
            typeArguments.size() == 1
            def typeArgument = typeArguments.get("T")
            typeArgument.name == "java.lang.Comparable"
            def nextTypeArguments = typeArgument.getTypeArguments()
            def nextTypeArgument = nextTypeArguments.get("T")
            nextTypeArgument.name == "java.lang.Comparable"
            def nextNextTypeArguments = nextTypeArgument.getTypeArguments()
            def nextNextTypeArgument = nextNextTypeArguments.get("T")
            nextNextTypeArgument.name == "java.lang.Object"
    }

    void "test recursive generic type parameter 2"() {
        given:
            ClassElement ce = buildClassElement('''\
package test;

final class Test<T extends Test> { // Missing argument
}

''')
        expect:
            def typeArguments = ce.getTypeArguments()
            typeArguments.size() == 1
            def typeArgument = typeArguments.get("T")
            typeArgument.name == "test.Test"
            def nextTypeArguments = typeArgument.getTypeArguments()
            def nextTypeArgument = nextTypeArguments.get("T")
            nextTypeArgument.name == "test.Test"
            def nextNextTypeArguments = nextTypeArgument.getTypeArguments()
            def nextNextTypeArgument = nextNextTypeArguments.get("T")
            nextNextTypeArgument.name == "java.lang.Object"
    }

    void "test recursive generic type parameter 3"() {
        given:
            ClassElement ce = buildClassElement('''\
package test;

final class Test<T extends Test<T>> {
}

''')
        expect:
            def typeArguments = ce.getTypeArguments()
            typeArguments.size() == 1
            def typeArgument = typeArguments.get("T")
            typeArgument.name == "test.Test"
            def nextTypeArguments = typeArgument.getTypeArguments()
            def nextTypeArgument = nextTypeArguments.get("T")
            nextTypeArgument.name == "test.Test"
            def nextNextTypeArguments = nextTypeArgument.getTypeArguments()
            def nextNextTypeArgument = nextNextTypeArguments.get("T")
            nextNextTypeArgument.name == "java.lang.Object"
    }

    void "test recursive generic type parameter 4"() {
        given:
            ClassElement ce = buildClassElement('''\
package test;

final class Test<T extends Test<?>> {
}

''')
        expect:
            def typeArguments = ce.getTypeArguments()
            typeArguments.size() == 1
            def typeArgument = typeArguments.get("T")
            typeArgument.name == "test.Test"
            def nextTypeArguments = typeArgument.getTypeArguments()
            def nextTypeArgument = nextTypeArguments.get("T")
            nextTypeArgument.name == "test.Test"
            def nextNextTypeArguments = nextTypeArgument.getTypeArguments()
            def nextNextTypeArgument = nextNextTypeArguments.get("T")
            nextNextTypeArgument.name == "java.lang.Object"
    }

    void "test recursive generic method return"() {
        given:
            ClassElement ce = buildClassElement('''\
package test;

import org.hibernate.SessionFactory;
import org.hibernate.engine.spi.SessionFactoryDelegatingImpl;

class MyFactory {

    SessionFactory sessionFactory() {
        return new SessionFactoryDelegatingImpl(null);
    }
}

''')
        expect:
            def sessionFactoryMethod = ce.getEnclosedElement(ElementQuery.ALL_METHODS.named("sessionFactory")).get()
            def withOptionsMethod = sessionFactoryMethod.getReturnType().getEnclosedElement(ElementQuery.ALL_METHODS.named("withOptions")).get()
            def typeArguments = withOptionsMethod.getReturnType().getTypeArguments()
            typeArguments.size() == 1
            def typeArgument = typeArguments.get("T")
            typeArgument.name == "org.hibernate.SessionBuilder"
            def nextTypeArguments = typeArgument.getTypeArguments()
            def nextTypeArgument = nextTypeArguments.get("T")
            nextTypeArgument.name == "org.hibernate.SessionBuilder"
            def nextNextTypeArguments = nextTypeArgument.getTypeArguments()
            def nextNextTypeArgument = nextNextTypeArguments.get("T")
            nextNextTypeArgument.name == "java.lang.Object"
    }

    void "test recursive generic method return 2"() {
        given:
            ClassElement ce = buildClassElement('''\
package test;

interface MyBuilder<T extends MyBuilder> {
    T build();
}

class MyBean {

   MyBuilder<test.MyBuilder> myBuilder() {
       return null;
   }

}

class MyFactory {

    MyBean myBean() {
        return new MyBean();
    }
}


''')
        expect:
            def myBeanMethod = ce.getEnclosedElement(ElementQuery.ALL_METHODS.named("myBean")).get()
            def myBuilderMethod = myBeanMethod.getReturnType().getEnclosedElement(ElementQuery.ALL_METHODS.named("myBuilder")).get()
            def typeArguments = myBuilderMethod.getReturnType().getTypeArguments()
            typeArguments.size() == 1
            def typeArgument = typeArguments.get("T")
            typeArgument.name == "test.MyBuilder"
            def nextTypeArguments = typeArgument.getTypeArguments()
            def nextTypeArgument = nextTypeArguments.get("T")
            nextTypeArgument.name == "test.MyBuilder"
            def nextNextTypeArguments = nextTypeArgument.getTypeArguments()
            def nextNextTypeArgument = nextNextTypeArguments.get("T")
            nextNextTypeArgument.name == "test.MyBuilder"
            def nextNextNextTypeArguments = nextNextTypeArgument.getTypeArguments()
            def nextNextNextTypeArgument = nextNextNextTypeArguments.get("T")
            nextNextNextTypeArgument.name == "java.lang.Object"
    }

    void "test recursive generic method return 3"() {
        given:
            ClassElement ce = buildClassElement('''\
package test;

interface MyBuilder<T extends MyBuilder> {
    T build();
}

class MyBean {

   MyBuilder myBuilder() {
       return null;
   }

}

class MyFactory {

    MyBean myBean() {
        return new MyBean();
    }
}

''')
        expect:
            def myBeanMethod = ce.getEnclosedElement(ElementQuery.ALL_METHODS.named("myBean")).get()
            def myBuilderMethod = myBeanMethod.getReturnType().getEnclosedElement(ElementQuery.ALL_METHODS.named("myBuilder")).get()
            def typeArguments = myBuilderMethod.getReturnType().getTypeArguments()
            typeArguments.size() == 1
            def typeArgument = typeArguments.get("T")
            typeArgument.name == "test.MyBuilder"
            def nextTypeArguments = typeArgument.getTypeArguments()
            def nextTypeArgument = nextTypeArguments.get("T")
            nextTypeArgument.name == "test.MyBuilder"
            def nextNextTypeArguments = nextTypeArgument.getTypeArguments()
            def nextNextTypeArgument = nextNextTypeArguments.get("T")
            nextNextTypeArgument.name == "java.lang.Object"
    }

    void "test recursive generic method return 4"() {
        given:
            ClassElement ce = buildClassElement('''\
package test;

interface MyBuilder<T extends MyBuilder> {
    T build();
}

class MyBean {

   MyBuilder<?> myBuilder() {
       return null;
   }

}

class MyFactory {

    MyBean myBean() {
        return new MyBean();
    }
}

''')
        expect:
            def myBeanMethod = ce.getEnclosedElement(ElementQuery.ALL_METHODS.named("myBean")).get()
            def myBuilderMethod = myBeanMethod.getReturnType().getEnclosedElement(ElementQuery.ALL_METHODS.named("myBuilder")).get()
            def typeArguments = myBuilderMethod.getReturnType().getTypeArguments()
            typeArguments.size() == 1
            def typeArgument = typeArguments.get("T")
            typeArgument.name == "test.MyBuilder"
            def nextTypeArguments = typeArgument.getTypeArguments()
            def nextTypeArgument = nextTypeArguments.get("T")
            nextTypeArgument.name == "test.MyBuilder"
            def nextNextTypeArguments = nextTypeArgument.getTypeArguments()
            def nextNextTypeArgument = nextNextTypeArguments.get("T")
            nextNextTypeArgument.name == "java.lang.Object"
    }

    void "test recursive generic method return 5"() {
        given:
            ClassElement ce = buildClassElement('''\
package test;

interface MyBuilder<T extends MyBuilder> {
    T build();
}

class MyBean {

   MyBuilder<? extends MyBuilder> myBuilder() {
       return null;
   }

}

class MyFactory {

    MyBean myBean() {
        return new MyBean();
    }
}

''')
        expect:
            def myBeanMethod = ce.getEnclosedElement(ElementQuery.ALL_METHODS.named("myBean")).get()
            def myBuilderMethod = myBeanMethod.getReturnType().getEnclosedElement(ElementQuery.ALL_METHODS.named("myBuilder")).get()
            def typeArguments = myBuilderMethod.getReturnType().getTypeArguments()
            typeArguments.size() == 1
            def typeArgument = typeArguments.get("T")
            typeArgument.name == "test.MyBuilder"
            def nextTypeArguments = typeArgument.getTypeArguments()
            def nextTypeArgument = nextTypeArguments.get("T")
            nextTypeArgument.name == "test.MyBuilder"
            def nextNextTypeArguments = nextTypeArgument.getTypeArguments()
            def nextNextTypeArgument = nextNextTypeArguments.get("T")
            nextNextTypeArgument.name == "test.MyBuilder"
            def nextNextNextTypeArguments = nextNextTypeArgument.getTypeArguments()
            def nextNextNextTypeArgument = nextNextNextTypeArguments.get("T")
            nextNextNextTypeArgument.name == "java.lang.Object"
    }

    void "test recursive generic method return 6"() {
        given:
            ClassElement ce = buildClassElement('''\
package test;

interface MyBuilder<T extends MyBuilder> {
    T build();
}

class MyBean<T extends MyBuilder> {

   MyBuilder<T> myBuilder() {
       return null;
   }

}

class MyFactory {

    MyBean myBean() {
        return new MyBean();
    }
}

''')
        expect:
            def myBeanMethod = ce.getEnclosedElement(ElementQuery.ALL_METHODS.named("myBean")).get()
            def myBuilderMethod = myBeanMethod.getReturnType().getEnclosedElement(ElementQuery.ALL_METHODS.named("myBuilder")).get()
            def typeArguments = myBuilderMethod.getReturnType().getTypeArguments()
            typeArguments.size() == 1
            def typeArgument = typeArguments.get("T")
            typeArgument.name == "test.MyBuilder"
            def nextTypeArguments = typeArgument.getTypeArguments()
            def nextTypeArgument = nextTypeArguments.get("T")
            nextTypeArgument.name == "test.MyBuilder"
            def nextNextTypeArguments = nextTypeArgument.getTypeArguments()
            def nextNextTypeArgument = nextNextTypeArguments.get("T")
            nextNextTypeArgument.name == "java.lang.Object"
    }

    void "test recursive generic method return 7"() {
        given:
            ClassElement ce = buildClassElement('''\
package test;

interface MyBuilder<T extends MyBuilder> {
    T build();
}

class MyBean<T extends MyBuilder> {

   MyBuilder<? extends T> myBuilder() {
       return null;
   }

}

class MyFactory {

    MyBean myBean() {
        return new MyBean();
    }
}

''')
        expect:
            def myBeanMethod = ce.getEnclosedElement(ElementQuery.ALL_METHODS.named("myBean")).get()
            def myBuilderMethod = myBeanMethod.getReturnType().getEnclosedElement(ElementQuery.ALL_METHODS.named("myBuilder")).get()
            def typeArguments = myBuilderMethod.getReturnType().getTypeArguments()
            typeArguments.size() == 1
            def typeArgument = typeArguments.get("T")
            typeArgument.name == "test.MyBuilder"
            def nextTypeArguments = typeArgument.getTypeArguments()
            def nextTypeArgument = nextTypeArguments.get("T")
            nextTypeArgument.name == "test.MyBuilder"
            def nextNextTypeArguments = nextTypeArgument.getTypeArguments()
            def nextNextTypeArgument = nextNextTypeArguments.get("T")
            nextNextTypeArgument.name == "test.MyBuilder"
            def nextNextNextTypeArguments = nextNextTypeArgument.getTypeArguments()
            def nextNextNextTypeArgument = nextNextNextTypeArguments.get("T")
            nextNextNextTypeArgument.name == "java.lang.Object"
    }

    void "test generics model"() {
        ClassElement ce = buildClassElement('''
package test;
import java.util.List;

class Test {
    List<List<List<String>>> method1() {
        return null;
    }
}
''')
        expect:
            def method1 = ce.getEnclosedElement(ElementQuery.ALL_METHODS.named("method1")).get()
            def genericType = method1.getGenericReturnType()
            def genericTypeLevel1 = genericType.getTypeArguments()["E"]
            !genericTypeLevel1.isGenericPlaceholder()
            !genericTypeLevel1.isWildcard()
            def genericTypeLevel2 = genericTypeLevel1.getTypeArguments()["E"]
            !genericTypeLevel2.isGenericPlaceholder()
            !genericTypeLevel2.isWildcard()
            def genericTypeLevel3 = genericTypeLevel2.getTypeArguments()["E"]
            !genericTypeLevel3.isGenericPlaceholder()
            !genericTypeLevel3.isWildcard()

            def type = method1.getReturnType()
            def typeLevel1 = type.getTypeArguments()["E"]
            !typeLevel1.isGenericPlaceholder()
            !typeLevel1.isWildcard()
            def typeLevel2 = typeLevel1.getTypeArguments()["E"]
            !typeLevel2.isGenericPlaceholder()
            !typeLevel2.isWildcard()
            def typeLevel3 = typeLevel2.getTypeArguments()["E"]
            !typeLevel3.isGenericPlaceholder()
            !typeLevel3.isWildcard()
    }

    void "test generics model for wildcard"() {
        ClassElement ce = buildClassElement('''
package test;
import java.util.List;

class Test<T> {

    List<?> method() {
        return null;
    }
}
''')
        expect:
            def method = ce.getEnclosedElement(ElementQuery.ALL_METHODS.named("method")).get()
            def genericTypeArgument = method.getGenericReturnType().getTypeArguments()["E"]
            !genericTypeArgument.isGenericPlaceholder()
            !genericTypeArgument.isRawType()
            genericTypeArgument.isWildcard()

            def typeArgument = method.getReturnType().getTypeArguments()["E"]
            !typeArgument.isGenericPlaceholder()
            !typeArgument.isRawType()
            typeArgument.isWildcard()
    }

    void "test generics model for placeholder"() {
        ClassElement ce = buildClassElement('''
package test;
import java.util.List;

class Test<T> {

    List<T> method() {
        return null;
    }
}
''')
        expect:
            def method = ce.getEnclosedElement(ElementQuery.ALL_METHODS.named("method")).get()
            def genericTypeArgument = method.getGenericReturnType().getTypeArguments()["E"]
            genericTypeArgument.isGenericPlaceholder()
            !genericTypeArgument.isRawType()
            !genericTypeArgument.isWildcard()

            def typeArgument = method.getReturnType().getTypeArguments()["E"]
            typeArgument.isGenericPlaceholder()
            !typeArgument.isRawType()
            !typeArgument.isWildcard()
    }

    void "test generics model for class placeholder wildcard"() {
        ClassElement ce = buildClassElement('''
package test;
import java.util.List;

class Test<T> {

    List<? extends T> method() {
        return null;
    }
}
''')
        expect:
            def method = ce.getEnclosedElement(ElementQuery.ALL_METHODS.named("method")).get()
            def genericTypeArgument = method.getGenericReturnType().getTypeArguments()["E"]
            !genericTypeArgument.isGenericPlaceholder()
            !genericTypeArgument.isRawType()
            genericTypeArgument.isWildcard()

            def genericWildcard = genericTypeArgument as WildcardElement
            !genericWildcard.lowerBounds
            genericWildcard.upperBounds.size() == 1
            def genericUpperBound = genericWildcard.upperBounds[0]
            genericUpperBound.name == "java.lang.Object"
            genericUpperBound.isGenericPlaceholder()
            !genericUpperBound.isWildcard()
            !genericUpperBound.isRawType()
            def genericPlaceholderUpperBound = genericUpperBound as GenericPlaceholderElement
            genericPlaceholderUpperBound.variableName == "T"
            genericPlaceholderUpperBound.declaringElement.get() == ce

            def typeArgument = method.getReturnType().getTypeArguments()["E"]
            !typeArgument.isGenericPlaceholder()
            !typeArgument.isRawType()
            typeArgument.isWildcard()

            def wildcard = genericTypeArgument as WildcardElement
            !wildcard.lowerBounds
            wildcard.upperBounds.size() == 1
            def upperBound = wildcard.upperBounds[0]
            upperBound.name == "java.lang.Object"
            upperBound.isGenericPlaceholder()
            !upperBound.isWildcard()
            !upperBound.isRawType()
            def placeholderUpperBound = upperBound as GenericPlaceholderElement
            placeholderUpperBound.variableName == "T"
            placeholderUpperBound.declaringElement.get() == ce
    }

    void "test generics model for method placeholder wildcard"() {
        ClassElement ce = buildClassElement('''
package test;
import java.util.List;

class Test {

    <T> List<? extends T> method() {
        return null;
    }
}
''')
        expect:
            def method = ce.getEnclosedElement(ElementQuery.ALL_METHODS.named("method")).get()
            method.getDeclaredTypeVariables().size() == 1
            method.getDeclaredTypeVariables()[0].declaringElement.get() == method
            method.getDeclaredTypeVariables()[0].variableName == "T"
            method.getDeclaredTypeArguments().size() == 1
            def placeholder = method.getDeclaredTypeArguments()["T"] as GenericPlaceholderElement
            placeholder.declaringElement.get() == method
            placeholder.variableName == "T"
            def genericTypeArgument = method.getGenericReturnType().getTypeArguments()["E"]
            !genericTypeArgument.isGenericPlaceholder()
            !genericTypeArgument.isRawType()
            genericTypeArgument.isWildcard()

            def genericWildcard = genericTypeArgument as WildcardElement
            !genericWildcard.lowerBounds
            genericWildcard.upperBounds.size() == 1
            def genericUpperBound = genericWildcard.upperBounds[0]
            genericUpperBound.name == "java.lang.Object"
            genericUpperBound.isGenericPlaceholder()
            !genericUpperBound.isWildcard()
            !genericUpperBound.isRawType()
            def genericPlaceholderUpperBound = genericUpperBound as GenericPlaceholderElement
            genericPlaceholderUpperBound.variableName == "T"
            genericPlaceholderUpperBound.declaringElement.get() == method

            def typeArgument = method.getReturnType().getTypeArguments()["E"]
            !typeArgument.isGenericPlaceholder()
            !typeArgument.isRawType()
            typeArgument.isWildcard()

            def wildcard = genericTypeArgument as WildcardElement
            !wildcard.lowerBounds
            wildcard.upperBounds.size() == 1
            def upperBound = wildcard.upperBounds[0]
            upperBound.name == "java.lang.Object"
            upperBound.isGenericPlaceholder()
            !upperBound.isWildcard()
            !upperBound.isRawType()
            def placeholderUpperBound = upperBound as GenericPlaceholderElement
            placeholderUpperBound.variableName == "T"
            placeholderUpperBound.declaringElement.get() == method
    }

    void "test generics model for constructor placeholder wildcard"() {
        ClassElement ce = buildClassElement('''
package test;
import java.util.List;

class Test {

    <T> Test(List<? extends T> list) {
    }
}
''')
        expect:
            def method = ce.getPrimaryConstructor().get()
            method.getDeclaredTypeVariables().size() == 1
            method.getDeclaredTypeVariables()[0].declaringElement.get() == method
            method.getDeclaredTypeVariables()[0].variableName == "T"
            method.getDeclaredTypeArguments().size() == 1
            def placeholder = method.getDeclaredTypeArguments()["T"] as GenericPlaceholderElement
            placeholder.declaringElement.get() == method
            placeholder.variableName == "T"
            def genericTypeArgument = method.getParameters()[0].getGenericType().getTypeArguments()["E"]
            !genericTypeArgument.isGenericPlaceholder()
            !genericTypeArgument.isRawType()
            genericTypeArgument.isWildcard()

            def genericWildcard = genericTypeArgument as WildcardElement
            !genericWildcard.lowerBounds
            genericWildcard.upperBounds.size() == 1
            def genericUpperBound = genericWildcard.upperBounds[0]
            genericUpperBound.name == "java.lang.Object"
            genericUpperBound.isGenericPlaceholder()
            !genericUpperBound.isWildcard()
            !genericUpperBound.isRawType()
            def genericPlaceholderUpperBound = genericUpperBound as GenericPlaceholderElement
            genericPlaceholderUpperBound.variableName == "T"
            genericPlaceholderUpperBound.declaringElement.get() == method

            def typeArgument = method.getParameters()[0].getType().getTypeArguments()["E"]
            !typeArgument.isGenericPlaceholder()
            !typeArgument.isRawType()
            typeArgument.isWildcard()

            def wildcard = genericTypeArgument as WildcardElement
            !wildcard.lowerBounds
            wildcard.upperBounds.size() == 1
            def upperBound = wildcard.upperBounds[0]
            upperBound.name == "java.lang.Object"
            upperBound.isGenericPlaceholder()
            !upperBound.isWildcard()
            !upperBound.isRawType()
            def placeholderUpperBound = upperBound as GenericPlaceholderElement
            placeholderUpperBound.variableName == "T"
            placeholderUpperBound.declaringElement.get() == method
    }

    void "test generics equality"() {
        ClassElement ce = buildClassElement('''
package test;
import java.util.List;

class Test<T extends Number> {

    Number number;

    <N extends T> Test(List<? extends N> list) {
    }

    <N extends T> List<? extends N> method1() {
        return null;
    }

    List<? extends T> method2() {
        return null;
    }

    T method3() {
        return null;
    }

    List<List<? extends T>> method4() {
        return null;
    }

    List<List<T>> method5() {
        return null;
    }

    Test<T> method6() {
        return null;
    }

    Test<?> method7() {
        return null;
    }

    Test method8() {
        return null;
    }

    <N extends T> Test<? extends N> method9() {
        return null;
    }

    <N extends T> Test<? super N> method10() {
        return null;
    }
}
''')
        expect:
            def numberType = ce.getFields()[0].getType()
            def constructor = ce.getPrimaryConstructor().get()
            constructor.getParameters()[0].getGenericType().getTypeArguments(List).get("E") == numberType
            constructor.getParameters()[0].getType().getTypeArguments(List).get("E") == numberType

            ce.findMethod("method1").get().getGenericReturnType().getTypeArguments(List).get("E") == numberType
            ce.findMethod("method1").get().getReturnType().getTypeArguments(List).get("E") == numberType

            ce.findMethod("method2").get().getGenericReturnType().getTypeArguments(List).get("E") == numberType
            ce.findMethod("method2").get().getReturnType().getTypeArguments(List).get("E") == numberType

            ce.findMethod("method3").get().getGenericReturnType() == numberType
            ce.findMethod("method3").get().getReturnType() == numberType

            ce.findMethod("method4").get().getGenericReturnType().getTypeArguments(List).get("E").getTypeArguments(List).get("E") == numberType
            ce.findMethod("method4").get().getReturnType().getTypeArguments(List).get("E").getTypeArguments(List).get("E") == numberType

            ce.findMethod("method5").get().getGenericReturnType().getTypeArguments(List).get("E").getTypeArguments(List).get("E") == numberType
            ce.findMethod("method5").get().getReturnType().getTypeArguments(List).get("E").getTypeArguments(List).get("E") == numberType

            ce.findMethod("method6").get().getGenericReturnType().getTypeArguments("test.Test").get("T") == numberType
            ce.findMethod("method6").get().getReturnType().getTypeArguments("test.Test").get("T") == numberType

            ce.findMethod("method7").get().getGenericReturnType().getTypeArguments("test.Test").get("T") == numberType
            ce.findMethod("method7").get().getReturnType().getTypeArguments("test.Test").get("T") == numberType

            ce.findMethod("method8").get().getGenericReturnType().getTypeArguments("test.Test").get("T") == numberType
            ce.findMethod("method8").get().getReturnType().getTypeArguments("test.Test").get("T") == numberType

            ce.findMethod("method9").get().getGenericReturnType().getTypeArguments("test.Test").get("T") == numberType
            ce.findMethod("method9").get().getReturnType().getTypeArguments("test.Test").get("T") == numberType

            ce.findMethod("method10").get().getGenericReturnType().getTypeArguments("test.Test").get("T") == numberType
            ce.findMethod("method10").get().getReturnType().getTypeArguments("test.Test").get("T") == numberType
    }

    void "test inherit parameter annotation"() {
        ClassElement ce = buildClassElement('''
package test;
import java.util.List;

interface MyApi {

    String get(@io.micronaut.inject.visitor.MyParameter('X-username') String username)
}

class UserController implements MyApi {

    @Override
    String get(String username) {
    }

}

''')
        expect:
            ce.findMethod("get").get().getParameters()[0].hasAnnotation(MyParameter)
            ce.getMethods().get(0).overriddenMethods.size() == 1
    }

    void "test how the annotations from the type are propagated"() {
        given:
            ClassElement ce = buildClassElement('''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;
import java.util.List;
import io.micronaut.inject.visitor.Book;

@jakarta.inject.Singleton
class MyBean {

    @Executable
    public void saveAll(List<Book> books) {
    }

    @Executable
    public <T extends Book> void saveAll2(List<? extends T> book) {
    }

    @Executable
    public <T extends Book> void saveAll3(List<T> book) {
    }

    @Executable
    public void save2(Book book) {
    }

    @Executable
    public <T extends Book> void save3(T book) {
    }

    @Executable
    public Book get() {
        return null;
    }
}

''')
        when:
            def saveAll = ce.findMethod("saveAll").get()
            def listTypeArgument = saveAll.getParameters()[0].getType().getTypeArguments(List).get("E")
        then:
            listTypeArgument.hasAnnotation(MyEntity)
            listTypeArgument.hasAnnotation(Introspected.class)

        when:
            def saveAll2 = ce.findMethod("saveAll2").get()
            def listTypeArgument2 = saveAll2.getParameters()[0].getType().getTypeArguments(List).get("E")
        then:
            listTypeArgument2.hasAnnotation(MyEntity.class)
            listTypeArgument2.hasAnnotation(Introspected.class)

        when:
            def saveAll3 = ce.findMethod("saveAll3").get()
            def listTypeArgument3 = saveAll3.getParameters()[0].getType().getTypeArguments(List).get("E")
        then:
            listTypeArgument3.hasAnnotation(MyEntity.class)
            listTypeArgument3.hasAnnotation(Introspected.class)

        when:
            def save2 = ce.findMethod("save2").get()
            def parameter2 = save2.getParameters()[0].getType()
        then:
            parameter2.hasAnnotation(MyEntity.class)
            parameter2.hasAnnotation(Introspected.class)

        when:
            def save3 = ce.findMethod("save3").get()
            def parameter3 = save3.getParameters()[0].getType()
        then:
            parameter3.hasAnnotation(MyEntity.class)
            parameter3.hasAnnotation(Introspected.class)

        when:
            def get = ce.findMethod("get").get()
            def returnType = get.getReturnType()
        then:
            returnType.hasAnnotation(MyEntity.class)
            returnType.hasAnnotation(Introspected.class)
    }

    void "test alias for recursion"() {
        given:
            ClassElement ce = buildClassElement('''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Singleton;
import jakarta.inject.Inject;
import java.util.List;
import java.lang.Integer;

interface MathService {

    Integer compute(Integer num);
}

@Singleton
class MathServiceImpl implements MathService {

    @Override
    Integer compute(Integer num) {
        return num * 4 // should never be called
    }
}

@Singleton
class MathInnerServiceSpec {

    @Inject
    MathService mathService

    @MockBean(MathService)
    static class MyMock implements MathService {

        @Override
        Integer compute(Integer num) {
            return 50
        }
    }
}

''')
        when:
            def replaces = ce.getAnnotation(Replaces)
        then:
            replaces.stringValue("bean").get() == "test.MathService"
    }

    void "test how the type annotations from the type are propagated"() {
        given:
            ClassElement ce = buildClassElement('''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;
import java.util.List;
import io.micronaut.inject.visitor.Book;
import io.micronaut.inject.visitor.TypeUseRuntimeAnn;

@jakarta.inject.Singleton
class MyBean {

    @Executable
    public void saveAll(List<@TypeUseRuntimeAnn Book> books) {
    }

    @Executable
    public <@TypeUseRuntimeAnn T extends Book> void saveAll2(List<? extends T> book) {
    }

    @Executable
    public <@TypeUseRuntimeAnn T extends Book> void saveAll3(List<T> book) {
    }

    @Executable
    public <T extends Book> void saveAll4(List<@TypeUseRuntimeAnn ? extends T> book) {
    }

    @Executable
    public <T extends Book> void saveAll5(List<? extends @TypeUseRuntimeAnn T> book) {
    }

    @Executable
    public void save2(@TypeUseRuntimeAnn Book book) {
    }

    @Executable
    public <@TypeUseRuntimeAnn T extends Book> void save3(T book) {
    }

    @Executable
    public <T extends @TypeUseRuntimeAnn Book> void save4(T book) {
    }

    @Executable
    public <T extends Book> void save5(@TypeUseRuntimeAnn T book) {
    }

    @TypeUseRuntimeAnn
    @Executable
    public Book get() {
        return null;
    }
}

''')
        when:
            def saveAll = ce.findMethod("saveAll").get()
            def listTypeArgument = saveAll.getParameters()[0].getGenericType().getTypeArguments(List).get("E")
        then:
            validateBookArgument(listTypeArgument)

        when:
            def saveAll2 = ce.findMethod("saveAll2").get()
            def listTypeArgument2 = saveAll2.getParameters()[0].getGenericType().getTypeArguments(List).get("E")
        then:
            validateBookArgument(listTypeArgument2)

//        when:
//            def saveAll3 = ce.findMethod("saveAll3").get()
//            def listTypeArgument3 = saveAll3.getParameters()[0].getGenericType().getTypeArguments(List).get("E")
//        then:
//            validateBookArgument(listTypeArgument3)

        when:
            def saveAll4 = ce.findMethod("saveAll4").get()
            def listTypeArgument4 = saveAll4.getParameters()[0].getGenericType().getTypeArguments(List).get("E")
        then:
            validateBookArgument(listTypeArgument4)

//        when:
//            def saveAll5 = ce.findMethod("saveAll5").get()
//            def listTypeArgument5 = saveAll5.getParameters()[0].getGenericType().getTypeArguments(List).get("E")
//        then:
//            validateBookArgument(listTypeArgument5)

        when:
            def save2 = ce.findMethod("save2").get()
            def parameter2 = save2.getParameters()[0].getGenericType()
        then:
            validateBookArgument(parameter2)

        when:
            def save3 = ce.findMethod("save3").get()
            def parameter3 = save3.getParameters()[0].getGenericType()
        then:
            validateBookArgument(parameter3)

        when:
            def save4 = ce.findMethod("save4").get()
            def parameter4 = save4.getParameters()[0].getGenericType()
        then:
            validateBookArgument(parameter4)

//        when:
//            def save5 = ce.findMethod("save5").get()
//            def parameter5 = save5.getParameters()[0].getGenericType()
//        then:
//            validateBookArgument(parameter5)

        when:
            def get = ce.findMethod("get").get()
            def returnType = get.getGenericReturnType()
        then:
            validateBookArgument(returnType)
    }

    @PendingFeature
    void "test how the type annotations from the type are propagated - not working case"() {
        given:
            ClassElement ce = buildClassElement('''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;
import java.util.List;
import io.micronaut.inject.visitor.Book;
import io.micronaut.inject.visitor.TypeUseRuntimeAnn;

@jakarta.inject.Singleton
class MyBean {

    @Executable
    public <@TypeUseRuntimeAnn T extends Book> void saveAll3(List<T> book) {
    }
}

''')

        when:
            def saveAll3 = ce.findMethod("saveAll3").get()
            def listTypeArgument3 = saveAll3.getParameters()[0].getGenericType().getTypeArguments(List).get("E")
        then:
            validateBookArgument(listTypeArgument3)
    }

    @PendingFeature
    void "test how the type annotations from the type are propagated 2"() {
        given:
            ClassElement ce = buildClassElement('''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;
import java.util.List;
import io.micronaut.inject.visitor.Book;
import io.micronaut.inject.visitor.TypeUseRuntimeAnn;

@jakarta.inject.Singleton
class MyBean {

    @Executable
    public <T extends Book> void saveAll5(List<? extends @TypeUseRuntimeAnn T> book) {
    }

    @Executable
    public <T extends Book> void save5(@TypeUseRuntimeAnn T book) {
    }

}

''')
        when:
            def saveAll5 = ce.findMethod("saveAll5").get()
            def listTypeArgument5 = saveAll5.getParameters()[0].getGenericType().getTypeArguments(List).get("E")
        then:
            validateBookArgument(listTypeArgument5)

        when:
            def save5 = ce.findMethod("save5").get()
            def parameter5 = save5.getParameters()[0].getGenericType()
        then:
            validateBookArgument(parameter5)
    }

    void "test interface placeholder"() {
        ClassElement ce = buildClassElement('''
package test;
import java.util.List;

interface Repo<E, ID> extends GenericRepository<E, ID> {
}

interface GenericRepository<E, ID> {
}


class MyBean {
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

class MyRepo implements Repo<MyBean, Long> {
}

''')

        when:
            def repo = ce.getTypeArguments("test.Repo")
        then:
            repo.get("E").simpleName == "MyBean"
            repo.get("E").getMethods().size() == 2
            repo.get("E").getFields().size() == 1
        when:
            def genRepo = ce.getTypeArguments("test.GenericRepository")
        then:
            genRepo.get("E").simpleName == "MyBean"
            genRepo.get("E").getMethods().size() == 2
            genRepo.get("E").getFields().size() == 1
        when:
            def interfaces = ce.getInterfaces()
        then:
            interfaces.size() == 1
            interfaces[0].simpleName == "Repo"
    }

    void "test interface type annotations"() {
        ClassElement ce = buildClassElement('test.MyRepo', '''
package test;
import jakarta.validation.Valid;
import java.util.List;

interface MyRepo extends Repo<@Valid MyBean, Long> {
}

interface Repo<E, ID> extends GenericRepository<E, ID> {

    void save(E entity);

}

interface GenericRepository<E, ID> {
}


class MyBean {
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

''')

        when:
            def method = ce.findMethod("save").get()
            def type = method.parameters[0].getGenericType()
        then:
            type.hasAnnotation(Valid)
    }

    void validateBookArgument(ClassElement classElement) {
        // The class element should have all the annotations present
        assert classElement.hasAnnotation(TypeUseRuntimeAnn.class)
        assert classElement.hasAnnotation(MyEntity.class)
        assert classElement.hasAnnotation(Introspected.class)

        def typeAnnotationMetadata = classElement.getTypeAnnotationMetadata()
        // The type annotations should have only type annotations
        assert typeAnnotationMetadata.hasAnnotation(TypeUseRuntimeAnn.class)
        assert !typeAnnotationMetadata.hasAnnotation(MyEntity.class)
        assert !typeAnnotationMetadata.hasAnnotation(Introspected.class)

        // Get the actual type -> the type shouldn't have any type annotations
        def type = classElement.getType()
        assert !type.hasAnnotation(TypeUseRuntimeAnn.class)
        assert type.hasAnnotation(MyEntity.class)
        assert type.hasAnnotation(Introspected.class)
        assert type.getTypeAnnotationMetadata().isEmpty()
    }

    private void assertListGenericArgument(ClassElement type, Closure cl) {
        def arg1 = type.getAllTypeArguments().get(List.class.name).get("E")
        def arg2 = type.getAllTypeArguments().get(Collection.class.name).get("E")
        def arg3 = type.getAllTypeArguments().get(Iterable.class.name).get("T")
        cl.call(arg1)
        cl.call(arg2)
        cl.call(arg3)
    }

}
