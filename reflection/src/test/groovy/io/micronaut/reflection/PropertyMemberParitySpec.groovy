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
                def other = reflectiveMember(property.name, member)
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

    void "a member is reported by the type declaring it in both descriptions"() {
        expect: "the field and the field it hides by the class declaring each, the getter by every type of the hierarchy declaring one, the bean type first"
        describe(generated.getRequiredProperty("note", String).members) == [
                "FIELD MemberParityBean note",
                "FIELD MemberParityBase note",
                "METHOD MemberParityBean getNote",
                "METHOD MemberParityBase getNote",
                "METHOD MemberParityContract getNote",
                "METHOD MemberParityBase setNote",
                "METHOD MemberParityContract setNote"
        ]

        and:
        describe(members("note")) == describe(generated.getRequiredProperty("note", String).members)
    }

    void "a getter overridden along the hierarchy carries the annotations of its own declaration in both descriptions"() {
        given:
        def tagsOf = { List<BeanPropertyMember> members -> members.collect { m -> tags(m) } }

        expect: "the hiding field, the hidden one, the override, the overridden getter and the one of the interface each answer its own tag alone"
        tagsOf(generated.getRequiredProperty("note", String).members) ==
                [["hiding-field"], ["inherited-field"], ["overriding-getter"], ["inherited-getter"], ["contract-getter"], [], ["contract-setter"]]
        tagsOf(members("note")) == tagsOf(generated.getRequiredProperty("note", String).members)

        and: "while the property carries the occurrence of the member it is read through, in both descriptions"
        generated.getRequiredProperty("note", String).getAnnotationValuesByType(Tag)*.stringValue()*.get() == ["overriding-getter"]
        reflective.getRequiredProperty("note", String).getAnnotationValuesByType(Tag)*.stringValue()*.get() ==
                generated.getRequiredProperty("note", String).getAnnotationValuesByType(Tag)*.stringValue()*.get()

        and: "every getter reads the value the override returns, and each field the value it holds"
        def bean = new MemberParityBean(note: "inherited")
        generated.getRequiredProperty("note", String).members.findAll { it.readable && it.elementType == ElementType.METHOD }*.read(bean).every { it == "inherited" }
        generated.getRequiredProperty("note", String).members.findAll { it.elementType == ElementType.FIELD }*.read(bean) == ["hidden", "inherited"]
        members("note").findAll { it.readable }*.read(bean) == generated.getRequiredProperty("note", String).members.findAll { it.readable }*.read(bean)
    }

    void "an introspection says whether it separates the declarations"() {
        expect: "a generated introspection compiled with the members does"
        generated.separatesDeclarations()

        and: "a reflective one always does"
        reflective.separatesDeclarations()

        and: "a generated introspection without the members does not, and lists no member"
        !BeanIntrospection.getIntrospection(ParityBean).separatesDeclarations()
        BeanIntrospection.getIntrospection(ParityBean).beanProperties.every { it.members.isEmpty() }
    }

    void "a member is the type it declares in both descriptions"() {
        expect:
        generated.beanProperties.every { property ->
            property.members.every { member ->
                def other = reflectiveMember(property.name, member)
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
                def other = reflectiveMember(property.name, member)
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

    private BeanPropertyMember reflectiveMember(String property, BeanPropertyMember member) {
        return members(property).find {
            it.name == member.name && it.elementType == member.elementType && it.declaringType == member.declaringType
        }
    }

    private static List<String> describe(List<BeanPropertyMember> members) {
        return members.collect { "$it.elementType $it.declaringType.simpleName $it.name" as String }
    }

    private static List<String> tags(BeanPropertyMember member) {
        return member.annotationMetadata.getAnnotationValuesByType(Tag)*.stringValue()*.get()
    }
}
