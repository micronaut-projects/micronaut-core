package io.micronaut.inject.annotation.synthesized

import io.micronaut.core.annotation.AnnotationClassValue
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.annotation.AnnotationValueProvider
import io.micronaut.inject.annotation.AnnotationMetadataSupport
import io.micronaut.inject.annotation.MutableAnnotationMetadata
import spock.lang.Specification

import java.lang.annotation.Annotation
import java.util.concurrent.TimeUnit

class SynthesizedAnnotationContractSpec extends Specification {

    void "an annotation synthesized from an annotation value equals and hashes like the written one: #holder.simpleName"() {
        given:
        MemberKinds written = holder.getAnnotation(MemberKinds)
        MemberKinds synthesized = AnnotationMetadataSupport.buildAnnotation(MemberKinds, AnnotationValue.of(written))

        expect:
        synthesized.equals(written)
        written.equals(synthesized)
        synthesized.hashCode() == written.hashCode()

        and: "the hash code stays the same when asked again"
        synthesized.hashCode() == synthesized.hashCode()

        where:
        holder << [Defaults, StringMember, PrimitiveMember, EnumMember, ClassMember, NestedMember, StringArrayMember,
                   PrimitiveArrayMember, EnumArrayMember, ClassArrayMember, NestedArrayMember, EmptyArrayMembers, EveryMember]
    }

    void "an annotation synthesized from annotation metadata equals and hashes like the written one: #holder.simpleName"() {
        given:
        MemberKinds written = holder.getAnnotation(MemberKinds)
        def metadata = new MutableAnnotationMetadata()
        metadata.addDeclaredAnnotation(MemberKinds.name, AnnotationValue.of(written).values)
        MemberKinds synthesized = metadata.synthesize(MemberKinds)

        expect:
        synthesized.equals(written)
        written.equals(synthesized)
        synthesized.hashCode() == written.hashCode()

        where:
        holder << [Defaults, StringMember, PrimitiveMember, EnumMember, ClassMember, NestedMember, StringArrayMember,
                   PrimitiveArrayMember, EnumArrayMember, ClassArrayMember, NestedArrayMember, EmptyArrayMembers, EveryMember]
    }

    void "the members of a synthesized annotation are the ones of the written one: #holder.simpleName"() {
        given:
        MemberKinds written = holder.getAnnotation(MemberKinds)
        MemberKinds synthesized = AnnotationMetadataSupport.buildAnnotation(MemberKinds, AnnotationValue.of(written))

        expect:
        synthesized.string() == written.string()
        synthesized.primitive() == written.primitive()
        synthesized.constant() == written.constant()
        synthesized.type() == written.type()
        synthesized.nested() == written.nested()
        synthesized.nested().hashCode() == written.nested().hashCode()
        synthesized.strings() == written.strings()
        synthesized.primitives() == written.primitives()
        synthesized.constants() == written.constants()
        synthesized.types() == written.types()
        synthesized.nesteds() == written.nesteds()

        where:
        holder << [Defaults, StringMember, PrimitiveMember, EnumMember, ClassMember, NestedMember, StringArrayMember,
                   PrimitiveArrayMember, EnumArrayMember, ClassArrayMember, NestedArrayMember, EmptyArrayMembers, EveryMember]
    }

    void "an annotation synthesized from members in their stored forms equals and hashes like the written one"() {
        given: "an enum as its name, a class as an annotation class value and a nested annotation as an annotation value"
        def value = new AnnotationValue<MemberKinds>(MemberKinds.name, [
            string    : "b",
            primitive : 7,
            constant  : "DAYS",
            type      : new AnnotationClassValue<>(String),
            nested    : new AnnotationValue<>(MemberKinds.Nested.name, [value: "b", unit: "DAYS"]),
            strings   : ["b", "c"] as String[],
            primitives: [7, 8] as int[],
            constants : ["DAYS", "HOURS"] as String[],
            types     : [new AnnotationClassValue<>(String), new AnnotationClassValue<>(Integer)] as AnnotationClassValue[],
            nesteds   : [
                new AnnotationValue<>(MemberKinds.Nested.name, [value: "b"]),
                new AnnotationValue<>(MemberKinds.Nested.name, [value: "c", unit: "DAYS"])
            ] as AnnotationValue[]
        ] as Map<CharSequence, Object>)
        MemberKinds written = EveryMember.getAnnotation(MemberKinds)
        MemberKinds synthesized = AnnotationMetadataSupport.buildAnnotation(MemberKinds, value)

        expect:
        synthesized.equals(written)
        written.equals(synthesized)
        synthesized.hashCode() == written.hashCode()
    }

    void "synthesized annotations of different members are not equal"() {
        given:
        MemberKinds one = AnnotationMetadataSupport.buildAnnotation(MemberKinds, AnnotationValue.of(EnumMember.getAnnotation(MemberKinds)))
        MemberKinds other = AnnotationMetadataSupport.buildAnnotation(MemberKinds, AnnotationValue.of(ClassMember.getAnnotation(MemberKinds)))

        expect:
        one != other
        one != ClassMember.getAnnotation(MemberKinds)
        one.hashCode() != other.hashCode()
    }

    void "an annotation synthesized from an explicitly empty nested annotation array answers the empty array"() {
        given:
        def value = new AnnotationValue<MemberKinds>(MemberKinds.name, [nesteds: storedEmpty] as Map<CharSequence, Object>)
        MemberKinds written = EmptyNestedArray.getAnnotation(MemberKinds)
        MemberKinds synthesized = AnnotationMetadataSupport.buildAnnotation(MemberKinds, value)

        expect:
        synthesized.nesteds().length == 0
        synthesized.equals(written)
        written.equals(synthesized)
        synthesized.hashCode() == written.hashCode()

        where:
        storedEmpty << [new AnnotationValue[0], new Object[0]]
    }

    void "an annotation synthesized without an annotation value is the annotation of the defaults"() {
        given:
        MemberKinds written = Defaults.getAnnotation(MemberKinds)
        MemberKinds synthesized = AnnotationMetadataSupport.buildAnnotation(MemberKinds, null)

        expect:
        synthesized.string() == "a"
        synthesized.equals(written)
        written.equals(synthesized)
        synthesized.hashCode() == written.hashCode()
        synthesized.toString() == "@" + MemberKinds.name
        (synthesized as AnnotationValueProvider).annotationValue().annotationName == MemberKinds.name
        (synthesized as AnnotationValueProvider).annotationValue().values.isEmpty()
    }

    void "a synthesized annotation equals and hashes like an annotation written by hand"() {
        given:
        MemberKinds.Nested literal = new MemberKinds.Nested() {
            @Override
            String value() { "b" }

            @Override
            TimeUnit unit() { TimeUnit.DAYS }

            @Override
            Class<? extends Annotation> annotationType() { MemberKinds.Nested }

            @Override
            boolean equals(Object obj) {
                obj instanceof MemberKinds.Nested && obj.value() == "b" && obj.unit() == TimeUnit.DAYS
            }

            @Override
            int hashCode() {
                ((127 * "value".hashCode()) ^ "b".hashCode()) + ((127 * "unit".hashCode()) ^ TimeUnit.DAYS.hashCode())
            }
        }
        MemberKinds.Nested synthesized = AnnotationMetadataSupport.buildAnnotation(
            MemberKinds.Nested,
            new AnnotationValue<MemberKinds.Nested>(MemberKinds.Nested.name, [value: "b", unit: "DAYS"] as Map<CharSequence, Object>))

        expect:
        synthesized.equals(literal)
        literal.equals(synthesized)
        synthesized.hashCode() == literal.hashCode()
    }
}

@MemberKinds
class Defaults {}

@MemberKinds(string = "b")
class StringMember {}

@MemberKinds(primitive = 7)
class PrimitiveMember {}

@MemberKinds(constant = TimeUnit.DAYS)
class EnumMember {}

@MemberKinds(type = String)
class ClassMember {}

@MemberKinds(nested = @MemberKinds.Nested(value = "b", unit = TimeUnit.DAYS))
class NestedMember {}

@MemberKinds(strings = ["b", "c"])
class StringArrayMember {}

@MemberKinds(primitives = [7, 8])
class PrimitiveArrayMember {}

@MemberKinds(constants = [TimeUnit.DAYS, TimeUnit.HOURS])
class EnumArrayMember {}

@MemberKinds(types = [String, Integer])
class ClassArrayMember {}

@MemberKinds(nesteds = [@MemberKinds.Nested("b"), @MemberKinds.Nested(value = "c", unit = TimeUnit.DAYS)])
class NestedArrayMember {}

@MemberKinds(strings = [], primitives = [], constants = [], types = [], nesteds = [])
class EmptyArrayMembers {}

@MemberKinds(
    string = "b",
    primitive = 7,
    constant = TimeUnit.DAYS,
    type = String,
    nested = @MemberKinds.Nested(value = "b", unit = TimeUnit.DAYS),
    strings = ["b", "c"],
    primitives = [7, 8],
    constants = [TimeUnit.DAYS, TimeUnit.HOURS],
    types = [String, Integer],
    nesteds = [@MemberKinds.Nested("b"), @MemberKinds.Nested(value = "c", unit = TimeUnit.DAYS)]
)
class EveryMember {}

@MemberKinds(nesteds = [])
class EmptyNestedArray {}
