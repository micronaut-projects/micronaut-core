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

import java.lang.reflect.GenericArrayType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType

/**
 * Declarations generated at random from a fixed seed, compiled, and rendered back through {@link Argument#asType()}
 * from the argument read reflectively and from the one the processor generates: each renders as the type the JDK
 * reflects - the same name and shape always, and an equal type with the same hash code wherever no variable is
 * involved.
 */
class AsTypeFuzzSpec extends AbstractTypeElementSpec {

    private static final List<String> CLASSES = ['String', 'Integer', 'Object', 'Number', 'CharSequence', 'Thread.State']
    private static final List<String> VARIABLES = ['T', 'U', 'V', 'W', 'E']
    private static final List<String> PRIMITIVES = ['int', 'long', 'boolean', 'char', 'double']

    private static String generate(Random random, int depth, boolean typeArgument) {
        int choice = random.nextInt(depth <= 0 ? 3 : 12)
        if (typeArgument && random.nextInt(5) == 0) {
            switch (random.nextInt(3)) {
                case 0: return '?'
                case 1: return '? extends ' + generate(random, depth - 1, false)
                default: return '? super ' + generate(random, depth - 1, false)
            }
        }
        switch (choice) {
            case 0: return CLASSES[random.nextInt(CLASSES.size())]
            case 1: return VARIABLES[random.nextInt(VARIABLES.size())]
            case 2: return PRIMITIVES[random.nextInt(PRIMITIVES.size())] + '[]' * (1 + random.nextInt(2))
            case 3: return 'List<' + generate(random, depth - 1, true) + '>'
            case 4: return 'Map<' + generate(random, depth - 1, true) + ', ' + generate(random, depth - 1, true) + '>'
            case 5: return 'Map.Entry<' + generate(random, depth - 1, true) + ', ' + generate(random, depth - 1, true) + '>'
            case 6: return 'Comparable<' + generate(random, depth - 1, true) + '>'
            case 7: return 'Nested<' + generate(random, depth - 1, true) + '>'
            case 8: return 'Plain.Inner<' + generate(random, depth - 1, true) + '>'
            case 9: return random.nextBoolean() ? 'List' : 'Map'
            case 10: return 'Function<' + generate(random, depth - 1, true) + ', ' + generate(random, depth - 1, true) + '>'
            default:
                String component = generate(random, depth - 1, false)
                return component + '[]' * (1 + random.nextInt(2))
        }
    }

    private static String source(long seed, int count) {
        Random random = new Random(seed)
        StringBuilder fields = new StringBuilder()
        for (int i = 0; i < count; i++) {
            fields.append('    public ').append(generate(random, 4, false)).append(' f').append(i).append(';\n')
        }
        return '''
package test;

import io.micronaut.core.annotation.Introspected;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

class Plain {
    class Inner<Y> {
    }
}

@Introspected(accessKind = Introspected.AccessKind.FIELD, visibility = Introspected.Visibility.ANY)
class Fuzz<T extends Number & Comparable<T>, U, V extends List<? extends T>, W extends U, E extends Enum<E>> {
    static class Nested<X> {
    }

''' + fields + '}\n'
    }

    private static boolean hasVariable(Type type, Set<Type> seen = [] as Set) {
        if (type instanceof TypeVariable) {
            return true
        }
        if (type instanceof ParameterizedType) {
            return ([type.ownerType] + (type.actualTypeArguments as List)).any { it != null && hasVariable(it, seen) }
        }
        if (type instanceof WildcardType) {
            return ((type.upperBounds as List) + (type.lowerBounds as List)).any { hasVariable(it, seen) }
        }
        if (type instanceof GenericArrayType) {
            return hasVariable(type.genericComponentType, seen)
        }
        return false
    }

    /**
     * Whether two types have the same structure, variables compared by name and by the shape of their bounds.
     */
    private static String shapeDifference(Type rendered, Type reflected, String path, Set<String> variables = [] as Set) {
        if (reflected instanceof Class) {
            return rendered == reflected ? null : path + ': ' + rendered + ' is not the class ' + reflected
        }
        if (reflected instanceof ParameterizedType) {
            if (!(rendered instanceof ParameterizedType)) {
                return path + ': ' + rendered + ' (' + rendered.getClass().simpleName + ') is not parameterized'
            }
            if (rendered.rawType != reflected.rawType) {
                return path + ': raw ' + rendered.rawType + ' != ' + reflected.rawType
            }
            if ((rendered.ownerType == null) != (reflected.ownerType == null)) {
                return path + ': owner ' + rendered.ownerType + ' != ' + reflected.ownerType
            }
            if (reflected.ownerType != null) {
                String owner = shapeDifference(rendered.ownerType, reflected.ownerType, path + '.owner', variables)
                if (owner != null) {
                    return owner
                }
            }
            return pairwise(rendered.actualTypeArguments, reflected.actualTypeArguments, path + '<>', variables)
        }
        if (reflected instanceof WildcardType) {
            if (!(rendered instanceof WildcardType)) {
                return path + ': ' + rendered + ' is not a wildcard'
            }
            return pairwise(rendered.upperBounds, reflected.upperBounds, path + '.upper', variables)
                ?: pairwise(rendered.lowerBounds, reflected.lowerBounds, path + '.lower', variables)
        }
        if (reflected instanceof GenericArrayType) {
            if (!(rendered instanceof GenericArrayType)) {
                return path + ': ' + rendered + ' (' + rendered.getClass().simpleName + ') is not a generic array'
            }
            return shapeDifference(rendered.genericComponentType, reflected.genericComponentType, path + '[]', variables)
        }
        if (reflected instanceof TypeVariable) {
            if (!(rendered instanceof TypeVariable) || rendered.name != reflected.name) {
                return path + ': ' + rendered + ' is not the variable ' + reflected
            }
            if (!variables.add(reflected.name)) {
                return null
            }
            String bounds = pairwise(rendered.bounds, reflected.bounds, path + '.bounds', variables)
            variables.remove(reflected.name)
            return bounds
        }
        return path + ': unexpected ' + reflected
    }

    private static String pairwise(Type[] rendered, Type[] reflected, String path, Set<String> variables) {
        if (rendered.length != reflected.length) {
            return path + ': ' + rendered.toList() + ' != ' + reflected.toList()
        }
        for (int i = 0; i < rendered.length; i++) {
            String difference = shapeDifference(rendered[i], reflected[i], path + i, variables)
            if (difference != null) {
                return difference
            }
        }
        return null
    }

    private static String compare(String route, Type rendered, Type reflected) {
        if (rendered.typeName != reflected.typeName) {
            return route + ' name ' + rendered.typeName
        }
        String shape = shapeDifference(rendered, reflected, '')
        if (shape != null) {
            return route + ' shape ' + shape
        }
        if (!hasVariable(reflected)) {
            if (rendered != reflected || reflected != rendered) {
                return route + ' not equal'
            }
            if (rendered.hashCode() != reflected.hashCode()) {
                return route + ' hash code'
            }
        }
        if (rendered != rendered || rendered.hashCode() != rendered.hashCode()) {
            return route + ' not equal to itself'
        }
        return null
    }

    private List<String> failures(long seed, boolean processed) {
        int count = 250
        BeanIntrospection<?> introspection = buildBeanIntrospection('test.Fuzz', source(seed, count))
        Class<?> fuzz = introspection.beanType
        List<String> failures = []
        for (int i = 0; i < count; i++) {
            Type reflected = fuzz.getDeclaredField('f' + i).genericType
            String failure = processed
                ? compare('processor', introspection.getRequiredProperty('f' + i, Object).asArgument().asType(), reflected)
                : compare('ofExact', ReflectionArguments.ofExact(reflected).asType(), reflected)
            if (failure != null) {
                failures << ('f' + i + ' ' + reflected.typeName + ' -> ' + failure)
            }
        }
        failures.take(20).each { println '  ' + it }
        return failures
    }

    void "a declaration read reflectively renders as the type the JDK reflects, seed #seed"() {
        expect:
        failures(seed, false).isEmpty()

        where:
        seed << (1L..4L)
    }

    void "a declaration the processor generates renders as the type the JDK reflects, seed #seed"() {
        expect:
        failures(seed, true).isEmpty()

        where:
        seed << (1L..4L)
    }
}
