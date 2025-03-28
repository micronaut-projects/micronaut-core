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

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.annotation.processing.visitor.JavaClassElement
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.exceptions.BeanContextException
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.Introspected
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ConstructorElement
import io.micronaut.inject.ast.Element
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
import io.micronaut.inject.ast.WildcardElement
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Null
import java.sql.SQLException
import java.util.function.Supplier
import spock.lang.IgnoreIf
import spock.lang.Issue
import spock.lang.Unroll
import spock.util.environment.Jvm

class ClassElementSpec extends AbstractTypeElementSpec {

    void "test duplicate methods"() {
        expect:
        buildClassElement('''
package annbinding1;

import java.lang.annotation.*;

class MyBean implements Middle<String> {
    void test() {
    }
}

interface Middle<ParentIdT> extends Parent<ParentIdT> {
    @Override
    default String updateResource(String request, ParentIdT parentId) {
        return Parent.super.updateResource(request,parentId);
    }
}
interface Parent<ParentIdT> {
    default String updateResource(
            String request,
            ParentIdT parentId) {
        return "ok";
    }
}
''') { ClassElement classElement ->
            assert classElement.getMethods().collect { it.name } == ["test", "updateResource"]
            classElement
        }
    }

    void "test canonical name"() {
        expect:
        buildClassElement('''
package test;

record Outer() {
    record Inner() {}
}
''') { ClassElement classElement ->
            assert classElement.canonicalName == 'test.Outer'
            assert classElement.getEnclosedElement(ElementQuery.ALL_INNER_CLASSES).get().canonicalName == 'test.Outer.Inner'
            classElement
        }
    }

    void "test package-private methods with broken different package"() {
        when:
        ClassElement classElement = buildClassElement('''
package test.another;

import test.Middle;

class Test extends Middle {
   private boolean testInjected;
    void injectPackagePrivateMethod() {
        testInjected = true;
    }
}

''')

            def elements = classElement.getEnclosedElements(ElementQuery.ALL_METHODS)
        then: "A special case for the identical methods with package-private access and broken package access in between"
            elements.size() == 2
    }

    void "test class element generics"() {
        given:
        ClassElement classElement = buildClassElement('''
package ast.test;

import java.util.*;

final class Test extends Parent<String> implements One<String> {
    Test(String constructorProp) {
        super(constructorProp);
    }
}

abstract class Parent<T extends CharSequence> extends java.util.AbstractCollection<T> {
    private final T parentConstructorProp;
    private T conventionProp;

    Parent(T parentConstructorProp) {
        this.parentConstructorProp = parentConstructorProp;
    }

    public void setConventionProp(T conventionProp) {
        this.conventionProp = conventionProp;
    }
    public T getConventionProp() {
        return conventionProp;
    }
    public T getParentConstructorProp() {
        return parentConstructorProp;
    }

    @Override public int size() {
        return 0;
    }

    @Override public Iterator<T> iterator() {
        return null;
    }

    public T publicFunc(T name) {
        return name;
    }

    public T parentFunc(T name) {
        return name;
    }

}

interface One<E> {}
''')
        List<PropertyElement> propertyElements = classElement.getBeanProperties()
        List<MethodElement> methodElements = classElement.getEnclosedElements(ElementQuery.ALL_METHODS)
        Map<String, MethodElement> methodMap = methodElements.collectEntries {
            [it.name, it]
        }
        Map<String, PropertyElement> propMap = propertyElements.collectEntries {
            [it.name, it]
        }

        expect:
        methodMap['add'].parameters[0].genericType.simpleName == 'String'
        methodMap['add'].parameters[0].type.simpleName == 'Object'
        methodMap['iterator'].returnType.firstTypeArgument.get().simpleName == 'CharSequence' // why?
        methodMap['iterator'].genericReturnType.firstTypeArgument.get().simpleName == 'String'
        methodMap['stream'].returnType.firstTypeArgument.get().simpleName == 'Object'
        methodMap['stream'].genericReturnType.firstTypeArgument.get().simpleName == 'String'
        propMap['conventionProp'].type.simpleName == 'String'
        propMap['conventionProp'].genericType.simpleName == 'String'
        propMap['conventionProp'].genericType.simpleName == 'String'
        propMap['conventionProp'].readMethod.get().returnType.simpleName == 'CharSequence'
        propMap['conventionProp'].readMethod.get().genericReturnType.simpleName == 'String'
        propMap['conventionProp'].writeMethod.get().parameters[0].type.simpleName == 'CharSequence'
        propMap['conventionProp'].writeMethod.get().parameters[0].genericType.simpleName == 'String'
        propMap['parentConstructorProp'].type.simpleName == 'String'
        propMap['parentConstructorProp'].genericType.simpleName == 'String'
        methodMap['parentFunc'].returnType.simpleName == 'CharSequence'
        methodMap['parentFunc'].genericReturnType.simpleName == 'String'
        methodMap['parentFunc'].parameters[0].type.simpleName == 'CharSequence'
        methodMap['parentFunc'].parameters[0].genericType.simpleName == 'String'
    }

    void "test class element generics - records"() {
        given:
        ClassElement classElement = buildClassElement('''
package ast.test;

import org.jetbrains.annotations.NotNull;
import java.util.*;

record Test(String constructorProp) implements Parent<String>, One<String> {
    @Override public String publicFunc(String name) {
        return null;
    }
    @Override public int size() {
    return 0;
    }
    @Override public boolean isEmpty() {
        return false;
    }
    @Override public boolean contains(Object o) {
        return false;
    }
    @NotNull @Override public Iterator<String> iterator() {
        return null;
    }
    @NotNull@Override public Object[] toArray() {
        return new Object[0];
    }
    @NotNull@Override public<T> T[] toArray(@NotNull T[] a) {
        return null;
    }
    @Override public boolean add(String s) {
        return false;
    }
    @Override public boolean remove(Object o) {
        return false;
    }
    @Override public boolean containsAll(@NotNull Collection<?> c) {
        return false;
    }
    @Override public boolean addAll(@NotNull Collection<? extends String> c) {
        return false;
    }
    @Override public boolean addAll(int index,@NotNull Collection<? extends String> c) {
        return false;
    }
    @Override public boolean removeAll(@NotNull Collection<?> c) {
        return false;
    }
    @Override public boolean retainAll(@NotNull Collection<?> c) {
        return false;
    }
    @Override public void clear() {

    }
    @Override public void add(int index, String element) {


    }@Override public String remove(int index) {
        return null;
    }
    @Override public int indexOf(Object o) {
        return 0;
    }
    @Override public int lastIndexOf(Object o) {
        return 0;
    }
    @NotNull @Override public ListIterator<String> listIterator() {
        return null;
    }
    @NotNull @Override public ListIterator<String> listIterator(int index) {
        return null;
    }
    @NotNull @Override public List<String> subList(int fromIndex, int toIndex) {
        return null;
    }
}

interface Parent<T extends CharSequence> extends java.util.List<T> {

    public T constructorProp();

    public T publicFunc(T name);

    default T parentFunc(T name) {
        return name;
    }

    @Override default T get(int index) {
        return null;
    }
    @Override default T set(int index, T element) {
        return null;
    }
}

interface One<E> {}
''')
        List<PropertyElement> propertyElements = classElement.getBeanProperties()
        List<MethodElement> methodElements = classElement.getEnclosedElements(ElementQuery.ALL_METHODS)
        Map<String, MethodElement> methodMap = methodElements.collectEntries {
            [it.name, it]
        }
        Map<String, PropertyElement> propMap = propertyElements.collectEntries {
            [it.name, it]
        }

        expect:
        methodMap['add'].parameters[1].genericType.simpleName == 'String'
        methodMap['add'].parameters[1].type.simpleName == 'String'
        methodMap['iterator'].returnType.firstTypeArgument.get().simpleName == 'String'
        methodMap['iterator'].genericReturnType.firstTypeArgument.get().simpleName == 'String'
        methodMap['stream'].returnType.firstTypeArgument.get().simpleName == 'Object'
        methodMap['stream'].genericReturnType.firstTypeArgument.get().simpleName == 'String'
        propMap['constructorProp'].readMethod.get().returnType.simpleName == 'String'
        propMap['constructorProp'].readMethod.get().genericReturnType.simpleName == 'String'
        propMap['constructorProp'].type.simpleName == 'String'
        propMap['constructorProp'].genericType.simpleName == 'String'
        methodMap['parentFunc'].returnType.simpleName == 'CharSequence'
        methodMap['parentFunc'].genericReturnType.simpleName == 'String'
        methodMap['parentFunc'].parameters[0].type.simpleName == 'CharSequence'
        methodMap['parentFunc'].parameters[0].genericType.simpleName == 'String'
    }

    void "test equals with primitive"() {
        given:
        def element = buildClassElement("""
package test;

class Test {
    boolean test1;
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

    void "test primitive nullability"() {
        given:
        def element = buildClassElement("""
package test;

class Test {
    boolean test1;
    boolean[] test2;
    void method1() {
    }
    boolean method2() {
        return true;
    }
}
""")

        expect:
        element.getFields().get(0).getType().isNonNull()
        !element.getFields().get(0).getType().isNullable()

        !element.getFields().get(1).getType().isNonNull()
        !element.getFields().get(1).getType().isNullable()

        !element.getMethods().get(0).getReturnType().isNonNull()
        !element.getMethods().get(0).getReturnType().isNullable()

        !element.getMethods().get(0).getReturnType().getType().isNonNull()
        !element.getMethods().get(0).getReturnType().getType().isNullable()

        element.getMethods().get(1).getReturnType().isNonNull()
        !element.getMethods().get(1).getReturnType().isNullable()

        element.getMethods().get(1).getReturnType().getType().isNonNull()
        !element.getMethods().get(1).getReturnType().getType().isNullable()
    }

    void "test resolve receiver type on method"() {
        given:
        def element = buildClassElement("""
package receivertypetest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

class Test {
    Test() {}

    void instance(@SomeAnn Test this) {}
    static void staticMethod() {}

    class Inner {
        Inner(@SomeAnn Test Test.this) {}
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
@interface SomeAnn {

}
""")

        ConstructorElement constructorElement =
                element.getEnclosedElement(ElementQuery.of(ConstructorElement)).get()
        MethodElement instanceMethod = element
                .getEnclosedElement(ElementQuery.ALL_METHODS.named(n -> n == 'instance')).get()
        MethodElement staticMethod = element
                .getEnclosedElement(ElementQuery.ALL_METHODS.named(n -> n == 'staticMethod')).get()
        ClassElement innerClass = element.getEnclosedElement(ElementQuery.ALL_INNER_CLASSES).get()
        MethodElement innerConstructor = innerClass.getEnclosedElement(ElementQuery.of(ConstructorElement)).get()

        expect:
        innerConstructor.receiverType.isPresent()
        innerConstructor.receiverType.get().hasAnnotation('receivertypetest.SomeAnn')
        !constructorElement.receiverType.isPresent()
        instanceMethod.receiverType.isPresent()
        instanceMethod.receiverType.get().hasAnnotation('receivertypetest.SomeAnn')
        !staticMethod.receiverType.isPresent()
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

    void "test annotate applies transformations"() {
        when:
        def element = buildClassElement('''
package anntransform;

class Test {
}
''')
        then:
        !element.hasAnnotation(AnnotationUtil.SINGLETON)

        when:
        element.annotate(javax.inject.Singleton)

        then:
        !element.hasAnnotation(javax.inject.Singleton)
        element.hasAnnotation(AnnotationUtil.SINGLETON)
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
        pe.getClass().name.contains("JavaPackageElement")
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
        pe.getClass().name.contains("JavaPackageElement")
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/5611')
    void 'test visit enum with custom annotation'() {
        when: "An enum has an annotation that is visited by CustomAnnVisitor"
        def context = buildContext('''
package test;

@io.micronaut.visitors.CustomAnn
enum EnumTest {

}
''')

        then: "No compilation error occurs"
        context != null

        cleanup:
        context.close()
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
        when: "all methods are retrieved"
        def allMethods = classElement.getEnclosedElements(ElementQuery.ALL_METHODS)

        then: "All methods, including non-accessible are returned but not overridden"
        allMethods.size() == 10

        when: "only abstract methods are requested"
        def abstractMethods = classElement.getEnclosedElements(ElementQuery.ALL_METHODS.onlyAbstract())

        then: "The result is correct"
        abstractMethods*.name as Set == ['unimplementedItfeMethod', 'unimplementedSuperMethod', 'unimplementedMethod'] as Set

        when: "only concrete methods are requested"
        def concrete = classElement.getEnclosedElements(ElementQuery.ALL_METHODS.onlyConcrete().onlyAccessible())

        then: "The result is correct"
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

        then: "The declared are correct"
        declared.size() == 4
        declared*.name as Set == ['privateMethod', 'packagePrivateMethod', 'publicMethod', 'staticMethod'] as Set

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

    void "test find matching constructors using ElementQuery"() {
        given:
        ClassElement classElement = buildClassElement('''
package elementquery;

class Test extends SuperType {
    static {}

    Test() {}

    Test(int i) {}
}

class SuperType {
    static {}

    SuperType() {}

    SuperType(String s) {}
}
''')
        when:
        def constructors = classElement.getEnclosedElements(ElementQuery.CONSTRUCTORS)

        then: "only our own instance constructors"
        constructors.size() == 2

        when:
        def allConstructors = classElement.getEnclosedElements(ElementQuery.of(ConstructorElement.class))

        then: "superclass constructors, but not including static initializers"
        allConstructors.size() == 4
    }

    void "test visit inherited controller classes"() {
        buildBeanDefinition('test.TestController', '''
package test;

import io.micronaut.http.annotation.*;
import jakarta.inject.Inject;

@Controller("/test")
public class TestController extends BaseTestController {

    @Get("/getMethod")
    public String getMethod(int[] argument) {
        return null;
    }

    @Get("/hello/annotinbase")
    @Override
    public String baseOverrideAnnotInBase(String name) {
        return name;
    }

    @Get("/noannotinbase")
    @Override
    public String baseOverrideNoAnnotInBase(String name) {
        return name;
    }

    @Get("/hellohello")
    @Override
    public String hellohello(String name) {
        return name;
    }

}

class BaseTestController extends Base {

    public String hello(String name) {
        return name;
    }

    @Get("/base")
    public String base(String name) {
        return name;
    }

    public String baseOverrideAnnotInBase(String name) {
        return name;
    }

    @Get("/noannotinbase")
    public String baseOverrideNoAnnotInBase(String name) {
        return name;
    }

}

class Base {

    public String hellohello(String name) {
        return name;
    }
}

class B {

    @Get("/b")
    public String b(String name) {
        return name;
    }

}
''')
        expect:
        AllElementsVisitor.VISITED_CLASS_ELEMENTS.size() == 1
        AllElementsVisitor.VISITED_METHOD_ELEMENTS.size() == 6
        ControllerGetVisitor.VISITED_METHOD_ELEMENTS.size() == 5
    }

    void "test visit methods that take and return arrays"() {
        buildBeanDefinition('test.TestController', '''
package test;

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
        buildBeanDefinition('test.TestController', '''
package test;

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
        buildBeanDefinition('test.TestController', '''
package test;

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
        buildBeanDefinition('test.TestController', '''
package test;

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
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].returnType.name == 'test.Foo'
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].parameters.size() == 1
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].parameters[0].type.name == 'test.Foo'
    }

    // Java 9+ doesn't allow resolving elements was the compiler
    // is finished being used so this test cannot be made to work beyond Java 8 the way it is currently written
    @IgnoreIf({ Jvm.current.isJava9Compatible() })
    void "test resolve generic type using getTypeArguments"() {
        buildBeanDefinition('test.TestController', '''
package test;

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
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0].getTypeArguments(Supplier).get("T").name == String.name
    }

    void "test array generic types at type level"() {
        buildBeanDefinition('test.TestController', '''
package test;

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
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].returnType.name == 'test.Foo'
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].parameters.size() == 1
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].parameters[0].type.name == 'test.Foo'
    }

    void "test generic types at type level used as type arguments"() {
        buildBeanDefinition('test.TestController', '''
package test;

import io.micronaut.http.annotation.*;
import jakarta.inject.Inject;

@Controller("/test")
public class TestController<MT extends Foo> {

    @Get("/getMethod")
    public java.util.List<MT> getMethod(java.util.Set<MT> argument) {
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

    void "test JavaClassElement.getSuperType() with generic types"() {
        given:
        buildBeanDefinition('test.MyBean', '''
package test;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.MathContext;
import static java.math.RoundingMode.HALF_UP;
import io.micronaut.http.annotation.*;

class Quantity<Q extends Quantity, U extends Unit> implements Serializable {
    private static final long serialVersionUID = -9000608810227353935L;
    private final BigDecimal amount;
    private final U unit;

    Quantity(BigDecimal amount, U unit) {
        this.amount = amount;
        this.unit = unit;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public U getUnit() {
        return unit;
    }

    public void setUnit(U unit) {
        throw new UnsupportedOperationException("Quantities can't change");
    }

    public void setAmount(BigDecimal amount) {
        throw new UnsupportedOperationException("Quantities can't change");
    }
}

enum TimeUnit implements Unit {
    Millisecond(BigDecimal.ONE.divide(BigDecimal.valueOf(1000), MATH_CONTEXT), "ms"),
    Second(BigDecimal.ONE, "s"),
    Minute(BigDecimal.valueOf(60), Second, "m"),
    Hour(BigDecimal.valueOf(60), Minute, "h"),
    Day(BigDecimal.valueOf(24), Hour, "d"),
    Week(BigDecimal.valueOf(7), Day, "w");

    private final BigDecimal ratio;
    private final String suffix;

    TimeUnit(BigDecimal ratio, String suffix) {
        this.ratio = ratio;
        this.suffix = suffix;
    }

    TimeUnit(BigDecimal factor, TimeUnit base, String suffix) {
        this.ratio = factor.multiply(base.ratio);
        this.suffix = suffix;
    }

    @Override public BigDecimal ratio() {
        return ratio;
    }

    @Override public String suffix() {
        return suffix;
    }
}

interface Unit {
    MathContext MATH_CONTEXT = new MathContext(16, HALF_UP);

    String name();

    BigDecimal ratio();

    String suffix();
}

@Controller
class Time extends Quantity<Time, TimeUnit> {

    private Time(BigDecimal amount, TimeUnit unit) {
        super(amount, unit);
    }

    public static Time of(BigDecimal amount, TimeUnit unit) {
        return new Time(amount, unit);
    }

    @Override
    public BigDecimal getAmount() {
        return super.getAmount();
    }

    @Override
    public TimeUnit getUnit() {
        return super.getUnit();
    }

    @Override
    public void setAmount(BigDecimal amount) {
        super.setAmount(amount);
    }

    @Override
    public void setUnit(TimeUnit unit) {
        super.setUnit(unit);
    }
}

@jakarta.inject.Singleton
class MyBean {}
''')

        expect:
        AllElementsVisitor.VISITED_CLASS_ELEMENTS.size() == 1
        AllElementsVisitor.VISITED_CLASS_ELEMENTS[0] instanceof JavaClassElement

        when:
        JavaClassElement time = (JavaClassElement) AllElementsVisitor.VISITED_CLASS_ELEMENTS[0]

        then:
        time.getSuperType()
        time.getSuperType().isPresent()

        when:
        JavaClassElement superType = time.getSuperType().get()

        then:
        superType.getTypeArguments().size() == 2
        superType.getTypeArguments().get('Q').getName() == 'test.Time'
        superType.getTypeArguments().get('U').getName() == 'test.TimeUnit'

    }

    void "test field type with generic in inner class"() {
        given:
        buildClassElement("""
package test;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import java.util.List;
import java.util.Locale;

@Controller("/test")
class TestController {
    @Get
    public HttpResponse<ResponseObject<List<Dto>>> endpoint() {
        return null;
    }

    public static class ResponseObject<T> {

        public T body;
    }

    public static class Dto {

        public Locale locale;
    }
}
""") { ClassElement ce ->

            def responseType = ce.methods[0].returnType

            responseType.type.name == 'io.micronaut.http.HttpResponse'
            assert responseType.typeArguments
            assert responseType.typeArguments.size() == 1

            def typeArg = responseType.firstTypeArgument.orElse(null)
            assert typeArg
            assert typeArg.fields
            assert typeArg.fields.size() == 1

            def bodyField = typeArg.fields[0]
            assert bodyField.name == "body"
            assert bodyField.genericType.name == "java.util.List"
            assert bodyField.genericType.getFirstTypeArgument().get().name == 'test.TestController$Dto'
        }
    }

    void "test field type with generic in inner super class"() {
        given:
        buildClassElement("""
package test;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import java.util.List;
import java.util.Locale;

@Controller("/test")
class TestController {
    @Get
    public HttpResponse<ResponseObject<List<Dto>>> endpoint() {
        return null;
    }

    public static class BaseResponseObject<T> {

        public T body;
    }

    public static class ResponseObject<T> extends BaseResponseObject<T> {
    }

    public static class Dto {

        public Locale locale;
    }
}
""") { ClassElement ce ->

            def responseType = ce.methods[0].returnType

            responseType.type.name == 'io.micronaut.http.HttpResponse'
            assert responseType.typeArguments
            assert responseType.typeArguments.size() == 1

            def typeArg = responseType.firstTypeArgument.orElse(null)
            assert typeArg
            assert typeArg.fields
            assert typeArg.fields.size() == 1

            def bodyField = typeArg.fields[0]
            assert bodyField.name == "body"
            assert bodyField.genericType.name == "java.util.List"
            assert bodyField.genericType.getFirstTypeArgument().get().name == 'test.TestController$Dto'
        }
    }

    @Issue('https://github.com/micronaut-projects/micronaut-openapi/issues/593')
    void 'test declaringType is the implementation and not the interface'() {
        given:
        def element = buildClassElement('''
package test;

import java.util.List;
import java.time.Instant;

class MyDtoImpl implements MyDto<Long> {

    private Long id;
    private Instant version;

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public void setId(Long id) {
        this.id = id;
    }

    @Override
    public Instant getVersion() {
        return version;
    }

    public void setVersion(Instant version) {
        this.version = version;
    }
}

interface MyDto<ID> {
    ID getId();
    void setId(ID id);

    Instant getVersion();
    void setVersion(Instant version);
}
''')

        when:
        def beanProperties = element.getBeanProperties()

        then: 'the declaring type is the implementation, not the interface'
        beanProperties
        beanProperties.size() == 2
        beanProperties*.declaringType*.name.every { it == 'test.MyDtoImpl' }
    }

    @Issue('https://github.com/micronaut-projects/micronaut-openapi/issues/593')
    void 'test bean properties defined with accessors style none'() {
        given: 'a POJO with Lombok accessors annotated with @AccessorsStyle NONE'
        def element = buildClassElement('''
package test;

import io.micronaut.core.annotation.AccessorsStyle;

@AccessorsStyle(readPrefixes = "", writePrefixes = "")
class Person {

    private String name;
    private Integer debtValue;
    private Integer totalGoals;

    public Person(String name, Integer debtValue, Integer totalGoals) {
        this.name = name;
        this.debtValue = debtValue;
        this.totalGoals = totalGoals;
    }

    public String name() {
        return name;
    }
    public Integer debtValue() {
        return debtValue;
    }
    public Integer totalGoals() {
        return totalGoals;
    }

    public void name(String name) {
        this.name = name;
    }
    public void debtValue(Integer debtValue) {
        this.debtValue = debtValue;
    }
    public void totalGoals(Integer totalGoals) {
        this.totalGoals = totalGoals;
    }
}
''')

        when: 'getting the bean properties'
        def beanProperties = element.getBeanProperties()

        then: 'the bean properties are found'
        beanProperties
        beanProperties.size() == 3

        when: 'defining the class without @AccessorsStyle annotation'
        element = buildClassElement('''
package test;

class Person {

    private String name;
    private Integer debtValue;
    private Integer totalGoals;

    public Person(String name, Integer debtValue, Integer totalGoals) {
        this.name = name;
        this.debtValue = debtValue;
        this.totalGoals = totalGoals;
    }

    public String name() {
        return name;
    }
    public Integer debtValue() {
        return debtValue;
    }
    public Integer totalGoals() {
        return totalGoals;
    }

    public void name(String name) {
        this.name = name;
    }
    public void debtValue(Integer debtValue) {
        this.debtValue = debtValue;
    }
    public void totalGoals(Integer totalGoals) {
        this.totalGoals = totalGoals;
    }
}
''')

        then: 'no bean properties are found'
        !element.getBeanProperties()
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

    @Issue("https://github.com/eclipse-ee4j/cdi-tck/blob/master/lang-model/src/main/java/org/jboss/cdi/lang/model/tck/InheritedMethods.java")
    // private static Since Java 9
    void "test inherited methods using ElementQuery"() {
        given:
        JavaClassElement classElement = buildClassElement('''
package elementquery;

class InheritedMethods extends SuperClassWithMethods implements SuperInterfaceWithMethods {
    @Override
    public String interfaceMethod1() {
        return null;
    }

    @Override
    public String interfaceMethod2() {
        return null;
    }

    public String instanceMethod3() {
        return null;
    }

}

interface SuperSuperInterfaceWithMethods {
    static String interfaceStaticMethod1() {
        return null;
    }

    private static String interfaceStaticMethod2() {
        return null;
    }

    String interfaceMethod1();

    default String interfaceMethod2() {
        return null;
    }

    private String interfaceMethod3() {
        return null;
    }
}

interface SuperInterfaceWithMethods extends SuperSuperInterfaceWithMethods {
    static String interfaceStaticMethod1() {
        return null;
    }

    private static String interfaceStaticMethod2() {
        return null;
    }

    @Override
    String interfaceMethod1();

    @Override
    default String interfaceMethod2() {
        return null;
    }

    private String interfaceMethod3() {
        return null;
    }
}

class SuperSuperClassWithMethods {
    public static void staticMethod() {
    }

    private String instanceMethod1() {
        return null;
    }

    public String instanceMethod2() {
        return null;
    }
}

abstract class SuperClassWithMethods extends SuperSuperClassWithMethods implements SuperSuperInterfaceWithMethods {
    public static void staticMethod() {
    }

    private String instanceMethod1() {
        return null;
    }

    @Override
    public String instanceMethod2() {
        return null;
    }
}

''')
        when:
        List<MethodElement> methods = classElement.getEnclosedElements(ElementQuery.ALL_METHODS)
        List<String> expected = [
                "interfaceMethod1",
                "interfaceMethod2",
                "instanceMethod3",
                "staticMethod",
                "instanceMethod1",
                "instanceMethod2",
                "instanceMethod1",
                "interfaceStaticMethod1",
                "interfaceStaticMethod2",
                "interfaceMethod3",
                "interfaceStaticMethod1",
                "interfaceStaticMethod2",
                "interfaceMethod3"
        ]

        then:
        for (String name : methods*.name) {
            assert expected.contains(name)
        }
        expected.size() == methods.size()
        methods[0].overriddenMethods.size() == 0
        methods[1].overriddenMethods.size() == 0
        methods[2].overriddenMethods.size() == 0
        methods[3].overriddenMethods.size() == 1
        methods[4].overriddenMethods.size() == 0
        methods[5].overriddenMethods.size() == 0
        methods[6].overriddenMethods.size() == 0
        methods[7].overriddenMethods.size() == 2
        methods[8].overriddenMethods.size() == 2
        methods[9].overriddenMethods.size() == 0
        methods[10].overriddenMethods.size() == 0
        methods[11].overriddenMethods.size() == 0
        methods[12].overriddenMethods.size() == 0
        classElement.getEnclosedElements(ElementQuery.ALL_METHODS.includeOverriddenMethods()).size() == 13 + 1 + 2 + 2

        when:
        List<MethodElement> allMethods = classElement.getEnclosedElements(ElementQuery.ALL_METHODS.includeOverriddenMethods().includeHiddenElements())
        expected = [
                "interfaceMethod1",
                "interfaceMethod2",
                "instanceMethod3",
                "staticMethod",
                "instanceMethod1",
                "instanceMethod2",
                "staticMethod",
                "instanceMethod1",
                "instanceMethod2",
                "interfaceStaticMethod1",
                "interfaceStaticMethod2",
                "interfaceMethod1",
                "interfaceMethod2",
                "interfaceMethod3",
                "interfaceStaticMethod1",
                "interfaceStaticMethod2",
                "interfaceMethod1",
                "interfaceMethod2",
                "interfaceMethod3"
        ]

        then:
        for (String name : allMethods*.name) {
            assert expected.contains(name)
        }
        expected.size() == allMethods.size()

        and:
        assertMethodsByName(allMethods, "interfaceStaticMethod1", ["SuperSuperInterfaceWithMethods", "SuperInterfaceWithMethods"])
        assertMethodsByName(allMethods, "interfaceStaticMethod2", ["SuperSuperInterfaceWithMethods", "SuperInterfaceWithMethods"])
        assertMethodsByName(allMethods, "interfaceMethod1", ["SuperSuperInterfaceWithMethods", "SuperInterfaceWithMethods", "InheritedMethods"])
        assertMethodsByName(allMethods, "interfaceMethod2", ["SuperSuperInterfaceWithMethods", "SuperInterfaceWithMethods", "InheritedMethods"])
        assertMethodsByName(allMethods, "interfaceMethod3", ["SuperSuperInterfaceWithMethods", "SuperInterfaceWithMethods"])
        assertMethodsByName(allMethods, "staticMethod", ["SuperSuperClassWithMethods", "SuperClassWithMethods"])
        assertMethodsByName(allMethods, "instanceMethod1", ["SuperSuperClassWithMethods", "SuperClassWithMethods"])
        assertMethodsByName(allMethods, "instanceMethod2", ["SuperSuperClassWithMethods", "SuperClassWithMethods"])
        assertMethodsByName(allMethods, "instanceMethod3", ["InheritedMethods"])
    }

    private final static String FIELDS_SCENARIO = '''\
package elementquery;

class InheritedFields extends SuperClassWithFields implements SuperInterfaceWithFields {
    String instanceField3 = "";
}

interface SuperSuperInterfaceWithFields {
    String interfaceField = "";
}

interface SuperInterfaceWithFields extends SuperSuperInterfaceWithFields {
    String interfaceField = "";
}

class SuperSuperClassWithFields {
    static String instanceField1 = "";

    String instanceField2 = "";
}

abstract class SuperClassWithFields extends SuperSuperClassWithFields implements SuperSuperInterfaceWithFields {
    static String instanceField1 = "";

    String instanceField2 = "";
}
'''

    void "verify getEnclosedElements with ElementQuery.ALL_FIELDS"() {
        given:
        ClassElement classElement = buildClassElement(FIELDS_SCENARIO)
        when:
        List<FieldElement> fields = classElement.getEnclosedElements(ElementQuery.ALL_FIELDS)
        Map<String, List<String>> expected = [
                "instanceField1": ["SuperClassWithFields"],
                "instanceField2": ["SuperClassWithFields"],
                "instanceField3": ["InheritedFields"],
                "interfaceField": ["SuperInterfaceWithFields", "SuperSuperInterfaceWithFields"]
        ]

        then:
        fields.size() == expected.collect { k, v -> v.size() }.sum()
        expected.each { k, v ->
            assertFieldsByName(fields, k, v)
        }
    }

    void "verify getEnclosedElements with ElementQuery.ALL_FIELDS.includeHiddenElements"() {
        given:
        ClassElement classElement = buildClassElement(FIELDS_SCENARIO)

        when:
        List<FieldElement> fields = classElement.getEnclosedElements(ElementQuery.ALL_FIELDS.includeHiddenElements())
        Map<String, List<String>> expected = [
                "instanceField1": ["SuperClassWithFields", "SuperSuperClassWithFields"],
                "instanceField2": ["SuperClassWithFields", "SuperSuperClassWithFields"],
                "instanceField3": ["InheritedFields"],
                "interfaceField": ["SuperInterfaceWithFields", "SuperSuperInterfaceWithFields"]
        ]

        then:
        fields.size() == expected.collect { k, v -> v.size() }.sum()
        expected.each { k, v ->
            assertFieldsByName(fields, k, v)
        }
    }

    void "test first inner class not breaking method's owning and declaring class"() {
        buildBeanDefinition('test.TestController', '''
package test;

import io.micronaut.http.annotation.*;
import jakarta.inject.Inject;

@Controller("/test")
public class TestController {

    public static class SomeException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    @Get
    public String hello() {
        return "HW";
    }

}

''')
        expect:
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].owningType.name == 'test.TestController'
        AllElementsVisitor.VISITED_METHOD_ELEMENTS[0].declaringType.name == 'test.TestController'
    }

    void "test fields selection"() {
        given:
        ClassElement classElement = buildClassElement('''
package test;

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

    String packprivme;

    public static String PUB_CONST;

    private static String PRV_CONST;

    protected static String PROT_CONST;

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
        ClassElement classElement = buildClassElement('''
package test;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.annotation.*;
import jakarta.inject.Inject;

@Controller("/pets")
interface PetOperations<T extends Pet> {

    @Post("/")
    T save(String name, int age);
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

    void "test type annotations on a method and a field"() {
        ClassElement ce = buildClassElement('''
package test;

class Test {
    @io.micronaut.visitors.TypeUseRuntimeAnn
    @io.micronaut.visitors.TypeUseClassAnn
    String myField;

    @io.micronaut.visitors.TypeUseRuntimeAnn
    @io.micronaut.visitors.TypeUseClassAnn
    String myMethod() {
        return null;
    }
}
''')
        expect:
        def field = ce.findField("myField").get()
        def method = ce.findMethod("myMethod").get()

        // Type annotations shouldn't appear on the field
        field.getAnnotationMetadata().getAnnotationNames().asList() == []
        field.getType().getAnnotationMetadata().getAnnotationNames().asList() == ['io.micronaut.visitors.TypeUseRuntimeAnn', 'io.micronaut.visitors.TypeUseClassAnn' ]
        field.getGenericType().getAnnotationMetadata().getAnnotationNames().asList() == ['io.micronaut.visitors.TypeUseRuntimeAnn', 'io.micronaut.visitors.TypeUseClassAnn' ]
        // Type annotations shouldn't appear on the method
        method.getAnnotationMetadata().getAnnotationNames().asList() == []
        method.getReturnType().getAnnotationMetadata().getAnnotationNames().asList() == ['io.micronaut.visitors.TypeUseRuntimeAnn', 'io.micronaut.visitors.TypeUseClassAnn' ]
        method.getGenericReturnType().getAnnotationMetadata().getAnnotationNames().asList() == ['io.micronaut.visitors.TypeUseRuntimeAnn', 'io.micronaut.visitors.TypeUseClassAnn' ]
    }

    void "test type annotations on a method and a field 2"() {
        ClassElement ce = buildClassElement('''
package test;

class Test {
    @io.micronaut.visitors.TypeFieldRuntimeAnn
    @io.micronaut.visitors.TypeUseRuntimeAnn
    @io.micronaut.visitors.TypeUseClassAnn
    String myField;

    @io.micronaut.visitors.TypeMethodRuntimeAnn
    @io.micronaut.visitors.TypeUseRuntimeAnn
    @io.micronaut.visitors.TypeUseClassAnn
    String myMethod() {
        return null;
    }
}
''')
        expect:
        def field = ce.findField("myField").get()
        def method = ce.findMethod("myMethod").get()

        // Type annotations shouldn't appear on the field
        field.getAnnotationMetadata().getAnnotationNames().asList() == ['io.micronaut.visitors.TypeFieldRuntimeAnn']
        field.getType().getAnnotationMetadata().getAnnotationNames().asList() == ['io.micronaut.visitors.TypeUseRuntimeAnn', 'io.micronaut.visitors.TypeUseClassAnn' ]
        field.getGenericType().getAnnotationMetadata().getAnnotationNames().asList() == ['io.micronaut.visitors.TypeUseRuntimeAnn', 'io.micronaut.visitors.TypeUseClassAnn' ]
        // Type annotations shouldn't appear on the method
        method.getAnnotationMetadata().getAnnotationNames().asList() == ['io.micronaut.visitors.TypeMethodRuntimeAnn']
        method.getReturnType().getAnnotationMetadata().getAnnotationNames().asList() == ['io.micronaut.visitors.TypeUseRuntimeAnn', 'io.micronaut.visitors.TypeUseClassAnn' ]
        method.getGenericReturnType().getAnnotationMetadata().getAnnotationNames().asList() == ['io.micronaut.visitors.TypeUseRuntimeAnn', 'io.micronaut.visitors.TypeUseClassAnn' ]
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

    void "test annotation metadata present on deep type parameters for method 2"() {
        ClassElement ce = buildClassElement('''
package test;
import io.micronaut.core.annotation.*;
import jakarta.validation.constraints.*;
import java.util.List;

class Test {
    List<List<List<@io.micronaut.visitors.TypeUseRuntimeAnn @io.micronaut.visitors.TypeUseClassAnn String>>> deepList() {
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
                    assert listArg3.getAnnotationMetadata().getAnnotationNames().asList() == ['io.micronaut.visitors.TypeUseRuntimeAnn', 'io.micronaut.visitors.TypeUseClassAnn']
                })
            })
        })

        def level1 = theType.getTypeArguments()["E"]
        def level2 = level1.getTypeArguments()["E"]
        def level3 = level2.getTypeArguments()["E"]
        level3.getAnnotationMetadata().getAnnotationNames().asList() == ['io.micronaut.visitors.TypeUseRuntimeAnn', 'io.micronaut.visitors.TypeUseClassAnn' ]
    }

    void "test annotations on recursive generic type parameter 1"() {
        given:
        ClassElement ce = buildClassElement('''\
package test;

final class TrackedSortedSet<@io.micronaut.visitors.TypeUseRuntimeAnn T extends java.lang.Comparable<? super T>> {
}

''')
        expect:
        def typeArguments = ce.getTypeArguments()
        typeArguments.size() == 1
        def typeArgument = typeArguments.get("T")
        typeArgument.name == "java.lang.Comparable"
        typeArgument.getAnnotationNames().asList() == ['io.micronaut.visitors.TypeUseRuntimeAnn']
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
        nextTypeArgument.name == "java.lang.Object"
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
        nextTypeArgument.name == "java.lang.Object"
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
        nextTypeArgument.name == "java.lang.Object"
    }

    void "test recursive generic method return"() {
        expect:
        buildClassElement('''\
package test;

import org.hibernate.SessionFactory;
import org.hibernate.engine.spi.SessionFactoryDelegatingImpl;

class MyFactory {

    SessionFactory sessionFactory() {
        return new SessionFactoryDelegatingImpl(null);
    }
}

''') { ClassElement ce ->
            def sessionFactoryMethod = ce.getEnclosedElement(ElementQuery.ALL_METHODS.named("sessionFactory")).get()
            def withOptionsMethod = sessionFactoryMethod.getReturnType().getEnclosedElement(ElementQuery.ALL_METHODS.named("withOptions")).get()
            def typeArguments = withOptionsMethod.getReturnType().getTypeArguments()
            assert typeArguments.size() == 1
            def typeArgument = typeArguments.get("T")
            assert typeArgument.name == "org.hibernate.SessionBuilder"
            def nextTypeArguments = typeArgument.getTypeArguments()
            def nextTypeArgument = nextTypeArguments.get("T")
            assert nextTypeArgument.name == "java.lang.Object"
            return ce
        }

    }

    void "test recursive generic method return 2"() {
        given:
        ClassElement ce = buildClassElement('''\
package test;

class MyFactory {

    MyBean myBean() {
        return new MyBean();
    }
}

interface MyBuilder<T extends MyBuilder> {
    T build();
}

class MyBean {

   MyBuilder<test.MyBuilder> myBuilder() {
       return null;
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

    void "test recursive generic method return 3"() {
        given:
        ClassElement ce = buildClassElement('''\
package test;

class MyFactory {

    MyBean myBean() {
        return new MyBean();
    }
}

interface MyBuilder<T extends MyBuilder> {
    T build();
}

class MyBean {

   MyBuilder myBuilder() {
       return null;
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
        nextTypeArgument.name == "java.lang.Object"
    }

    void "test recursive generic method return 4"() {
        given:
        ClassElement ce = buildClassElement('''\
package test;

class MyFactory {

    MyBean myBean() {
        return new MyBean();
    }
}

interface MyBuilder<T extends MyBuilder> {
    T build();
}

class MyBean {

   MyBuilder<?> myBuilder() {
       return null;
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

class MyFactory {

    MyBean myBean() {
        return new MyBean();
    }
}

interface MyBuilder<T extends MyBuilder> {
    T build();
}

class MyBean {

   MyBuilder<? extends MyBuilder> myBuilder() {
       return null;
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

    void "test recursive generic method return 6"() {
        given:
        ClassElement ce = buildClassElement('''\
package test;

class MyFactory {

    MyBean myBean() {
        return new MyBean();
    }
}

interface MyBuilder<T extends MyBuilder> {
    T build();
}

class MyBean<T extends MyBuilder> {

   MyBuilder<T> myBuilder() {
       return null;
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

class MyFactory {

    MyBean myBean() {
        return new MyBean();
    }
}

interface MyBuilder<T extends MyBuilder> {
    T build();
}

class MyBean<T extends MyBuilder> {

   MyBuilder<? extends T> myBuilder() {
       return null;
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

    void "test how the annotations from the type are propagated"() {
        given:
        ClassElement ce = buildClassElement('''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;
import java.util.List;
import io.micronaut.visitors.Book;

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
        listTypeArgument.hasAnnotation(MyEntity.class)
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
import io.micronaut.context.annotation.*;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Singleton;
import jakarta.inject.Inject;
import java.util.List;
import java.lang.Integer;

@Singleton
class MathInnerServiceSpec {

    @Inject
    MathService mathService;

    @MockBean(MathService.class)
    static class MyMock implements MathService {

        @Override
        public Integer compute(Integer num) {
            return 50;
        }
    }
}

interface MathService {

    Integer compute(Integer num);
}


@Singleton
class MathServiceImpl implements MathService {

    @Override
    public Integer compute(Integer num) {
        return num * 4; // should never be called
    }
}

''')
        when:
        def replaces = ce.getEnclosedElements(ElementQuery.ALL_INNER_CLASSES).get(0).getAnnotation(Replaces)
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
import io.micronaut.visitors.Book;
import io.micronaut.visitors.TypeUseRuntimeAnn;

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
        def listTypeArgument = saveAll.getParameters()[0].getType().getTypeArguments(List).get("E")
        then:
        validateBookArgument(listTypeArgument)

        when:
        def saveAll2 = ce.findMethod("saveAll2").get()
        def listTypeArgument2 = saveAll2.getParameters()[0].getType().getTypeArguments(List).get("E")
        then:
        validateBookArgument(listTypeArgument2)

        when:
        def saveAll3 = ce.findMethod("saveAll3").get()
        def listTypeArgument3 = saveAll3.getParameters()[0].getType().getTypeArguments(List).get("E")
        then:
        validateBookArgument(listTypeArgument3)

        when:
        def saveAll4 = ce.findMethod("saveAll4").get()
        def listTypeArgument4 = saveAll4.getParameters()[0].getType().getTypeArguments(List).get("E")
        then:
        validateBookArgument(listTypeArgument4)

        when:
        def saveAll5 = ce.findMethod("saveAll5").get()
        def listTypeArgument5 = saveAll5.getParameters()[0].getType().getTypeArguments(List).get("E")
        then:
        validateBookArgument(listTypeArgument5)

        when:
        def save2 = ce.findMethod("save2").get()
        def parameter2 = save2.getParameters()[0].getType()
        then:
        validateBookArgument(parameter2)

        when:
        def save3 = ce.findMethod("save3").get()
        def parameter3 = save3.getParameters()[0].getType()
        then:
        validateBookArgument(parameter3)

        when:
        def save4 = ce.findMethod("save4").get()
        def parameter4 = save4.getParameters()[0].getType()
        then:
        validateBookArgument(parameter4)

        when:
        def save5 = ce.findMethod("save5").get()
        def parameter5 = save5.getParameters()[0].getType()
        then:
        validateBookArgument(parameter5)

        when:
        def get = ce.findMethod("get").get()
        def returnType = get.getReturnType()
        then:
        validateBookArgument(returnType)
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

    String get(@io.micronaut.visitors.MyParameter("X-username") String username);
}

class UserController implements MyApi {

    @Override
    public String get(String username) {
        return null;
    }

}

''')
        expect:
        ce.findMethod("get").get().getParameters()[0].hasAnnotation(MyParameter)
    }

    void "test interface placeholder"() {
        ClassElement ce = buildClassElement('''
package test;
import java.util.List;

class MyRepo implements Repo<MyBean, Long> {
}

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
    }

    void "test interface type annotations"() {
        ClassElement ce = buildClassElement('''
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

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/10042')
    void "private record"() {
        given:
        def outerElement = buildClassElement("""
package test;

class Outer {
    private record Rec(String foo) {

    }
}
""")
        def recordElement = outerElement.getEnclosedElement(ElementQuery.ALL_INNER_CLASSES).get()

        expect:
        recordElement.getPrimaryConstructor().isEmpty()
    }

    void "test interface with type with not inherited generic annotations and conflicting method"() {
        expect:
        buildClassElement('''
package test;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import java.util.List;

interface MyImpl extends BaseRepository<Long, @NotNull MyBean>, Finder<Long, @Null MyBean> {
}

interface BaseRepository<K, V> {
V findById(K key);
}

interface Finder<K, V> {
V findById(K key);
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

''') { ClassElement ce ->
            def method = ce.findMethod("findById").get()
            def type = method.getGenericReturnType()
            assert method.getAnnotationNames().isEmpty()
            assert method.getReturnType().isEmpty()
            assert type.hasAnnotation(NotNull)
            assert !type.hasAnnotation(Null)
            return ce
        }
    }

    void "test interface with type with not inherited generic annotations and conflicting method different order"() {
        expect:
        buildClassElement('''
package test;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import java.util.List;

interface MyImpl extends Finder<Long, @Null MyBean>, BaseRepository<Long, @NotNull MyBean>  {
}

interface BaseRepository<K, V> {
V findById(K key);
}

interface Finder<K, V> {
V findById(K key);
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

''') { ClassElement ce ->
            def method = ce.findMethod("findById").get()
            def type = method.getGenericReturnType()
            assert method.getAnnotationNames().isEmpty()
            assert method.getReturnType().isEmpty()
            assert !type.hasAnnotation(NotNull)
            assert type.hasAnnotation(Null)
            return ce
        }

    }

    void "test interface with conflicting method having not inherited method annotations"() {
        ClassElement ce = buildClassElement('''
package test;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import java.util.List;

interface MyImpl extends BaseRepository<Long, MyBean>, Finder<Long, MyBean> {
}

interface BaseRepository<K, V> {
@NotNull V findById(K key);
}

interface Finder<K, V> {
@Null V findById(K key);
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
        def method = ce.findMethod("findById").get()
        then:
        method.getReturnType().isEmpty()
        method.getGenericReturnType().isEmpty()
        method.hasAnnotation(NotNull)
        !method.hasAnnotation(Null)
    }

    void "test interface with type with inherited generic annotations and conflicting method"() {
        ClassElement ce = buildClassElement('''
package test;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import java.lang.annotation.Inherited;
import java.util.List;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

interface MyImpl extends BaseRepository<Long, @MyAnnotation1 MyBean>, Finder<Long, @MyAnnotation2 MyBean> {
}

interface BaseRepository<K, V> {
V findById(K key);
}

interface Finder<K, V> {
V findById(K key);
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

@Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE })
@Retention(RUNTIME)
@Documented
@Inherited
@interface MyAnnotation1 {
}

@Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE })
@Retention(RUNTIME)
@Documented
@Inherited
@interface MyAnnotation2 {
}

''')

        when:
        def method = ce.findMethod("findById").get()
        def type = method.getGenericReturnType()
        then:
        method.getAnnotationNames().isEmpty()
        method.getReturnType().isEmpty()
        type.hasAnnotation("test.MyAnnotation1")
        !type.hasAnnotation("test.MyAnnotation2")
    }

    void "test interface with conflicting method having inherited method annotations"() {
        ClassElement ce = buildClassElement('''
package test;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import java.lang.annotation.Inherited;
import java.util.List;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

interface MyImpl extends BaseRepository<Long, MyBean>, Finder<Long, MyBean> {
}

interface BaseRepository<K, V> {
@MyAnnotation1 V findById(K key);
}

interface Finder<K, V> {
@MyAnnotation2 V findById(K key);
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

@Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE })
@Retention(RUNTIME)
@Documented
@Inherited
@interface MyAnnotation1 {
}

@Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE })
@Retention(RUNTIME)
@Documented
@Inherited
@interface MyAnnotation2 {
}

''')

        when:
        def method = ce.findMethod("findById").get()
        then:
        method.getAnnotationNames() == ["test.MyAnnotation1"] as Set
        method.getGenericReturnType().isEmpty()
        method.getReturnType().isEmpty()
        method.getGenericReturnType().getType().isEmpty()
        method.getReturnType().getGenericType().isEmpty()
    }

    void "test interface with conflicting method having inherited method parameter annotations"() {
        ClassElement ce = buildClassElement('''
package test;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import java.lang.annotation.Inherited;
import java.util.List;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

interface MyImpl extends BaseRepository<Long, MyBean>, Finder<Long, MyBean> {
}

interface BaseRepository<K, V> {
V findById(@MyAnnotation1 K key);
}

interface Finder<K, V> {
V findById(@MyAnnotation2 K key);
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

@Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE })
@Retention(RUNTIME)
@Documented
@Inherited
@interface MyAnnotation1 {
}

@Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE })
@Retention(RUNTIME)
@Documented
@Inherited
@interface MyAnnotation2 {
}

''')

        when:
        def method = ce.findMethod("findById").get()
        then:
        method.getParameters()[0].getAnnotationNames() == ["test.MyAnnotation1"] as Set
        method.getParameters()[0].getType().getAnnotationNames().isEmpty()
        method.getParameters()[0].getGenericType().getAnnotationNames().isEmpty()
    }

    void "test similar methods with arrays"() {
        ClassElement ce = buildClassElement('''
package test;

import io.micronaut.aop.Introduction;

@Introduction
interface MyInterface {

    String process(String str);

    String process(String str, int intParam);

    String process2(String str, int intParam);

    String process(String str, int intArrayParam[]);
}

''')

        when:
        def methods = ce.getMethods();
        then:
        methods.size() == 4
    }

    void "test unrecognized default method"() {
        given:
        ClassElement classElement = buildClassElement('''
package elementquery;

interface MyBean extends GenericInterface, SpecificInterface {

    default Specific getObject() {
        return null;
    }

}

class Generic {
}
class Specific extends Generic {
}
interface GenericInterface {
    Generic getObject();
}
interface SpecificInterface {
    Specific getObject();
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
        declaredMethods.get(0).isDefault() == true
    }

    void "test overridden methods"() {
        given:
        ClassElement classElement = buildClassElement('''
package test;

import java.util.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@jakarta.inject.Singleton
class Test implements TestBase {
    @Override
    public void map(Map<@NotBlank String, List<@NotNull String>> list) {
    }
}

interface TestBase {
    @io.micronaut.context.annotation.Executable
    void map(Map<String, List<@NotBlank String>> list);
}

''')
        when:
        def declaredMethods = classElement.getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared())
        then:
        declaredMethods.size() == 1
        declaredMethods.get(0).getOverriddenMethods()[0].getDeclaringType().getName() == "test.TestBase"
        declaredMethods.get(0).getOverriddenMethods()[0].getParameters()[0].getGenericType().getTypeArguments(Map).get("V").getTypeArguments(List).get("E").getAnnotationNames().toList() == ['jakarta.validation.constraints.NotBlank$List']
    }

    void "test bean properties interfaces"() {
        def ce = buildClassElement('''
package test;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.visitors.data.Pageable;
import jakarta.inject.Singleton;

class TestNamed {
    Pageable method() {
        return null;
    }
}

''')
        def properties = ce.findMethod("method").get().getReturnType().getBeanProperties()
        expect:
            properties.stream().map {it.getName()}.sorted().toList() == [
                    "sorted",
                    "orderBy",
                    "number",
                    "size",
                    "offset",
                    "sort",
                    "unpaged"
            ].sort()
    }

    void "test bean properties 2"() {
        def ce = buildClassElement('''
package test;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.visitors.data.Pageable;
import jakarta.inject.Singleton;

class TestNamed {
    Pageable method() {
        return null;
    }
}

''')
        def properties = ce.findMethod("method").get().getReturnType().getBeanProperties()
                .stream()
                .filter {it.getName() == "orderBy"}.findFirst()
                .get()
                .getGenericType()
                .getFirstTypeArgument()
                .get()
                .getBeanProperties()
        expect:
            properties.stream().map {it.getName()}.sorted().toList() == [
                    "ignoreCase",
                    "direction",
                    "property",
                    "ascending"
            ].sort()
            properties.stream()
                    .filter { it.getName() == "direction" }
                    .findFirst()
                    .get()
                    .getType()
                    .isEnum()
            properties.stream()
                    .filter { it.getName() == "direction" }
                    .findFirst()
                    .get()
                    .getType() instanceof EnumElement
    }

    private void assertListGenericArgument(ClassElement type, Closure cl) {
        def arg1 = type.getAllTypeArguments().get(List.class.name).get("E")
        def arg2 = type.getAllTypeArguments().get(Collection.class.name).get("E")
        def arg3 = type.getAllTypeArguments().get(Iterable.class.name).get("T")
        cl.call(arg1)
        cl.call(arg2)
        cl.call(arg3)
    }

    private void assertMethodsByName(List<MethodElement> allMethods, String name, List<String> expectedDeclaringTypeSimpleNames) {
        Collection<MethodElement> methods = collectElements(allMethods, name)
        assert expectedDeclaringTypeSimpleNames.size() == methods.size()
        for (String expectedDeclaringTypeSimpleName : expectedDeclaringTypeSimpleNames) {
            assert oneElementPresentWithDeclaringType(methods, expectedDeclaringTypeSimpleName)
        }
    }

    private void assertFieldsByName(List<FieldElement> allFields, String name, List<String> expectedDeclaringTypeSimpleNames) {
        Collection<FieldElement> fields = collectElements(allFields, name)
        assert expectedDeclaringTypeSimpleNames.size() == fields.size()
        for (String expectedDeclaringTypeSimpleName : expectedDeclaringTypeSimpleNames) {
            assert oneElementPresentWithDeclaringType(fields, expectedDeclaringTypeSimpleName)
        }
    }

    private boolean oneElementPresentWithDeclaringType(Collection<MemberElement> elements, String declaringTypeSimpleName) {
        elements.stream()
                .filter { it -> it.getDeclaringType().getSimpleName() == declaringTypeSimpleName }
                .count() == 1
    }

    static <T extends Element> Collection<T> collectElements(List<T> allElements, String name) {
        return allElements.findAll { it.name == name }
    }
}
