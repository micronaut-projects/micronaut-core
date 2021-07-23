package io.micronaut.docs.inject.anninheritance;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.inject.BeanDefinition;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AnnotationInheritanceSpec {

    @Test
    void testAnnotationInheritance() {
        final Map<String, Object> config = Collections.singletonMap("datasource.url", "jdbc://someurl");
        try (ApplicationContext context = ApplicationContext.run(config)) {
            final BeanDefinition<BookRepository> beanDefinition = context.getBeanDefinition(BookRepository.class);
            final String name = beanDefinition.stringValue(AnnotationUtil.NAMED).orElse(null);
            assertEquals("bookRepository", name);
            assertTrue(beanDefinition.isSingleton());
        }
    }
}
