/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.reflection

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.type.Argument
import io.micronaut.core.type.GenericPlaceholder
import io.micronaut.core.type.WildcardArgument

/**
 * A class read reflectively must not disagree with the same class compiled about which of its types were
 * written raw: the compiled argument keeps the type arguments the declaring type declares, so only
 * {@link Argument#isRawType()} tells a raw usage apart from one written with those variables, and from
 * {@code List<Object>}, which erases to the same thing.
 *
 * <p>The bean is compiled here with javac and the processors, so the comparison is of the two descriptions of
 * one class and nothing else. Every argument is rendered whole - what it erases to, whether it is raw, the
 * variable it stands for with the bounds that variable declares, and the same of each type argument
 * recursively - so that a difference anywhere in the description fails here.</p>
 *
 * <p>Two shapes are deliberately absent, because the two descriptions differ on them for reasons that have
 * nothing to do with raw types and would only be pinned here by accident: a type variable named after one the
 * enclosing class declares, which the Java processor resolves against the enclosing one, and a variable bounded
 * by itself - {@code N extends Comparable<N>} - whose recursion the two cut at different depths.</p>
 */
class RawTypeArgumentParitySpec extends AbstractTypeElementSpec {

    private static final String SOURCE = '''
package test;

import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.Introspected;
import java.util.List;
import java.util.Map;

class Box<B extends Number> {}
class Two<A, B extends Number> {}

@Introspected(accessKind = {Introspected.AccessKind.FIELD, Introspected.AccessKind.METHOD})
class Holder<T extends Number> {

    public List raw;
    public List<T> variable;
    public List<String> concrete;
    public List<Object> object;
    public Box box;
    public Two two;
    public Map<String, List> nested;
    public List<List> nestedRawOnly;
    public List[] rawArray;
    public List<String>[] concreteArray;

    @Executable
    public List returnsRaw() { return null; }

    @Executable
    public void takes(List rawParameter, List<T> variableParameter) {}
}
'''

    /**
     * What the argument erases to, whether it is raw, the variable it stands for with its declared bounds, and
     * the same of each type argument.
     */
    private static String render(Argument<?> argument) {
        StringBuilder rendered = new StringBuilder(argument.type.simpleName)
        if (argument.isRawType()) {
            rendered.append('!raw')
        }
        if (argument instanceof WildcardArgument) {
            rendered.append('!wildcard')
        }
        if (argument instanceof GenericPlaceholder) {
            rendered.append('<var=').append(((GenericPlaceholder<?>) argument).variableName)
                    .append(' bounds=').append(((GenericPlaceholder<?>) argument).bounds*.type*.simpleName)
                    .append('>')
        }
        if (argument.typeParameters.length > 0) {
            rendered.append('(').append(argument.typeParameters.collect { render(it) }.join(', ')).append(')')
        }
        return rendered.toString()
    }

    void "a class read reflectively says the same of every raw type as the same class compiled"() {
        given:
        BeanIntrospection<?> generated = buildBeanIntrospection('test.Holder', SOURCE)
        BeanIntrospection<?> reflective = ReflectionBeanIntrospection.of(generated.beanType)
        Map<String, String> generatedShapes = [:]
        Map<String, String> reflectiveShapes = [:]

        when: 'every property, and the return type and arguments of every method'
        generated.beanProperties.each { property ->
            generatedShapes["property $property.name"] = render(property.asArgument())
            reflectiveShapes["property $property.name"] =
                    render(reflective.getRequiredProperty(property.name, property.type).asArgument())
        }
        generated.beanMethods.each { method ->
            def other = reflective.beanMethods.find { it.name == method.name && it.arguments.length == method.arguments.length }
            generatedShapes["returned by $method.name"] = render(method.returnType.asArgument())
            reflectiveShapes["returned by $method.name"] = render(other.returnType.asArgument())
            method.arguments.eachWithIndex { argument, i ->
                generatedShapes["argument $i of $method.name"] = render(argument)
                reflectiveShapes["argument $i of $method.name"] = render(other.arguments[i])
            }
        }

        then:
        generatedShapes == reflectiveShapes

        and: 'and the description they agree on is the one the source says'
        generatedShapes['property raw'] == 'List!raw(Object<var=E bounds=[Object]>)'
        generatedShapes['property variable'] == 'List(Number<var=T bounds=[Number]>)'
        generatedShapes['property concrete'] == 'List(String)'
        generatedShapes['property object'] == 'List(Object)'
        generatedShapes['property box'] == 'Box!raw(Number<var=B bounds=[Number]>)'
        generatedShapes['property two'] == 'Two!raw(Object<var=A bounds=[Object]>, Number<var=B bounds=[Number]>)'
        generatedShapes['property nested'] == 'Map(String, List!raw(Object<var=E bounds=[Object]>))'
        generatedShapes['property nestedRawOnly'] == 'List(List!raw(Object<var=E bounds=[Object]>))'
        generatedShapes['property rawArray'] == 'List[]!raw(Object<var=E bounds=[Object]>)'
        generatedShapes['property concreteArray'] == 'List[](String)'
        generatedShapes['returned by returnsRaw'] == 'List!raw(Object<var=E bounds=[Object]>)'
        generatedShapes['argument 0 of takes'] == 'List!raw(Object<var=E bounds=[Object]>)'
        generatedShapes['argument 1 of takes'] == 'List(Number<var=T bounds=[Number]>)'
    }

    void "a raw usage is told apart from one written with a type variable, which the placeholder alone cannot"() {
        given:
        BeanIntrospection<?> generated = buildBeanIntrospection('test.Holder', SOURCE)
        BeanIntrospection<?> reflective = ReflectionBeanIntrospection.of(generated.beanType)
        def argument = { BeanIntrospection<?> introspection, String name ->
            introspection.getRequiredProperty(name, Object).asArgument()
        }

        expect: 'each keeps a placeholder of the variable the declaring type declares, either way'
        [generated, reflective].every { introspection ->
            ['raw', 'variable'].every {
                argument(introspection, it).typeParameters[0] instanceof GenericPlaceholder &&
                        argument(introspection, it).typeParameters[0].isTypeVariable()
            }
        }

        and: 'and only the raw one says so'
        [generated, reflective].every { argument(it, 'raw').isRawType() && !argument(it, 'variable').isRawType() }

        and: 'a raw usage erases to what List<Object> does, and is still told apart from it'
        [generated, reflective].every {
            argument(it, 'raw').typeParameters[0].type == Object &&
                    argument(it, 'object').typeParameters[0].type == Object &&
                    argument(it, 'raw').isRawType() && !argument(it, 'object').isRawType()
        }
    }

    void "a raw argument renders back as the bare type it was written as"() {
        given:
        BeanIntrospection<?> reflective = ReflectionBeanIntrospection.of(
                buildBeanIntrospection('test.Holder', SOURCE).beanType)

        expect: 'the type arguments a raw usage keeps are not rendered as ones it was written with'
        ReflectionArguments.toType(reflective.getRequiredProperty('raw', List).asArgument()) == List
        ReflectionArguments.toType(reflective.getRequiredProperty('box', Object).asArgument()).typeName == 'test.Box'

        and: 'where a usage written with its type arguments renders as the parameterized type it was'
        ReflectionArguments.toType(reflective.getRequiredProperty('concrete', List).asArgument()).typeName ==
                'java.util.List<java.lang.String>'
    }
}
