package io.micronaut.kotlin.processing.inject.ast

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.GenericPlaceholderElement
import io.micronaut.inject.ast.WildcardElement

class AllSuperTypesSpec extends AbstractKotlinCompilerSpec {

    private static final String TYPES = '''
package test

interface View<V>
interface Named
interface Titled : Named
abstract class Box<T> : View<T>, Named
abstract class Mid<X> : Box<X>(), Titled
class Leaf : Mid<String>()
class Swapped<K, V> : java.util.HashMap<V, K>()
class Wild : Box<java.util.List<out Number>>()
class Diamond : Box<String>(), Titled, Named
class Ordered<T : Comparable<T>> : Box<T>()
class Holder {
    var box: Box<String>? = null
    var swapped: Swapped<String, Int>? = null
    var list: java.util.List<String>? = null
    var strings: Array<String>? = null
    var boxes: Array<Box<String>>? = null
    var ints: Array<IntArray>? = null
    var primitive: Int = 0
    var star: Box<*>? = null
}
'''

    void "the closure of a class binds the variables of its super types along the path"() {
        expect:
        closures('test.Leaf') == [['test.Leaf', 'test.Mid<java.lang.String>', 'test.Titled', 'test.Named', 'test.Box<java.lang.String>', 'test.View<java.lang.String>', 'java.lang.Object']]
    }

    void "the closure of a parameterized use binds the variables of the super types to its arguments"() {
        expect:
        closures('test.Holder', 'box', 'swapped', 'list', 'star') == [
            ['test.Box<java.lang.String>', 'test.View<java.lang.String>', 'test.Named', 'java.lang.Object'],
            ['test.Swapped<java.lang.String, java.lang.Integer>', 'java.util.HashMap<java.lang.Integer, java.lang.String>', 'java.lang.Cloneable', 'java.io.Serializable', 'java.util.Map<java.lang.Integer, java.lang.String>', 'java.util.AbstractMap<java.lang.Integer, java.lang.String>', 'java.lang.Object'],
            ['java.util.List<java.lang.String>', 'java.util.SequencedCollection<java.lang.String>', 'java.util.Collection<java.lang.String>', 'java.lang.Iterable<java.lang.String>', 'java.lang.Object'],
            ['test.Box<?>', 'test.View<?>', 'test.Named', 'java.lang.Object'],
        ]
    }

    void "an interface reached by both sides of a diamond is listed once, where it is reached first"() {
        expect:
        closures('test.Diamond') == [['test.Diamond', 'test.Titled', 'test.Named', 'test.Box<java.lang.String>', 'test.View<java.lang.String>', 'java.lang.Object']]
    }

    void "the super types of a type copied without arguments are raw"() {
        given:
        List<String> rendered = []
        List<Boolean> raw = []
        buildClassElement('test.Holder', TYPES) { holder ->
            def erased = field(holder, 'box').withTypeArguments([:]).getAllSuperTypes()
            rendered.addAll(erased.collect { render(it) })
            raw.addAll(erased.collect { it.rawType })
        }

        expect:
        rendered == ['test.Box', 'test.View', 'test.Named', 'java.lang.Object']
        raw == [true, true, false, false]
    }

    void "an array is followed by Object alone and a primitive by nothing"() {
        expect:
        closures('test.Holder', 'strings', 'boxes', 'ints', 'primitive') == [
            ['java.lang.String[]', 'java.lang.Object'],
            ['test.Box<java.lang.String>[]', 'java.lang.Object'],
            ['int[][]', 'java.lang.Object'],
            ['int'],
        ]
    }

    void "an unbound variable stays a placeholder with its bound, and a wildcard is kept"() {
        given:
        List<String> ordered = []
        List<String> wild = []
        buildClassElement('test.Ordered', TYPES) { element ->
            ordered.addAll(closure(element))
            def variable = element.getAllSuperTypes()[2].typeArguments['V']
            ordered << (variable instanceof GenericPlaceholderElement ? variable.variableName + ' bound ' + render(variable.bounds[0]) : 'not a placeholder')
        }
        buildClassElement('test.Wild', TYPES) { element ->
            wild.addAll(closure(element))
            wild << (element.getAllSuperTypes()[2].typeArguments['V'].typeArguments['E'] instanceof WildcardElement ? 'wildcard' : 'not a wildcard')
        }

        expect:
        ordered == ['test.Ordered<T>', 'test.Box<T>', 'test.View<T>', 'test.Named', 'java.lang.Object', 'T bound java.lang.Comparable<T>']
        wild == ['test.Wild', 'test.Box<java.util.List<? extends java.lang.Number>>', 'test.View<java.util.List<? extends java.lang.Number>>', 'test.Named', 'java.lang.Object', 'wildcard']
    }

    /**
     * The closures of the named class, or of the given fields of it, computed while the compilation is open.
     */
    private List<List<String>> closures(String className, String... fields) {
        List<List<String>> result = []
        buildClassElement(className, TYPES) { element ->
            if (fields.length == 0) {
                result << closure(element)
            } else {
                fields.each { result << closure(field(element, it)) }
            }
        }
        return result
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
