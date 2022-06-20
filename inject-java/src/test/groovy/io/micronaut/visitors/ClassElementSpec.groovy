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
import io.micronaut.context.exceptions.BeanContextException
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ConstructorElement
import io.micronaut.inject.ast.Element
import io.micronaut.inject.ast.ElementModifier
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.EnumElement
import io.micronaut.inject.ast.FieldElement
import io.micronaut.inject.ast.MemberElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.PackageElement
import jakarta.inject.Singleton
import spock.lang.IgnoreIf
import spock.lang.Issue
import spock.lang.Requires
import spock.lang.Unroll
import spock.util.environment.Jvm

import java.sql.SQLException
import java.util.function.Supplier

class ClassElementSpec extends AbstractTypeElementSpec {

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
        element.annotate(Singleton)

        then:
        !element.hasAnnotation(Singleton)
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

    // TODO: Investigate why this fails on JDK 11
    // com.sun.tools.javac.util.PropagatedException: java.lang.IllegalStateException
    //     at jdk.compiler/com.sun.tools.javac.api.JavacTaskImpl.prepareCompiler(JavacTaskImpl.java:187)
    //     at jdk.compiler/com.sun.tools.javac.api.JavacTaskImpl.enter(JavacTaskImpl.java:290)
    //     at jdk.compiler/com.sun.tools.javac.api.JavacTaskImpl.ensureEntered(JavacTaskImpl.java:481)
    //     at jdk.compiler/com.sun.tools.javac.model.JavacElements.ensureEntered(JavacElements.java:779)
    //     at jdk.compiler/com.sun.tools.javac.model.JavacElements.doGetTypeElement(JavacElements.java:171)
    //     at jdk.compiler/com.sun.tools.javac.model.JavacElements.getTypeElement(JavacElements.java:160)
    //     at jdk.compiler/com.sun.tools.javac.model.JavacElements.getTypeElement(JavacElements.java:87)
    //     at io.micronaut.annotation.processing.GenericUtils.buildGenericTypeArgumentElementInfo(GenericUtils.java:91)
    //     at io.micronaut.annotation.processing.visitor.JavaClassElement.getSuperType(JavaClassElement.java:127)
    @IgnoreIf({ Jvm.current.isJava9Compatible() })
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
        List<FieldElement>  allFields = classElement.getEnclosedElements(ElementQuery.ALL_FIELDS)

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
    @Requires({ jvm.isJava9Compatible() }) // private static Since Java 9
    void "test inherited methods using ElementQuery"() {
        given:
            ClassElement classElement = buildClassElement('''
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
                .filter { it -> it.getDeclaringType().getSimpleName() == declaringTypeSimpleName}
                .count() == 1
    }

    static <T extends Element> Collection<T> collectElements(List<T> allElements, String name) {
        return allElements.findAll { it.name == name }
    }
}
