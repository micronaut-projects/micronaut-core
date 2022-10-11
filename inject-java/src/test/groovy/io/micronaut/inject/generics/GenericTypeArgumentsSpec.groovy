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
package io.micronaut.inject.generics

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod
import io.micronaut.inject.writer.BeanDefinitionWriter
import spock.lang.Unroll
import zipkin2.Span
import zipkin2.reporter.AsyncReporter
import zipkin2.reporter.Reporter

import javax.validation.ConstraintViolationException
import java.util.function.Function
import java.util.function.Supplier

class GenericTypeArgumentsSpec extends AbstractTypeElementSpec {

    void "test generic type arguments with inner classes resolve"() {
        given:
        def definition = buildBeanDefinition('innergenerics.Outer$FooImpl', '''
package innergenerics;

class Outer {

    interface Foo<T extends CharSequence> {}
    
    @jakarta.inject.Singleton
    class FooImpl implements Foo<String> {}
}
''')
        def itfe = definition.beanType.classLoader.loadClass('innergenerics.Outer$Foo')

        expect:
        definition.getTypeParameters(itfe).length == 1
    }

    void "test type arguments with inherited fields"() {
        given:
        BeanDefinition definition = buildBeanDefinition('inheritedfields.UserDaoClient', '''
package inheritedfields;

import jakarta.inject.*;

@Singleton
class UserDaoClient extends DaoClient<User>{
}

@Singleton
class UserDao extends Dao<User> {
}

class User {
}

class DaoClient<T> {

    @Inject
    Dao<T> dao;
}

class Dao<T> {
}
''')
        expect:
        definition.injectedFields.first().asArgument().typeParameters.length == 1
        definition.injectedFields.first().asArgument().typeParameters[0].type.simpleName == "User"
    }

    void "test type arguments for exception handler"() {
        given:
        BeanDefinition definition = buildBeanDefinition('exceptionhandler.Test', '''\
package exceptionhandler;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;
import javax.validation.ConstraintViolationException;

@Context
class Test implements ExceptionHandler<ConstraintViolationException, java.util.function.Supplier<Foo>> {

    public java.util.function.Supplier<Foo> handle(String request, ConstraintViolationException e) {
        return null;
    }
}

interface Foo {}
interface ExceptionHandler<T extends Throwable, R> {
    R handle(String request, T exception);
}
''')
        expect:
        definition != null
        def typeArgs = definition.getTypeArguments("exceptionhandler.ExceptionHandler")
        typeArgs.size() == 2
        typeArgs[0].type == ConstraintViolationException
        typeArgs[1].type == Supplier
    }

    void "test type arguments for factory returning interface"() {
        given:
        BeanDefinition definition = buildBeanDefinition('factorygenerics.Test$MyFunc0', '''\
package factorygenerics;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@Factory
class Test {

    @Bean
    io.micronaut.context.event.BeanCreatedEventListener<Foo> myFunc() {
        return (event) -> event.getBean();
    }
}

interface Foo {}

''')
        expect:
        definition != null
        definition.getTypeArguments(BeanCreatedEventListener).size() == 1
        definition.getTypeArguments(BeanCreatedEventListener)[0].type.name == 'factorygenerics.Foo'
    }

    @Unroll
    void "test generic return type resolution for return type: #returnType"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test', """\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;
import java.util.*;

@jakarta.inject.Singleton
class Test {

    @Executable
    public $returnType test() {
        return null;
    }
}
""")
        def method = definition.getRequiredMethod("test")

        expect:
        method.getDescription(true).startsWith("$returnType" )

        where:
        returnType <<
                ['List<Map<String, Integer>>',
                 'List<List<String>>',
                 'List<String>',
                 'Map<String, Integer>']
    }

    void "test wildcard placeholder"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.ConvertibleValuesSerializer', '''
package test;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import io.micronaut.core.convert.value.ConvertibleValues;

import jakarta.inject.Singleton;
import java.io.IOException;
import java.util.Map;
import io.micronaut.context.annotation.Executable;

@Singleton
@Executable
class ConvertibleValuesSerializer extends JsonSerializer<ConvertibleValues<?>> {

    @Override
    public boolean isEmpty(SerializerProvider provider, ConvertibleValues<?> value) {
        return value.isEmpty();
    }

    @Override
    public void serialize(ConvertibleValues<?> value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();

        for (Map.Entry<String, ?> entry : value) {
            String fieldName = entry.getKey();
            Object v = entry.getValue();
            if (v != null) {
                gen.writeFieldName(fieldName);
                gen.writeObject(v);
            }
        }
        gen.writeEndObject();
    }
}
''')
        expect:
        definition != null
    }

    void "test recusive generic type parameter"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.TrackedSortedSet', '''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@jakarta.inject.Singleton
final class TrackedSortedSet<T extends java.lang.Comparable<? super T>> {
 public TrackedSortedSet(java.util.Collection<? extends T> initial) {
        super();
    }
}

''')
        expect:
        definition != null
    }

    void "test type arguments for interface"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test', '''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@jakarta.inject.Singleton
class Test implements java.util.function.Function<String, Integer>{

    public Integer apply(String str) {
        return 10;
    }
}

class Foo {}
''')
        expect:
        definition != null
        definition.getTypeArguments(Function).size() == 2
        definition.getTypeArguments(Function)[0].name == 'T'
        definition.getTypeArguments(Function)[1].name == 'R'
        definition.getTypeArguments(Function)[0].type == String
        definition.getTypeArguments(Function)[1].type == Integer
    }

    void "test type arguments for inherited interface"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test', '''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@jakarta.inject.Singleton
class Test implements Foo {

    public Integer apply(String str) {
        return 10;
    }
}

interface Foo extends java.util.function.Function<String, Integer> {}
''')
        expect:
        definition != null
        definition.getTypeArguments(Function).size() == 2
        definition.getTypeArguments(Function)[0].name == 'T'
        definition.getTypeArguments(Function)[1].name == 'R'
        definition.getTypeArguments(Function)[0].type == String
        definition.getTypeArguments(Function)[1].type == Integer
    }

    void "test type arguments for inherited interface 2"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test', '''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@jakarta.inject.Singleton
class Test implements Bar {

    public Integer apply(String str) {
        return 10;
    }
}

interface Bar extends Foo<Integer> {}
interface Foo<A> extends java.util.function.Function<String, A> {}
''')
        expect:
        definition != null
        definition.getTypeArguments(Function).size() == 2
        definition.getTypeArguments(Function)[0].name == 'T'
        definition.getTypeArguments(Function)[1].name == 'R'
        definition.getTypeArguments(Function)[0].type == String
        definition.getTypeArguments(Function)[1].type == Integer
    }

    void "test type arguments for inherited interface - using same name as another type parameter"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test', '''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@jakarta.inject.Singleton
class Test implements Bar {

    public Integer apply(String str) {
        return 10;
    }
}

interface Bar extends Foo<Integer> {}
interface Foo<T> extends java.util.function.Function<String, T> {}
''')
        expect:
        definition != null
        definition.getTypeArguments(Function).size() == 2
        definition.getTypeArguments(Function)[0].name == 'T'
        definition.getTypeArguments(Function)[1].name == 'R'
        definition.getTypeArguments(Function)[0].type == String
        definition.getTypeArguments(Function)[1].type == Integer
    }


    void "test type arguments for superclass that implements interface"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test', '''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@jakarta.inject.Singleton
class Test extends Foo {

    public Integer apply(String str) {
        return 10;
    }
}

abstract class Foo implements java.util.function.Function<String, Integer> {}
''')
        expect:
        definition != null
        definition.getTypeArguments(Function).size() == 2
        definition.getTypeArguments(Function)[0].name == 'T'
        definition.getTypeArguments(Function)[1].name == 'R'
        definition.getTypeArguments(Function)[0].type == String
        definition.getTypeArguments(Function)[1].type == Integer
    }

    void "test type arguments for superclass"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test', '''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@jakarta.inject.Singleton
class Test extends Foo<String, Integer> {

    public Integer apply(String str) {
        return 10;
    }
}

abstract class Foo<T, R> {

    abstract R apply(T t);
}
''')
        expect:
        definition != null
        definition.getTypeArguments('test.Foo').size() == 2
        definition.getTypeArguments('test.Foo')[0].name == 'T'
        definition.getTypeArguments('test.Foo')[1].name == 'R'
        definition.getTypeArguments('test.Foo')[0].type == String
        definition.getTypeArguments('test.Foo')[1].type == Integer
    }

    void "test type arguments for factory"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test$MyFunc0', '''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@Factory
class Test {

    @Bean
    java.util.function.Function<String, Integer> myFunc() {
        return (str) -> 10;
    }
}

''')
        expect:
        definition != null
        definition.getTypeArguments(Function).size() == 2
        definition.getTypeArguments(Function)[0].name == 'T'
        definition.getTypeArguments(Function)[1].name == 'R'
        definition.getTypeArguments(Function)[0].type == String
        definition.getTypeArguments(Function)[1].type == Integer
    }

    void "test type arguments for factory with inheritance"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test$MyFunc0', '''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@Factory
class Test {

    @Bean
    Foo myFunc() {
        return (str) -> 10;
    }
}

interface Foo extends java.util.function.Function<String, Integer> {}

''')
        expect:
        definition != null
        definition.getTypeArguments(Function).size() == 2
        definition.getTypeArguments(Function)[0].name == 'T'
        definition.getTypeArguments(Function)[1].name == 'R'
        definition.getTypeArguments(Function)[0].type == String
        definition.getTypeArguments(Function)[1].type == Integer
    }

    void "test type arguments for factory with interface"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test$AsyncReporter0', '''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;
import zipkin2.reporter.*;
import zipkin2.*;

@Factory
class Test {

    @jakarta.inject.Singleton
    AsyncReporter<Span> asyncReporter() {
        return null;
    }
}

''')
        expect:
        definition != null
        definition.getTypeParameters(AsyncReporter) == [Span] as Class[]
        definition.getTypeParameters(Reporter) == [Span] as Class[]

    }

    void "test type arguments for factory with AOP advice applied"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.$TestFactory$MyFunc0' + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionWriter.PROXY_SUFFIX, '''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@Factory
class TestFactory {

    @Bean
    @io.micronaut.aop.simple.Mutating("foo")
    java.util.function.Function<String, Integer> myFunc() {
        return (String str) -> 10;
    }
}

''')
        expect:
        definition != null
        definition.getTypeArguments(Function).size() == 2
        definition.getTypeArguments(Function)[0].name == 'T'
        definition.getTypeArguments(Function)[1].name == 'R'
        definition.getTypeArguments(Function)[0].type == String
        definition.getTypeArguments(Function)[1].type == Integer
    }

    void "test type arguments for methods"() {
        BeanDefinition definition = buildBeanDefinition('test.StatusController', '''
package test;

import io.micronaut.http.annotation.*;

class GenericController<T> {

    @Post
    T save(@Body T entity) {
        return entity;
    }
}

@Controller
class StatusController extends GenericController<String> {

}
''')
        List<ExecutableMethod> methods = definition.getExecutableMethods().toList()

        expect:
        definition != null
        methods.size() == 1


        and:
        def method = methods[0]
        method.getArguments()[0].type == String
        method.getReturnType().type == String
    }

    void 'test collection field with generics'() {
        given:
        def definition = buildBeanDefinition('test.MyConfig', '''
package test;

import io.micronaut.context.annotation.ConfigurationProperties;
import java.util.*;

@ConfigurationProperties("foo.bar")
public class MyConfig {
    private Map<String, Map<String, Value>> map = new HashMap<>();

    public void setMap(Map<String, Map<String, Value>> map) {
        this.map = map;
    }
}

class Value {}
''')
        def methodInjectionPoint = definition.injectedMethods.iterator().next()
        def argument = methodInjectionPoint.arguments[0]
        def parameters = argument.typeParameters
        expect:
        parameters
        parameters.length == 2
        parameters[0].type == String
    }
}
