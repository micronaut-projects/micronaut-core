package io.micronaut.docs.ioc.mappers;

import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SimpleMapperSpec {
    @Test
    void testSimpleMappers() {
        try (ApplicationContext context = ApplicationContext.run(Collections.singletonMap("spec.name", "SimpleMapperSpec"))) {
            // tag::mappers[]
            ContactMappers contactMappers = context.getBean(ContactMappers.class);
            ContactEntity contactEntity = contactMappers.toEntity(new ContactForm("John", "Snow"));
            assertEquals("John", contactEntity.firstName());
            assertEquals("Snow", contactEntity.lastName());
            // end::mappers[]
        }
    }
}
