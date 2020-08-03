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
import io.micronaut.http.annotation.Controller
import io.micronaut.inject.AbstractTypeElementSpec
import io.micronaut.inject.ast.EnumElement
import spock.lang.IgnoreIf
import spock.util.environment.Jvm

import java.util.function.Supplier

class ClassElementSpec extends AbstractTypeElementSpec {

    void "test visit inherited controller classes"() {
        buildBeanDefinition('test.TestController', '''
package test;

import io.micronaut.http.annotation.*;
import javax.inject.Inject;

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

    // Java 9+ doesn't allow resolving elements was the compiler
    // is finished being used so this test cannot be made to work beyond Java 8 the way it is currently written
    @IgnoreIf({ Jvm.current.isJava9Compatible() })
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

@javax.inject.Singleton
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
