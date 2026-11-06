package io.micronaut.core.convert

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.type.Argument
import spock.lang.Specification

import java.lang.annotation.Annotation

class ContainerConversionSpec extends Specification {

    void "a string converts to the elements of split with a comma, empty elements included"() {
        given:
        def service = new DefaultMutableConversionService()

        expect:
        service.convert(value, Argument.listOf(String)).get() == (value.split(",", -1) as List)
        service.convert(value, Argument.listOf(Integer)).get() == integers

        where:
        value     | integers
        ""        | []
        ","       | []
        "1,"      | [1]
        ",1"      | [1]
        "1,,2"    | [1, 2]
        "1,2,3"   | [1, 2, 3]
        "x,1"     | [1]
        "1"       | [1]
    }

    void "an iterable converts its elements, keeping the ones of the element type"() {
        given:
        def service = new DefaultMutableConversionService()
        def nested = [1, 2]

        expect:
        service.convert([1, "2", null, 3L], Argument.listOf(Integer)).get() == [1, 2, 3]
        service.convert(["a", 1], Argument.listOf(String)).get() == ["a", "1"]
        service.convert([nested], Argument.listOf(Argument.listOf(String))).get() == [["1", "2"]]
        service.convert([1, 2] as Set, Argument.listOf(Integer)).get() == [1, 2]
        service.convert(nested, Argument.listOf(Integer)).get() == nested
        !service.convert(nested, Argument.listOf(Integer)).get().is(nested)
    }

    void "a subclass of the conversion service converts every element"() {
        given:
        def service = new DefaultMutableConversionService() {
            @Override
            <S, T> Optional<T> convert(S object, Class<? super S> sourceType, Class<T> targetType, ConversionContext context) {
                if (targetType == Integer && object instanceof Integer) {
                    return Optional.of((T) (object + 100))
                }
                return super.convert(object, sourceType, targetType, context)
            }
        }

        expect:
        service.convert([1, 2], Argument.listOf(Integer)).get() == [101, 102]
    }

    void "a context of an argument synthesizes the annotations of the argument before the parent ones"() {
        given:
        Annotation childAnnotation = Stub(Deprecated)
        Annotation parentAnnotation = Stub(Deprecated)
        Annotation otherAnnotation = Stub(Override)
        AnnotationMetadata childMetadata = Stub(AnnotationMetadata) {
            synthesize(Deprecated) >> childAnnotation
            synthesize(Override) >> null
            synthesizeAll() >> ([childAnnotation] as Annotation[])
            synthesizeDeclared() >> ([childAnnotation] as Annotation[])
        }
        AnnotationMetadata parentMetadata = Stub(AnnotationMetadata) {
            synthesize(Deprecated) >> parentAnnotation
            synthesize(Override) >> otherAnnotation
            synthesizeAll() >> ([parentAnnotation, otherAnnotation] as Annotation[])
            synthesizeDeclared() >> ([parentAnnotation] as Annotation[])
        }
        def parent = ConversionContext.of(Argument.of(List, "list", parentMetadata, Argument.of(String)))

        when:
        def child = parent.with(Argument.of(String, "element", childMetadata))

        then:
        child.synthesize(Deprecated).is(childAnnotation)
        child.synthesize(Override).is(otherAnnotation)
        child.synthesizeAll() as List == [childAnnotation, parentAnnotation, otherAnnotation]
        child.synthesizeDeclared() as List == [childAnnotation, parentAnnotation]
        child.getAnnotationMetadata().is(childMetadata)

        when: "a rejection of the child is one of the parent"
        child.reject(new IllegalArgumentException("bad"))

        then:
        parent.getLastError().isPresent()
    }
}
