package io.micronaut.core.beans;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultBeanIntrospectorTest {

    private static final String CONTEXT_CLASSLOADER_PROPERTY = "micronaut.introspections.use.context.classloader";

    @TempDir
    Path tempDir;

    @Test
    void sharedIntrospectorFallsBackToBeanTypeClassLoader() throws Exception {
        Path classesDir = tempDir.resolve("classes");
        compileChildLoaderIntrospection(classesDir);
        writeMicronautServiceEntry(classesDir);

        ClassLoader parentClassLoader = getClass().getClassLoader();
        Thread thread = Thread.currentThread();
        ClassLoader previousContextClassLoader = thread.getContextClassLoader();
        String previousProperty = System.getProperty(CONTEXT_CLASSLOADER_PROPERTY);
        try (URLClassLoader childClassLoader = new URLClassLoader(new URL[] { classesDir.toUri().toURL() }, parentClassLoader)) {
            thread.setContextClassLoader(parentClassLoader);
            System.clearProperty(CONTEXT_CLASSLOADER_PROPERTY);

            Class<?> beanType = childClassLoader.loadClass("example.ChildBean");

            assertTrue(BeanIntrospector.SHARED.findIntrospection(beanType).isPresent());
        } finally {
            thread.setContextClassLoader(previousContextClassLoader);
            if (previousProperty == null) {
                System.clearProperty(CONTEXT_CLASSLOADER_PROPERTY);
            } else {
                System.setProperty(CONTEXT_CLASSLOADER_PROPERTY, previousProperty);
            }
        }
    }

    private void compileChildLoaderIntrospection(Path classesDir) throws Exception {
        Path sourceDir = tempDir.resolve("src/example");
        Files.createDirectories(sourceDir);
        Files.createDirectories(classesDir);

        Path beanSource = sourceDir.resolve("ChildBean.java");
        Files.writeString(beanSource, """
            package example;

            public class ChildBean {
            }
            """, StandardCharsets.UTF_8);

        Path introspectionSource = sourceDir.resolve("$ChildBean$Introspection.java");
        Files.writeString(introspectionSource, """
            package example;

            import io.micronaut.core.annotation.AnnotationMetadata;
            import io.micronaut.core.beans.BeanIntrospection;
            import io.micronaut.core.beans.BeanIntrospectionReference;
            import io.micronaut.core.beans.BeanProperty;
            import io.micronaut.core.reflect.exception.InstantiationException;
            import java.lang.annotation.Annotation;
            import java.util.Collection;
            import java.util.List;
            import java.util.Optional;

            public final class $ChildBean$Introspection implements BeanIntrospection<ChildBean>, BeanIntrospectionReference<ChildBean> {
                @Override
                public boolean isPresent() {
                    return true;
                }

                @Override
                public Class<ChildBean> getBeanType() {
                    return ChildBean.class;
                }

                @Override
                public BeanIntrospection<ChildBean> load() {
                    return this;
                }

                @Override
                public String getName() {
                    return ChildBean.class.getName();
                }

                @Override
                public AnnotationMetadata getAnnotationMetadata() {
                    return AnnotationMetadata.EMPTY_METADATA;
                }

                @Override
                public Collection<BeanProperty<ChildBean, Object>> getBeanProperties() {
                    return List.of();
                }

                @Override
                public Collection<BeanProperty<ChildBean, Object>> getIndexedProperties(Class<? extends Annotation> annotationType) {
                    return List.of();
                }

                @Override
                public Optional<BeanProperty<ChildBean, Object>> getIndexedProperty(
                        Class<? extends Annotation> annotationType,
                        String annotationValue) {
                    return Optional.empty();
                }

                @Override
                public Builder<ChildBean> builder() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public ChildBean instantiate() throws InstantiationException {
                    return new ChildBean();
                }

                @Override
                public ChildBean instantiate(boolean strictNullable, Object... arguments) throws InstantiationException {
                    return new ChildBean();
                }
            }
            """, StandardCharsets.UTF_8);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "No system Java compiler available");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> sources = fileManager.getJavaFileObjects(beanSource.toFile(), introspectionSource.toFile());
            Boolean compiled = compiler.getTask(
                null,
                fileManager,
                diagnostics,
                List.of("-classpath", System.getProperty("java.class.path"), "-d", classesDir.toString()),
                null,
                sources
            ).call();
            if (!Boolean.TRUE.equals(compiled)) {
                throw new AssertionError(diagnostics.getDiagnostics().toString());
            }
        }
    }

    private static void writeMicronautServiceEntry(Path classesDir) throws Exception {
        Path serviceDirectory = classesDir.resolve("META-INF/micronaut/io.micronaut.core.beans.BeanIntrospectionReference");
        Files.createDirectories(serviceDirectory);
        Files.writeString(serviceDirectory.resolve("example.$ChildBean$Introspection"), "", StandardCharsets.UTF_8);
    }
}
