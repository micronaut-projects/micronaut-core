package io.micronaut.inject.visitor.beans

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

import java.lang.annotation.ElementType

class BeanPropertyMemberFlagsSpec extends AbstractTypeElementSpec {

    void "a member tells whether the generated dispatch reads it through reflection"() {
        given:
        def introspection = buildBeanIntrospection('test.Person', '''
package test;

import io.micronaut.core.annotation.Introspected;

@Introspected(members = true)
class Person {
    private String name;
    String nick;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNick() { return nick; }
    public void setNick(String nick) { this.nick = nick; }
}
''')
        def name = introspection.getRequiredProperty('name', String).members
        def nick = introspection.getRequiredProperty('nick', String).members

        expect: 'the private field is read reflectively, the getter and the package private field are not'
        name.find { it.elementType == ElementType.FIELD }.reflectionRequired
        !name.find { it.name == 'getName' }.reflectionRequired
        !nick.find { it.elementType == ElementType.FIELD }.reflectionRequired

        and: 'no member of a plain class is synthetic'
        !(name + nick).any { it.synthetic }
    }

    void "the accessor of a record component is synthetic beside the component field"() {
        given:
        def introspection = buildBeanIntrospection('test.Point', '''
package test;

import io.micronaut.core.annotation.Introspected;

@Introspected(members = true)
record Point(int x, String label) {
}
''')
        def x = introspection.getRequiredProperty('x', int).members

        expect:
        x*.elementType as Set == [ElementType.FIELD, ElementType.METHOD] as Set
        x.find { it.elementType == ElementType.METHOD }.synthetic
        !x.find { it.elementType == ElementType.FIELD }.synthetic
    }
}
