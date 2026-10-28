package io.micronaut.kotlin.processing.annotations

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.core.annotation.AnnotationValue

class RetainableSpec extends AbstractKotlinCompilerSpec {

    void 'test a retainable composed annotation is attributed to the annotation that introduced it'() {
        given:
        def definition = buildBeanDefinition('retainspec.Test', '''
package retainspec

import io.micronaut.context.annotation.AliasFor
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Executable
import io.micronaut.core.annotation.Retainable

@Retainable
@Retention(AnnotationRetention.RUNTIME)
annotation class SizeLike(val min: Int = 0, val max: Int = 100)

@SizeLike(min = 5)
@Retention(AnnotationRetention.RUNTIME)
annotation class ComposedA(
    @get:AliasFor(annotationName = "retainspec.SizeLike", member = "min", applyDefault = true)
    val min: Int = 5
)

@SizeLike(max = 50)
@Retention(AnnotationRetention.RUNTIME)
annotation class ComposedB(
    @get:AliasFor(annotationName = "retainspec.SizeLike", member = "max", applyDefault = true)
    val max: Int = 50
)

@Bean
class Test {

    @ComposedA(min = 3)
    @ComposedB(max = 9)
    @Executable
    fun getValue(): String = ""
}
''')
        def metadata = definition.getRequiredMethod("getValue").getAnnotationMetadata()

        expect: 'each composed annotation reports the occurrence it introduced'
        sizeOf(metadata.getAnnotation("retainspec.ComposedA")) == [[min: 3]]
        sizeOf(metadata.getAnnotation("retainspec.ComposedB")) == [[max: 9]]
    }

    void 'test annotations nested in members and repeatable containers retain what they compose'() {
        given:
        def definition = buildBeanDefinition('nestedretention.Test', '''
package nestedretention

import io.micronaut.context.annotation.AliasFor
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Executable
import io.micronaut.core.annotation.Retainable

@Retainable
@Retention(AnnotationRetention.RUNTIME)
annotation class Limit(val min: Int = 0, val max: Int = 100)

@Retainable
@Retention(AnnotationRetention.BINARY)
annotation class Hidden

@Limit(min = 1)
@Hidden
@Retention(AnnotationRetention.RUNTIME)
@JvmRepeatable(BoundedList::class)
annotation class Bounded(
    @get:AliasFor(annotationName = "nestedretention.Limit", member = "min", applyDefault = true)
    val min: Int = 1
)

@Retention(AnnotationRetention.RUNTIME)
annotation class BoundedList(val value: Array<Bounded>)

@Bounded(min = 2)
@Retention(AnnotationRetention.RUNTIME)
@JvmRepeatable(RangeList::class)
annotation class Range(
    @get:AliasFor(annotationName = "nestedretention.Bounded", member = "min", applyDefault = true)
    val from: Int = 2
)

@Retention(AnnotationRetention.RUNTIME)
annotation class RangeList(val value: Array<Range>)

@Retention(AnnotationRetention.RUNTIME)
annotation class Group(val value: Array<Range>)

@Retention(AnnotationRetention.RUNTIME)
annotation class Holder(
    val ranges: Array<Range> = [],
    val single: Bounded,
    val groups: Array<Group> = [],
    val list: RangeList
)

@Bean
class Test {

    @Range(from = 3)
    @Range(from = 3)
    @Range(from = 7)
    @Executable
    fun repeated(): String = ""

    @Holder(
        ranges = [Range(from = 4), Range(from = 4)],
        single = Bounded(min = 5),
        groups = [Group([Range(from = 6), Range(from = 2)])],
        list = RangeList([Range(from = 8)])
    )
    @Executable
    fun members(): String = ""
}
''')
        def repeated = definition.getRequiredMethod("repeated").getAnnotationMetadata()
        def members = definition.getRequiredMethod("members").getAnnotationMetadata()
        def holder = members.getAnnotation("nestedretention.Holder")

        expect: 'each occurrence in a repeatable container retains two levels of composition, aliases applied'
        // equal repeated occurrences are stored once, as before; an array member keeps both
        repeated.getAnnotationValuesByName("nestedretention.Range").collect { tree(it) } == [range(3), range(7)]

        and: 'annotations in member arrays, single members, nested arrays and nested containers retain the same tree'
        holder.getAnnotations("ranges").collect { tree(it) } == [range(4), range(4)]
        tree(holder.getAnnotation("single").get()) == bounded(5)
        holder.getAnnotations("groups").collect { group -> group.getAnnotations("value").collect { tree(it) } } ==
                [[range(6), range(2)]]
        holder.getAnnotation("list").get().getAnnotations("value").collect { tree(it) } == [range(8)]

        and: 'the reserved member is not an attribute of a nested annotation'
        holder.getAnnotation("single").get().values == [min: 5]

        and: 'the flat view is unchanged'
        !members.hasStereotype("nestedretention.Range")
        !members.hasStereotype("nestedretention.Limit")
    }

    void 'test annotations in a container that is not repeatable retain what they compose'() {
        given:
        def definition = buildBeanDefinition('legacycontainer.Test', '''
package legacycontainer

import io.micronaut.context.annotation.AliasFor
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Executable
import io.micronaut.core.annotation.Retainable

@Retainable
@Retention(AnnotationRetention.RUNTIME)
annotation class Limit(val min: Int = 0, val max: Int = 100)

@Limit(min = 1)
@Retention(AnnotationRetention.RUNTIME)
annotation class Bundled(
    @get:AliasFor(annotationName = "legacycontainer.Limit", member = "min", applyDefault = true)
    val min: Int = 1
) {
    @Retention(AnnotationRetention.RUNTIME)
    annotation class List(val value: Array<Bundled>)
}

@Bean
class Test {

    @Bundled.List([Bundled(min = 3), Bundled(min = 7)])
    @Executable
    fun bundled(): String = ""
}
''')
        def bundled = definition.getRequiredMethod("bundled").getAnnotationMetadata()
                .getAnnotation('legacycontainer.Bundled$List')
                .getAnnotations("value")

        expect: 'each occurrence read back from the bean definition retains what it composes, aliases applied'
        bundled*.values == [[min: 3], [min: 7]]
        bundled.collect { it.getStereotypes()*.annotationName } == [['legacycontainer.Limit']] * 2
        bundled.collect { it.getStereotypes()*.values } == [[[min: 3]], [[min: 7]]]
    }

    private static Map<String, Object> range(int from) {
        return [name: 'Range', values: [from: from], stereotypes: [bounded(from)]]
    }

    private static Map<String, Object> bounded(int min) {
        // The retained Hidden is not available at runtime and is not written
        return [name: 'Bounded', values: [min: min], stereotypes: [[name: 'Limit', values: [min: min], stereotypes: []]]]
    }

    /**
     * The composition each fixture annotation declares. An alias also introduces the annotations it reaches through
     * the composition as occurrences of their own, as it does for an annotation declared on an element; the tree
     * compared is the declared composition.
     */
    private static final Map<String, List<String>> COMPOSED = [Range: ['Bounded'], Bounded: ['Limit', 'Hidden']]

    private static Map<String, Object> tree(AnnotationValue<?> annotation) {
        String name = annotation.annotationName.substring(annotation.annotationName.lastIndexOf('.') + 1)
        List<String> composed = COMPOSED[name] ?: []
        return [
                name       : name,
                values     : annotation.values,
                stereotypes: (annotation.getStereotypes() ?: [])
                        .findAll { composed.contains(it.annotationName.substring(it.annotationName.lastIndexOf('.') + 1)) }
                        .collect { tree(it) }
        ]
    }

    private static List<Map> sizeOf(AnnotationValue<?> annotationValue) {
        annotationValue.getStereotypes()
                .findAll { it.getAnnotationName() == "retainspec.SizeLike" }
                .collect { it.getValues() }
    }
}
