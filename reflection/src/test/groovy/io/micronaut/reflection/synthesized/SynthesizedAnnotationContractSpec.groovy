package io.micronaut.reflection.synthesized

import io.micronaut.core.annotation.AnnotationClassValue
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.reflection.ReflectionAnnotations
import spock.lang.Specification

import java.util.concurrent.TimeUnit

class SynthesizedAnnotationContractSpec extends Specification {

    void "a synthesized annotation equals and hashes like the written one: #holder.simpleName"() {
        given:
        MemberKinds written = holder.getAnnotation(MemberKinds)
        MemberKinds synthesized = ReflectionAnnotations.synthesize(MemberKinds, ReflectionAnnotations.valueOf(written))

        expect:
        synthesized.equals(written)
        written.equals(synthesized)
        synthesized.hashCode() == written.hashCode()

        where:
        holder << [Defaults, StringMember, PrimitiveMember, EnumMember, ClassMember, NestedMember, StringArrayMember,
                   PrimitiveArrayMember, EnumArrayMember, ClassArrayMember, NestedArrayMember, EmptyArrayMembers, EveryMember]
    }

    void "an annotation synthesized by name through a class loader equals and hashes like the written one: #holder.simpleName"() {
        given:
        MemberKinds written = holder.getAnnotation(MemberKinds)
        MemberKinds synthesized = ReflectionAnnotations.synthesize(ReflectionAnnotations.valueOf(written), MemberKinds.classLoader)

        expect:
        synthesized.equals(written)
        written.equals(synthesized)
        synthesized.hashCode() == written.hashCode()

        where:
        holder << [Defaults, StringMember, PrimitiveMember, EnumMember, ClassMember, NestedMember, StringArrayMember,
                   PrimitiveArrayMember, EnumArrayMember, ClassArrayMember, NestedArrayMember, EmptyArrayMembers, EveryMember]
    }

    void "an annotation synthesized from members in their stored forms equals and hashes like the written one"() {
        given:
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
        MemberKinds synthesized = ReflectionAnnotations.synthesize(MemberKinds, value)

        expect:
        synthesized.equals(written)
        written.equals(synthesized)
        synthesized.hashCode() == written.hashCode()
    }

    void "a synthesized qualifier can be found in a set of written ones"() {
        given:
        MemberKinds written = EveryMember.getAnnotation(MemberKinds)
        MemberKinds synthesized = ReflectionAnnotations.synthesize(MemberKinds, ReflectionAnnotations.valueOf(written))

        expect:
        ([written] as HashSet).contains(synthesized)
        ([synthesized] as HashSet).contains(written)
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
