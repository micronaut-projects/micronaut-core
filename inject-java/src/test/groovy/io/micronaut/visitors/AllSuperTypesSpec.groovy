package io.micronaut.visitors

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery
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
        closure(leaf) == ['test.Leaf', 'test.Mid<java.lang.String>', 'test.Box<java.lang.String>', 'test.View<java.lang.String>', 'test.Named', 'test.Titled', 'java.lang.Object']
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
        closure(field(holder, 'swapped')) == ['test.Swapped<java.lang.String, java.lang.Integer>', 'java.util.HashMap<java.lang.Integer, java.lang.String>', 'java.util.AbstractMap<java.lang.Integer, java.lang.String>', 'java.util.Map<java.lang.Integer, java.lang.String>', 'java.lang.Cloneable', 'java.io.Serializable', 'java.lang.Object']
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
        closure(diamond) == ['test.Diamond', 'test.Box<java.lang.String>', 'test.View<java.lang.String>', 'test.Named', 'test.Titled', 'java.lang.Object']
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

    void "an array is an array of each super type of its component, then Cloneable, Serializable and Object, and a primitive has none"() {
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
        closure(field(holder, 'strings')) == ['java.lang.String[]', 'java.io.Serializable[]', 'java.lang.Comparable<java.lang.String>[]', 'java.lang.CharSequence[]', 'java.lang.constant.Constable[]', 'java.lang.constant.ConstantDesc[]', 'java.lang.Object[]', 'java.lang.Cloneable', 'java.io.Serializable', 'java.lang.Object']
        closure(field(holder, 'boxes')) == ['test.Box<java.lang.String>[]', 'test.View<java.lang.String>[]', 'test.Named[]', 'java.lang.Object[]', 'java.lang.Cloneable', 'java.io.Serializable', 'java.lang.Object']
        closure(field(holder, 'ints')) == ['int[][]', 'java.lang.Cloneable', 'java.io.Serializable', 'java.lang.Object']
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

    void "an argument resolved to a raw type does not make the type that carries it raw"() {
        given:
        ClassElement child = buildClassElement('''
package test;

class Child extends Holder<java.util.List> {}
class Holder<X> {
    Box<X> box;
}
interface View<V> {}
interface Named {}
abstract class Box<T> implements View<T>, Named {}
''')

        when:
        def box = field(child, 'box')

        then:
        !box.rawType
        closure(box) == ['test.Box<java.util.List>', 'test.View<java.util.List>', 'test.Named', 'java.lang.Object']
    }

    void "the Object listed last is assignable as Object"() {
        given:
        ClassElement leaf = buildClassElement('''
package test;

class Leaf implements Named {}
interface Named {}
''')

        when:
        def object = leaf.getAllSuperTypes().last()

        then:
        object.name == 'java.lang.Object'
        object.isAssignable('java.lang.Object')
        object.isAssignable(Object)
        object.isAssignable(ClassElement.of(Object))
        !object.isAssignable(Runnable)
    }

    void "a lattice of diamonds is walked once"() {
        given:
        ClassElement top = buildClassElement('''
package test;

class Top implements A14, B14 {}
interface A0 {}\ninterface B0 {}\ninterface A1 extends A0, B0 {}\ninterface B1 extends A0, B0 {}\ninterface A2 extends A1, B1 {}\ninterface B2 extends A1, B1 {}\ninterface A3 extends A2, B2 {}\ninterface B3 extends A2, B2 {}\ninterface A4 extends A3, B3 {}\ninterface B4 extends A3, B3 {}\ninterface A5 extends A4, B4 {}\ninterface B5 extends A4, B4 {}\ninterface A6 extends A5, B5 {}\ninterface B6 extends A5, B5 {}\ninterface A7 extends A6, B6 {}\ninterface B7 extends A6, B6 {}\ninterface A8 extends A7, B7 {}\ninterface B8 extends A7, B7 {}\ninterface A9 extends A8, B8 {}\ninterface B9 extends A8, B8 {}\ninterface A10 extends A9, B9 {}\ninterface B10 extends A9, B9 {}\ninterface A11 extends A10, B10 {}\ninterface B11 extends A10, B10 {}\ninterface A12 extends A11, B11 {}\ninterface B12 extends A11, B11 {}\ninterface A13 extends A12, B12 {}\ninterface B13 extends A12, B12 {}\ninterface A14 extends A13, B13 {}\ninterface B14 extends A13, B13 {}
''')

        when:
        def types = top.getAllSuperTypes()

        then:
        types.size() == 32
        types.collect { it.name }.toUnique() == types.collect { it.name }
        types[1].name == 'test.A14'
        types[2].name == 'test.A13'
        types.last().name == 'java.lang.Object'
    }

    private static ClassElement field(ClassElement holder, String name) {
        holder.getEnclosedElements(ElementQuery.ALL_FIELDS).find { it.name == name }.genericType
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
