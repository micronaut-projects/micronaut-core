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
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The processors write a descriptor of each bean definition into its {@code META-INF/micronaut} entry. A native image
 * finds the definitions by the names of the entries and reads no descriptor, so it keeps the entries without their
 * content. The test runs on the JVM too, where the entries have it.
 *
 * <p>The resources of the test include the entries with a glob as well, as code that opens them in an image would, so
 * that the exclusion in the resource configuration of {@code micronaut-inject} is tested against a glob too.</p>
 */
class BeanDefinitionEntriesTest {

    private static final String SERVICE = BeanDefinitionReference.class.getName();
    private static final String DIRECTORY = "META-INF/micronaut/" + SERVICE;
    private static final String ENTRIES = DIRECTORY + "/";
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

    @Test
    void theDirectoryListsTheEntries() throws IOException {
        // on the JVM a directory in a jar has no content: what lists it there is the loader of the services
        assumeTrue(NativeImageUtils.inImageRuntimeCode(), "a directory is listed by its content in an image");
        Set<String> listed = new TreeSet<>();
        // one listing for each jar or directory of the class path the image was built from
        Enumeration<URL> directories = classLoader.getResources(DIRECTORY);
        while (directories.hasMoreElements()) {
            try (InputStream in = directories.nextElement().openStream()) {
                for (String name : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                    if (!name.isEmpty()) {
                        listed.add(name);
                    }
                }
            }
        }
        // the definitions are found by names that a listing gives too, and each name it gives is an entry, empty
        Set<String> names = entryNames();
        assertTrue(listed.containsAll(names), () -> "not listed: " + names.stream().filter(n -> !listed.contains(n)).toList());
        for (String name : listed) {
            assertEquals(0, content(name).length, name);
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
