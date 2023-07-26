package io.micronaut.docs.ioc.mappers

import io.micronaut.context.BeanContext
import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import static org.junit.jupiter.api.Assertions.assertEquals

@Property(name = "spec.name", value = "SimpleMapperSpec")
@MicronautTest(startApplication = false)
class SimpleMapperSpec extends Specification {

    @Inject
    BeanContext context;
    void "testSimpleMappers"() {
        // tag::mappers[]
        ContactMappers contactMappers = context.getBean(ContactMappers)
        ContactEntity contactEntity = contactMappers.toEntity(new ContactForm(firstName: "John", lastName: "Snow"))
        assertEquals("John", contactEntity.firstName)
        assertEquals("Snow", contactEntity.lastName)
        // end::mappers[]
    }
}
