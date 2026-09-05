package io.micronaut.reflection

import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.BeanPropertyMember
import spock.lang.Specification

import java.lang.annotation.ElementType
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * The members a property is made of, as the processor writes them for {@code @Introspected(members = true)}
 * and as reflection reads them for the same type. A specification that tells the constraints of a field from
 * the ones of a getter has to be told the same thing either way, so the two descriptions are compared member
 * by member.
 */
class PropertyMemberParitySpec extends Specification {

    private BeanIntrospection<MemberParityBean> generated = BeanIntrospection.getIntrospection(MemberParityBean)
    private BeanIntrospection<MemberParityBean> reflective = ReflectionBeanIntrospection.of(MemberParityBean)

    void "the generated introspection carries the members the annotation asked for"() {
        expect:
        generated.beanProperties.every { !it.members.isEmpty() }
    }

    void "every property is made of the same members, in the same order"() {
        expect:
        generated.beanProperties.every { property ->
            describe(property.members) == describe(members(property.name))
        }
    }

    void "a member carries the annotations of its own declaration in both descriptions"() {
        expect:
        generated.beanProperties.every { property ->
            property.members.every { member ->
                def other = reflectiveMember(property.name, member.name, member.elementType)
                other != null && tags(other) == tags(member)
            }
        }
    }

    void "the annotations of a member are the ones written on it, not the merged ones of the property"() {
        given:
        def field = { members(it).find { m -> m.member instanceof Field } }
        def getter = { members(it).find { m -> m.member instanceof Method && m.member.parameterCount == 0 } }
        def setter = { members(it).find { m -> m.member instanceof Method && m.member.parameterCount == 1 } }

        expect: "the same split the generated introspection makes"
        tags(field("value")) == ["field"]
        tags(getter("value")) == ["getter"]
        tags(setter("value")) == ["setter"]

        and:
        tags(generated.getRequiredProperty("value", String).members.find { it.elementType == ElementType.FIELD }) == ["field"]

        and: "while the property itself merges them, the accessors before the field"
        generated.getRequiredProperty("value", String).getAnnotationValuesByType(Tag)*.stringValue()*.get().toSet() ==
                reflective.getRequiredProperty("value", String).getAnnotationValuesByType(Tag)*.stringValue()*.get().toSet()
    }

    void "a member declared by a super class reports that class in both descriptions"() {
        expect:
        generated.getRequiredProperty("note", String).members*.declaringType.every { it == MemberParityBase }
        members("note")*.declaringType.every { it == MemberParityBase }
    }

    void "a member is the type it declares in both descriptions"() {
        expect:
        generated.beanProperties.every { property ->
            property.members.every { member ->
                def other = reflectiveMember(property.name, member.name, member.elementType)
                other.type == member.type &&
                        other.asArgument().typeParameters*.type == member.asArgument().typeParameters*.type
            }
        }
    }

    void "a field and a getter are readable, a setter is not, in both descriptions"() {
        given:
        def bean = new MemberParityBean(value: "read", tags: ["a"], note: "inherited")

        expect:
        generated.beanProperties.every { property ->
            property.members.every { member ->
                def other = reflectiveMember(property.name, member.name, member.elementType)
                other.readable == member.readable &&
                        (!member.readable || other.read(bean) == member.read(bean))
            }
        }

        and: "a field is read as the field, not through the getter"
        members("value").find { it.member instanceof Field }.read(bean) == "read"
    }

    void "a member that cannot be read says so the same way in both descriptions"() {
        given:
        def bean = new MemberParityBean()
        def generatedSetter = generated.getRequiredProperty("value", String).members.find { !it.readable }
        def reflectiveSetter = members("value").find { !it.readable }

        when:
        generatedSetter.read(bean)

        then:
        thrown(UnsupportedOperationException)

        when:
        reflectiveSetter.read(bean)

        then:
        thrown(UnsupportedOperationException)
    }

    void "a write only property is made of its field and its setter in both descriptions"() {
        expect:
        describe(generated.getRequiredProperty("writeOnly", String).members) == describe(members("writeOnly"))
    }

    void "reading a member of the wrong bean type is rejected by both descriptions"() {
        when:
        generated.getRequiredProperty("value", String).members.find { it.readable }.read("not a bean")

        then:
        thrown(IllegalArgumentException)

        when:
        members("value").find { it.readable }.read("not a bean")

        then:
        thrown(IllegalArgumentException)
    }

    private List<BeanPropertyMember> members(String property) {
        return reflective.getProperty(property).map { it.members }.orElse([])
    }

    private BeanPropertyMember reflectiveMember(String property, String name, ElementType elementType) {
        return members(property).find { it.name == name && it.elementType == elementType }
    }

    private static List<String> describe(List<BeanPropertyMember> members) {
        return members.collect { "$it.elementType $it.declaringType.simpleName $it.name" as String }
    }

    private static List<String> tags(BeanPropertyMember member) {
        return member.annotationMetadata.getAnnotationValuesByType(Tag)*.stringValue()*.get()
    }
}
