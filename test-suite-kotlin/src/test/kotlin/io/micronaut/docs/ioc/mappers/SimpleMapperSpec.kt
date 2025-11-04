package io.micronaut.docs.ioc.mappers

import io.micronaut.context.ApplicationContext
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.*

class SimpleMapperSpec {
    @Test
    fun testSimpleMappers() {
        ApplicationContext.run(Collections.singletonMap<String, Any>("spec.name", "SimpleMapperSpec")).use { context ->
            // tag::mappers[]
            val contactMappers = context.getBean(ContactMappers::class.java)
            val entity : ContactEntity = contactMappers.toEntity(ContactForm("John", "Snow"))
            Assertions.assertEquals("John", entity.firstName)
            Assertions.assertEquals("Snow", entity.lastName)
            // end::mappers[]
        }
    }
}
