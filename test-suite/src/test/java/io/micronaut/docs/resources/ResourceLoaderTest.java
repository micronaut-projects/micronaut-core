package io.micronaut.docs.resources;

import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceLoaderTest {

    @Test
    void testExampleForResourceResolver() throws Exception {
        ApplicationContext applicationContext = ApplicationContext.run(
            Map.of("spec.name", "ResourceLoaderTest"),
            "test"
        );
        MyResourceLoader myResourceLoader = applicationContext.getBean(MyResourceLoader.class);

        assertNotNull(myResourceLoader);
        Optional<String> text = myResourceLoader.getClasspathResourceAsText("hello.txt");
        assertTrue(text.isPresent());
        assertEquals("Hello!", text.get().trim());

        applicationContext.stop();
    }
}
