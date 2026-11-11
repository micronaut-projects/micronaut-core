package io.micronaut.kotlin.processing.annotations

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.AnnotationClassValue
import io.micronaut.core.annotation.AnnotationValue
import spock.lang.AutoCleanup
import spock.lang.Shared

/**
 * An array member of an annotation that is given no element holds an array of the type an element would have, which
 * is the type javac gives it, rather than the class of the element type the member declares.
 *
 * <p>The values are read both as the builder produced them and as the generated definition holds them, because the
 * writer maps some component types of its own: an array of an enum is written as one of the names of its constants
 * whatever the builder made of it.</p>
 */
class EmptyArrayMemberSpec extends AbstractKotlinCompilerSpec {

    private static final String ANNOTATION = 'test.ArrayMembers'

    /**
     * The component type of every array member of {@code test.ArrayMembers}, which is the one an element of it has.
     * {@code ints} is left out: an {@code IntArray} is not a {@code kotlin.Array}, so its element type is not looked
     * at, and the primitive arrays are covered on their own below.
     */
    private static final Map<String, Class<?>> COMPONENT_TYPES = [
            types  : AnnotationClassValue,
            aliased: AnnotationClassValue,
            reqs   : AnnotationValue,
            inners : AnnotationValue,
            units  : String,
            modes  : String,
            strings: String
    ]

    private static final String SOURCE = '''
package test

import io.micronaut.context.annotation.Requires
import jakarta.inject.Qualifier
import jakarta.inject.Singleton
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

@Retention(AnnotationRetention.RUNTIME)
annotation class Inner(val v: String = "")

enum class Mode { SOLID }

typealias Classes = KClass<*>

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ArrayMembers(
    val name: String = "",
    val types: Array<KClass<*>> = [],
    val aliased: Array<Classes> = [],
    val reqs: Array<Requires> = [],
    val inners: Array<Inner> = [],
    val units: Array<TimeUnit> = [],
    val modes: Array<Mode> = [],
    val ints: IntArray = [],
    val strings: Array<String> = []
)

@Singleton
@ArrayMembers(name = "empty", types = [], aliased = [], reqs = [], inners = [], units = [], modes = [], ints = [], strings = [])
class Empty

@Singleton
@ArrayMembers(name = "full", types = [String::class], aliased = [String::class], reqs = [Requires(classes = [])],
    inners = [Inner("a")], units = [TimeUnit.SECONDS], modes = [Mode.SOLID], ints = [1], strings = ["a"])
class Full

@Singleton
@Requires(classes = [])
class JavaEmpty
'''

    @Shared
    @AutoCleanup
    ApplicationContext context

    /** The values of the members that are given no element, as the generated definition holds them. */
    @Shared
    Map<CharSequence, Object> empty

    /** The values of the same members given one element. */
    @Shared
    Map<CharSequence, Object> full

    /** The values the builder produced for the members that are given no element, before they were written. */
    @Shared
    Map<CharSequence, Object> compiled

    /** The defaults the builder registered for the annotation, which are its members given no element. */
    @Shared
    Map<CharSequence, Object> defaults

    void setupSpec() {
        context = buildContext(SOURCE)
        empty = values('test.Empty')
        full = values('test.Full')
        AnnotationValue<?> annotation = buildClassElementMapped('test.Empty', SOURCE) { it.getAnnotation(ANNOTATION) }
        compiled = annotation.values
        defaults = annotation.defaultValues
    }

    void "a member that is given no element holds an empty array of the type an element would have"() {
        expect:
        componentTypes(empty) == COMPONENT_TYPES
        COMPONENT_TYPES.keySet().every { empty[it].length == 0 }
    }

    void "the builder produces that type and registers it as the default of the member"() {
        expect:
        componentTypes(compiled) == COMPONENT_TYPES
        componentTypes(defaults) == COMPONENT_TYPES

        and:
        COMPONENT_TYPES.keySet().every { compiled[it].length == 0 && defaults[it].length == 0 }
    }

    void "a member that is given one element holds the same type"() {
        expect:
        componentTypes(full) == COMPONENT_TYPES
    }

    void "an array of a primitive type is one of objects, and one of wrappers when an element is given"() {
        expect:
        empty.ints.getClass() == Object[]
        empty.ints.length == 0
        compiled.ints.getClass() == Object[]
        defaults.ints.getClass() == Object[]

        and:
        full.ints.getClass() == Integer[]
    }

    void "a Class array member of an annotation of the class path that is given no element is one of class values"() {
        given: "the member of @Requires on a bean, and the one nested in a member that is given an element"
        Object classes = values('test.JavaEmpty', Requires.name).classes
        Object nested = full.reqs[0].values.classes

        expect:
        classes.getClass() == AnnotationClassValue[]
        classes.length == 0
        nested.getClass() == AnnotationClassValue[]
        nested.length == 0
    }

    private Map<CharSequence, Object> values(String bean, String annotation = ANNOTATION) {
        return getBeanDefinition(context, bean).annotationMetadata.getAnnotation(annotation).values
    }

    /**
     * Groovy compares arrays by their elements only, so that an empty {@code Object[]} equals an empty array of any
     * type: the component types are compared here, by name so that a missing member fails the comparison.
     */
    private static Map<String, Class<?>> componentTypes(Map<CharSequence, Object> values) {
        return values.collectEntries { member, value -> [(member.toString()): value.getClass().componentType] }
                .findAll { COMPONENT_TYPES.containsKey(it.key) }
    }
}
