package example.micronaut;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.DefaultBeanDefinitionsProvider;
import io.micronaut.core.io.service.MicronautMetaServiceLoaderUtils;
import io.micronaut.core.util.NativeImageUtils;
import io.micronaut.inject.BeanDefinitionReference;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The processors write a descriptor of each bean definition into its {@code META-INF/micronaut} entry. A native image
 * finds the definitions by the names of the entries and reads no descriptor, so it keeps the entries without their
 * content. The test runs on the JVM too, where the entries have it.
 */
class BeanDefinitionEntriesTest {

    private static final String SERVICE = BeanDefinitionReference.class.getName();
    private static final String ENTRIES = "META-INF/micronaut/" + SERVICE + "/";
    private static final String CONTROLLER = "example.micronaut.$HelloController$Definition";

    private final ClassLoader classLoader = BeanDefinitionEntriesTest.class.getClassLoader();

    @Test
    void theEntriesAreKeptWithoutTheirContent() throws IOException {
        Set<String> names = entryNames();
        for (String name : names) {
            byte[] content = content(name);
            if (NativeImageUtils.inImageRuntimeCode()) {
                assertEquals(0, content.length, name);
            }
        }
        if (!NativeImageUtils.inImageRuntimeCode()) {
            // the descriptor that the build writes and an image leaves out
            assertTrue(content(CONTROLLER).length > 0);
        }
    }

    @Test
    void aDefinitionIsFoundForEachEntry() throws IOException {
        Set<String> names = entryNames();
        Set<String> found = new TreeSet<>();
        for (BeanDefinitionReference<?> reference : new DefaultBeanDefinitionsProvider().provide(classLoader)) {
            found.add(reference.getBeanDefinitionName());
        }
        if (NativeImageUtils.inImageRuntimeCode()) {
            // the build leaves out the entries whose definitions cannot be loaded
            assertEquals(names, found);
        } else {
            assertTrue(names.containsAll(found));
        }
        assertTrue(found.contains(CONTROLLER));

        try (ApplicationContext context = ApplicationContext.run()) {
            assertTrue(context.findBeanDefinition(HelloController.class).isPresent());
        }
    }

    private Set<String> entryNames() throws IOException {
        Set<String> names = new TreeSet<>(MicronautMetaServiceLoaderUtils.findMicronautMetaServiceEntries(classLoader, SERVICE));
        assertTrue(names.contains(CONTROLLER), CONTROLLER);
        return names;
    }

    private byte[] content(String name) throws IOException {
        URL entry = classLoader.getResource(ENTRIES + name);
        assertNotNull(entry, name);
        try (InputStream in = entry.openStream()) {
            return in.readAllBytes();
        }
    }
}
