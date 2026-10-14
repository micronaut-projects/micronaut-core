package io.micronaut.visitors

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.GenericPlaceholderElement
import io.micronaut.inject.ast.WildcardElement

class AllSuperTypesSpec extends AbstractTypeElementSpec {

    void "the closure of a class binds the variables of its super types along the path"() {
        given:
        ClassElement leaf = buildClassElement('''
package test;

class Leaf extends Mid<String> {}
interface View<V> {}
interface Named {}
interface Titled extends Named {}
abstract class Box<T> implements View<T>, Named {}
abstract class Mid<X> extends Box<X> implements Titled {}
class Swapped<K, V> extends java.util.HashMap<V, K> {}
class Wild extends Box<java.util.List<? extends Number>> {}
''')

        expect:
        closure(leaf) == ['test.Leaf', 'test.Mid<java.lang.String>', 'test.Titled', 'test.Named', 'test.Box<java.lang.String>', 'test.View<java.lang.String>', 'java.lang.Object']
    }

    void "the closure of a parameterized use binds the variables of the super types to its arguments"() {
        given:
        ClassElement holder = buildClassElement('''
package test;

class Holder {
    Box<String> box;
    Swapped<String, Integer> swapped;
    java.util.List<String> list;
}
interface View<V> {}
interface Named {}
interface Titled extends Named {}
abstract class Box<T> implements View<T>, Named {}
abstract class Mid<X> extends Box<X> implements Titled {}
class Leaf extends Mid<String> {}
class Swapped<K, V> extends java.util.HashMap<V, K> {}
class Wild extends Box<java.util.List<? extends Number>> {}
''')

        expect:
        closure(field(holder, 'box')) == ['test.Box<java.lang.String>', 'test.View<java.lang.String>', 'test.Named', 'java.lang.Object']
        closure(field(holder, 'swapped')) == ['test.Swapped<java.lang.String, java.lang.Integer>', 'java.util.HashMap<java.lang.Integer, java.lang.String>', 'java.util.Map<java.lang.Integer, java.lang.String>', 'java.lang.Cloneable', 'java.io.Serializable', 'java.util.AbstractMap<java.lang.Integer, java.lang.String>', 'java.lang.Object']
        closure(field(holder, 'list')) == ['java.util.List<java.lang.String>', 'java.util.SequencedCollection<java.lang.String>', 'java.util.Collection<java.lang.String>', 'java.lang.Iterable<java.lang.String>', 'java.lang.Object']
    }

    void "an interface reached by both sides of a diamond is listed once, where it is reached first"() {
        given:
        ClassElement diamond = buildClassElement('''
package test;

class Diamond extends Box<String> implements Titled, Named {}
interface View<V> {}
interface Named {}
interface Titled extends Named {}
abstract class Box<T> implements View<T>, Named {}
abstract class Mid<X> extends Box<X> implements Titled {}
class Leaf extends Mid<String> {}
class Swapped<K, V> extends java.util.HashMap<V, K> {}
class Wild extends Box<java.util.List<? extends Number>> {}
''')

        expect:
        closure(diamond) == ['test.Diamond', 'test.Titled', 'test.Named', 'test.Box<java.lang.String>', 'test.View<java.lang.String>', 'java.lang.Object']
    }

    void "the super types of a raw type are raw"() {
        given:
        ClassElement holder = buildClassElement('''
package test;

class Holder {
    Raw raw;
    Box rawBox;
    Box<String> box;
}
abstract class Raw extends Box {}
interface View<V> {}
interface Named {}
interface Titled extends Named {}
abstract class Box<T> implements View<T>, Named {}
abstract class Mid<X> extends Box<X> implements Titled {}
class Leaf extends Mid<String> {}
class Swapped<K, V> extends java.util.HashMap<V, K> {}
class Wild extends Box<java.util.List<? extends Number>> {}
''')

        when:
        def raw = field(holder, 'raw').getAllSuperTypes()
        def rawBox = field(holder, 'rawBox').getAllSuperTypes()
        def erased = field(holder, 'box').withTypeArguments([:]).getAllSuperTypes()

        then:
        raw.collect { render(it) } == ['test.Raw', 'test.Box', 'test.View', 'test.Named', 'java.lang.Object']
        raw[1].rawType
        raw[2].rawType
        raw[2].typeArguments.isEmpty()
        rawBox.collect { render(it) } == ['test.Box', 'test.View', 'test.Named', 'java.lang.Object']
        rawBox[1].rawType
        erased.collect { render(it) } == ['test.Box', 'test.View', 'test.Named', 'java.lang.Object']
        erased[0].rawType
        erased[1].rawType
    }

    void "an array is followed by Object alone and a primitive by nothing"() {
        given:
        ClassElement holder = buildClassElement('''
package test;

class Holder {
    String[] strings;
    Box<String>[] boxes;
    int[][] ints;
    int primitive;
}
interface View<V> {}
interface Named {}
interface Titled extends Named {}
abstract class Box<T> implements View<T>, Named {}
abstract class Mid<X> extends Box<X> implements Titled {}
class Leaf extends Mid<String> {}
class Swapped<K, V> extends java.util.HashMap<V, K> {}
class Wild extends Box<java.util.List<? extends Number>> {}
''')

        expect:
        closure(field(holder, 'strings')) == ['java.lang.String[]', 'java.lang.Object']
        closure(field(holder, 'boxes')) == ['test.Box<java.lang.String>[]', 'java.lang.Object']
        closure(field(holder, 'ints')) == ['int[][]', 'java.lang.Object']
        closure(field(holder, 'primitive')) == ['int']
    }

    void "an unbound variable stays a placeholder with its bound, and a wildcard is kept"() {
        given:
        ClassElement ordered = buildClassElement('''
package test;

class Ordered<T extends Comparable<T>> extends Box<T> {}
interface View<V> {}
interface Named {}
interface Titled extends Named {}
abstract class Box<T> implements View<T>, Named {}
abstract class Mid<X> extends Box<X> implements Titled {}
class Leaf extends Mid<String> {}
class Swapped<K, V> extends java.util.HashMap<V, K> {}
class Wild extends Box<java.util.List<? extends Number>> {}
''')
        ClassElement wild = buildClassElement('''
package test;

class Wild extends Box<java.util.List<? extends Number>> {}
interface View<V> {}
interface Named {}
abstract class Box<T> implements View<T>, Named {}
''')

        when:
        def types = ordered.getAllSuperTypes()
        def variable = types[2].typeArguments['V']

        then:
        closure(ordered) == ['test.Ordered<T>', 'test.Box<T>', 'test.View<T>', 'test.Named', 'java.lang.Object']
        variable instanceof GenericPlaceholderElement
        variable.variableName == 'T'
        render(variable.bounds[0]) == 'java.lang.Comparable<T>'
        closure(wild) == ['test.Wild', 'test.Box<java.util.List<? extends java.lang.Number>>', 'test.View<java.util.List<? extends java.lang.Number>>', 'test.Named', 'java.lang.Object']
        wild.getAllSuperTypes()[2].typeArguments['V'].typeArguments['E'] instanceof WildcardElement
    }

    private static ClassElement field(ClassElement holder, String name) {
        holder.getFields().find { it.name == name }.genericType
    }

    /**
     * A type as its name and arguments, an unbound variable as its name, so the closure reads as it is written.
     */
    private static String render(ClassElement type) {
        if (type instanceof GenericPlaceholderElement placeholder && placeholder.resolved.isEmpty()) {
            return placeholder.variableName + "[]" * type.arrayDimensions
        }
        if (type instanceof WildcardElement wildcard) {
            return "?" + wildcard.upperBounds.findAll { it.name != "java.lang.Object" }.collect { " extends " + render(it) }.join("") +
                wildcard.lowerBounds.collect { " super " + render(it) }.join("")
        }
        String dimensions = "[]" * type.arrayDimensions
        if (type.rawType || type.typeArguments.isEmpty()) {
            return type.name + dimensions
        }
        return type.name + "<" + type.typeArguments.values().collect { render(it) }.join(", ") + ">" + dimensions
    }

    private static List<String> closure(ClassElement type) {
        type.getAllSuperTypes().collect { render(it) }
    }
}
