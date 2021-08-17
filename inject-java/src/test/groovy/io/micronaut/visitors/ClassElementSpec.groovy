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

import io.micronaut.annotation.processing.visitor.JavaClassElement
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ConstructorElement
import io.micronaut.inject.ast.ElementModifier
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.EnumElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.PackageElement
import jakarta.inject.Singleton
import spock.lang.IgnoreIf
import spock.lang.Issue
import spock.util.environment.Jvm

import java.util.function.Supplier

class ClassElementSpec extends AbstractTypeElementSpec {

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

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/5611')
    void 'test visit enum with custom annotation'() {
        when:"An enum has an annotation that is visited by CustomAnnVisitor"
        def context = buildContext('''
package test;

@io.micronaut.visitors.CustomAnn
enum EnumTest {

}
''')

        then:"No compilation error occurs"
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
        when:"all methods are retrieved"
        def allMethods = classElement.getEnclosedElements(ElementQuery.ALL_METHODS)

        then:"All methods, including non-accessible are returned but not overridden"
        allMethods.size() == 7
        allMethods.find { it.name == 'publicMethod'}.declaringType.simpleName == 'Test'
        allMethods.find { it.name == 'otherSuper'}.declaringType.simpleName == 'SuperType'

        when:"obtaining only the declared methods"
        def declared = classElement.getEnclosedElements(ElementQuery.of(MethodElement).onlyDeclared())

        then:"The declared are correct"
        declared.size() == 4
        declared*.name as Set == ['privateMethod', 'packagePrivateMethod', 'publicMethod', 'staticMethod'] as Set

        when:"Accessible methods are retrieved"
        def accessible = classElement.getEnclosedElements(ElementQuery.of(MethodElement).onlyAccessible())

        then:"Only accessible methods, excluding those that require reflection"
        accessible.size() == 5
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

        then:"only our own instance constructors"
        constructors.size() == 2

        when:
        def allConstructors = classElement.getEnclosedElements(ElementQuery.of(ConstructorElement.class))

        then:"superclass constructors, but not including static initializers"
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
}
