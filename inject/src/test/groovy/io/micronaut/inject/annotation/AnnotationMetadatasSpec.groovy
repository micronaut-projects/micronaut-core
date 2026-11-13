package io.micronaut.inject.annotation

import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.AnnotationValue
import jakarta.inject.Named
import spock.lang.Specification

class AnnotationMetadatasSpec extends Specification {

    void "merge reads every level as declared, the last level winning"() {
        given:
        def first = new MutableAnnotationMetadata()
        first.addDeclaredAnnotation(Named.name, [value: 'first'])
        first.addDeclaredAnnotation('test.OnlyFirst', [:])
        first.addDeclaredRepeatable(io.micronaut.context.annotation.Requirements.name, AnnotationValue.builder(Requires).member('property', 'a').build())
        def second = new MutableAnnotationMetadata()
        second.addDeclaredAnnotation(Named.name, [value: 'second'])
        second.addDeclaredRepeatable(io.micronaut.context.annotation.Requirements.name, AnnotationValue.builder(Requires).member('property', 'b').build())
        AnnotationMetadata[] levels = [first, second]

        when:
        def merged = AnnotationMetadatas.merge(levels)

        then: 'the last level wins'
        merged.stringValue(Named).get() == 'second'

        and: 'every level is declared'
        merged.hasDeclaredAnnotation('test.OnlyFirst')
        merged.hasDeclaredAnnotation(Named)

        and: 'repeated occurrences come from every level'
        merged.getAnnotationValuesByType(Requires)*.stringValue('property')*.get() as Set == ['a', 'b'] as Set

        and: 'the occurrences are those of every level, the last level first'
        merged.stringValues(Named, 'value') as List == ['second', 'first']
        merged.getAnnotationValuesByName(Named.name)*.stringValue()*.get() == ['second', 'first']

        and: 'the declared view covers every level'
        merged.getDeclaredMetadata().hasAnnotation('test.OnlyFirst')
        merged.getDeclaredMetadata().stringValue(Named).get() == 'second'

        and: 'the array given is not reordered'
        levels[0].is(first)
    }

    void "merge keeps the retained stereotypes of the winning occurrence"() {
        given:
        def composed = AnnotationValue.builder('test.Composed').member('value', 1).build()
        def first = new MutableAnnotationMetadata()
        first.addDeclaredAnnotation('test.Composing', [value: 'first'])
        def second = new MutableAnnotationMetadata()
        def retaining = AnnotationValue.builder('test.Composing').member('value', 'second').retainStereotypes([composed]).build()
        second.addDeclaredAnnotation('test.Composing', retaining.getValues() + [(AnnotationUtil.STEREOTYPES_MEMBER): [composed] as AnnotationValue[]])

        when:
        def merged = AnnotationMetadatas.merge(first, second).getAnnotation('test.Composing')

        then:
        merged.stringValue().get() == 'second'
        merged.getStereotypes()*.annotationName == ['test.Composed']
        merged.contains(AnnotationUtil.STEREOTYPES_MEMBER)
    }

    void "a value resolving placeholders keeps resolving them without its stereotypes"() {
        given:
        def context = io.micronaut.context.ApplicationContext.run(['my.value': 'resolved'])
        def value = new EnvironmentAnnotationValue(context.environment, AnnotationValue.builder('test.Composing')
            .member('value', '${my.value}')
            .retainStereotypes([AnnotationValue.builder('test.Composed').build()])
            .build())

        when:
        def stripped = value.withoutStereotypes()

        then:
        value.stringValue().get() == 'resolved'
        stripped instanceof EnvironmentAnnotationValue
        stripped.stringValue().get() == 'resolved'
        stripped.getStereotypes() == null
        !stripped.contains(AnnotationUtil.STEREOTYPES_MEMBER)

        and: 'a plain value keeps its value mapper too'
        def plain = new AnnotationValue(value, null, value.convertibleValues, { it == '${my.value}' ? 'mapped' : it } as java.util.function.Function)
        plain.withoutStereotypes().stringValue().get() == 'mapped'

        cleanup:
        context.close()
    }

    void "merge skips empty levels and returns a single level itself"() {
        given:
        def only = new MutableAnnotationMetadata()
        only.addDeclaredAnnotation(Named.name, [value: 'x'])

        expect:
        AnnotationMetadatas.merge().is(AnnotationMetadata.EMPTY_METADATA)
        AnnotationMetadatas.merge(AnnotationMetadata.EMPTY_METADATA, new MutableAnnotationMetadata()).is(AnnotationMetadata.EMPTY_METADATA)
        AnnotationMetadatas.merge(AnnotationMetadata.EMPTY_METADATA, only).is(only)
        AnnotationMetadatas.merge([only, AnnotationMetadata.EMPTY_METADATA]).is(only)
    }

    void "a value retains its stereotypes and drops them on request"() {
        given:
        def composed = AnnotationValue.builder('test.Composed').member('value', 1).build()
        def retaining = AnnotationValue.builder('test.Composing').member('value', 'x')
            .retainStereotypes([composed])
            .build()

        expect: 'retained in the reserved member, not an attribute'
        retaining.getStereotypes() == [composed]
        retaining.contains(AnnotationUtil.STEREOTYPES_MEMBER)
        retaining.getValues().keySet() == ['value'] as Set

        and: 'kept by a copy'
        retaining.mutate().build().getStereotypes() == [composed]

        when:
        def dropped = retaining.withoutStereotypes()

        then:
        dropped.getStereotypes() == null
        !dropped.contains(AnnotationUtil.STEREOTYPES_MEMBER)
        dropped.stringValue().get() == 'x'
        dropped.withoutStereotypes().is(dropped)
    }

    void "synthesize materializes a value, defaults included"() {
        when:
        Named named = AnnotationMetadatas.synthesize(Named, AnnotationValue.builder(Named).value('n').build())
        Requires requires = AnnotationMetadatas.synthesize(Requires, null)

        then:
        named.value() == 'n'
        named.annotationType() == Named
        requires.property() == ''
    }
}
