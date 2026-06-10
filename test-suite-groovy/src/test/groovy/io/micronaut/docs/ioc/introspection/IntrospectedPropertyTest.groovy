package io.micronaut.docs.ioc.introspection

import io.micronaut.core.annotation.Introspected
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.BeanProperty
import spock.lang.Specification

class IntrospectedPropertyTest extends Specification {

    void "test introspected property metadata"() {
        when:
        // tag::metadata[]
        BeanIntrospection<Book> introspection = BeanIntrospection.getIntrospection(Book)
        BeanProperty<Book, String> property = introspection.getRequiredProperty('title', String)
        Optional<String> externalName = property.stringValue(Introspected.Property, 'name')
        // end::metadata[]

        then:
        externalName.orElseThrow() == 'book_title'
    }

    void "test Jackson property metadata"() {
        when:
        BeanIntrospection<User> introspection = BeanIntrospection.getIntrospection(User)
        BeanProperty<User, String> property = introspection.getRequiredProperty('displayName', String)

        then:
        property.stringValue(Introspected.Property, 'name').orElseThrow() == 'display_name'
    }
}
