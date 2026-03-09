package io.micronaut.micronautc;

import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MicronautcNativeTest {

    @Test
    void nativeMicronautcHelpMatchesJavacHelp() throws Exception {
        String executable = System.getProperty("micronautc.executable");
        assertTrue(executable != null && !executable.isBlank(), "micronautc.executable must be set");

        String micronautcHelp = runCommand(List.of(executable, "--help"));
        String javacHelp = runCommand(List.of(resolveJavacExecutable(), "--help"));

        assertEquals(normalizeOutput(javacHelp), normalizeOutput(micronautcHelp));
    }

    @Test
    void nativeMicronautcCompilesAndProducesInjectableBeans(@TempDir Path tempDir) throws Exception {
        String executable = System.getProperty("micronautc.executable");
        String compileClasspath = System.getProperty("micronautc.compile.classpath");
        assertTrue(executable != null && !executable.isBlank(), "micronautc.executable must be set");
        assertTrue(compileClasspath != null && !compileClasspath.isBlank(), "micronautc.compile.classpath must be set");

        Path sourcesDir = tempDir.resolve("src").resolve("example");
        Path probeClassesDir = tempDir.resolve("probe-classes");
        Path mapperProbeClassesDir = tempDir.resolve("mapper-probe-classes");
        Path transformerProbeClassesDir = tempDir.resolve("transformer-probe-classes");
        Path classesDir = tempDir.resolve("classes");
        Files.createDirectories(sourcesDir);
        Files.createDirectories(probeClassesDir);
        Files.createDirectories(mapperProbeClassesDir);
        Files.createDirectories(transformerProbeClassesDir);
        Files.createDirectories(classesDir);

        Path greeterSource = sourcesDir.resolve("Greeter.java");
        Path helloServiceSource = sourcesDir.resolve("HelloService.java");
        Path mapperProbeSource = sourcesDir.resolve("MapperProbe.java");
        Path transformerProbeSource = sourcesDir.resolve("TransformerProbe.java");
        Path customVisitorJar = createCustomVisitorJar(tempDir, compileClasspath);
        Path customAnnotationServicesJar = createCustomAnnotationServicesJar(tempDir, compileClasspath);

        Files.writeString(greeterSource, """
            package example;

            import jakarta.inject.Singleton;

            @Singleton
            public class Greeter {
                public String greet() {
                    return \"hello\";
                }
            }
            """);

        Files.writeString(helloServiceSource, """
            package example;

            import jakarta.inject.Singleton;

            @Singleton
            public class HelloService {
                private final Greeter greeter;

                public HelloService(Greeter greeter) {
                    this.greeter = greeter;
                }

                public String message() {
                    return greeter.greet();
                }
            }
            """);

        Files.writeString(mapperProbeSource, """
            package example;

            import jakarta.inject.Singleton;

            @Singleton
            @TriggerMapper
            class MapperProbe {
            }

            @interface TriggerMapper {
            }
            """);

        Files.writeString(transformerProbeSource, """
            package example;

            import jakarta.inject.Singleton;

            @Singleton
            @TriggerTransformer
            class TransformerProbe {
            }

            @interface TriggerTransformer {
            }
            """);

        List<String> probeCommand = new ArrayList<>();
        probeCommand.add(executable);
        probeCommand.add("-d");
        probeCommand.add(probeClassesDir.toString());
        probeCommand.add("-processorpath");
        probeCommand.add(customVisitorJar.toString());
        probeCommand.add("-classpath");
        probeCommand.add(compileClasspath);
        probeCommand.add(greeterSource.toString());
        probeCommand.add(helloServiceSource.toString());

        ProcessBuilder processBuilder = new ProcessBuilder(probeCommand);
        processBuilder.environment().put("MICRONAUTC_JAVA_HOME", System.getProperty("java.home"));
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        String probeOutput = readOutput(process.getInputStream());
        int probeExitCode = process.waitFor();
        assertTrue(probeOutput.contains("CUSTOM_VISITOR_RAN"), () -> "custom visitor did not run:\n" + probeOutput);
        assertNotEquals(0, probeExitCode, () -> "custom visitor probe should fail compilation:\n" + probeOutput);

        List<String> mapperProbeCommand = new ArrayList<>();
        mapperProbeCommand.add(executable);
        mapperProbeCommand.add("-d");
        mapperProbeCommand.add(mapperProbeClassesDir.toString());
        mapperProbeCommand.add("-processorpath");
        mapperProbeCommand.add(customAnnotationServicesJar.toString());
        mapperProbeCommand.add("-classpath");
        mapperProbeCommand.add(compileClasspath);
        mapperProbeCommand.add(mapperProbeSource.toString());

        processBuilder = new ProcessBuilder(mapperProbeCommand);
        processBuilder.environment().put("MICRONAUTC_JAVA_HOME", System.getProperty("java.home"));
        processBuilder.redirectErrorStream(true);
        process = processBuilder.start();
        String mapperProbeOutput = readOutput(process.getInputStream());
        int mapperProbeExitCode = process.waitFor();
        assertTrue(mapperProbeOutput.contains("CUSTOM_MAPPER_RAN"), () -> "custom mapper did not run:\n" + mapperProbeOutput);
        assertNotEquals(0, mapperProbeExitCode, () -> "custom mapper probe should fail compilation:\n" + mapperProbeOutput);

        List<String> transformerProbeCommand = new ArrayList<>();
        transformerProbeCommand.add(executable);
        transformerProbeCommand.add("-d");
        transformerProbeCommand.add(transformerProbeClassesDir.toString());
        transformerProbeCommand.add("-processorpath");
        transformerProbeCommand.add(customAnnotationServicesJar.toString());
        transformerProbeCommand.add("-classpath");
        transformerProbeCommand.add(compileClasspath);
        transformerProbeCommand.add(transformerProbeSource.toString());

        processBuilder = new ProcessBuilder(transformerProbeCommand);
        processBuilder.environment().put("MICRONAUTC_JAVA_HOME", System.getProperty("java.home"));
        processBuilder.redirectErrorStream(true);
        process = processBuilder.start();
        String transformerProbeOutput = readOutput(process.getInputStream());
        int transformerProbeExitCode = process.waitFor();
        assertTrue(transformerProbeOutput.contains("CUSTOM_TRANSFORMER_RAN"), () -> "custom transformer did not run:\n" + transformerProbeOutput);
        assertNotEquals(0, transformerProbeExitCode, () -> "custom transformer probe should fail compilation:\n" + transformerProbeOutput);

        List<String> command = new ArrayList<>();
        command.add(executable);
        command.add("-d");
        command.add(classesDir.toString());
        command.add("-classpath");
        command.add(compileClasspath);
        command.add(greeterSource.toString());
        command.add(helloServiceSource.toString());

        processBuilder = new ProcessBuilder(command);
        processBuilder.environment().put("MICRONAUTC_JAVA_HOME", System.getProperty("java.home"));
        processBuilder.redirectErrorStream(true);
        process = processBuilder.start();
        String output = readOutput(process.getInputStream());
        int exitCode = process.waitFor();
        assertEquals(0, exitCode, () -> "micronautc failed:\n" + output);

        Path beanRefPath = classesDir.resolve("META-INF/micronaut/io.micronaut.inject.BeanDefinitionReference");
        assertTrue(Files.isDirectory(beanRefPath), "BeanDefinitionReference metadata directory must exist");
        try (Stream<Path> files = Files.list(beanRefPath)) {
            assertFalse(files.findAny().isEmpty(), "BeanDefinitionReference metadata must be generated");
        }

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{classesDir.toUri().toURL()}, getClass().getClassLoader());
             ApplicationContext applicationContext = ApplicationContext.builder().classLoader(classLoader).build().start()) {
            Class<?> helloServiceClass = classLoader.loadClass("example.HelloService");
            Object helloService = applicationContext.getBean(helloServiceClass);
            Method messageMethod = helloServiceClass.getMethod("message");
            assertEquals("hello", messageMethod.invoke(helloService));
        }
    }

    @Test
    void nativeMicronautcGeneratesIntrospectionForIntrospectedTypes(@TempDir Path tempDir) throws Exception {
        String executable = System.getProperty("micronautc.executable");
        String compileClasspath = System.getProperty("micronautc.compile.classpath");
        assertTrue(executable != null && !executable.isBlank(), "micronautc.executable must be set");
        assertTrue(compileClasspath != null && !compileClasspath.isBlank(), "micronautc.compile.classpath must be set");

        Path sourcesDir = tempDir.resolve("src").resolve("example");
        Path classesDir = tempDir.resolve("classes");
        Files.createDirectories(sourcesDir);
        Files.createDirectories(classesDir);

        Path introspectedBeanSource = sourcesDir.resolve("IntrospectedBean.java");
        Files.writeString(introspectedBeanSource, """
            package example;

            import io.micronaut.core.annotation.Introspected;

            @Introspected
            public class IntrospectedBean {
                private String name;

                public String getName() {
                    return name;
                }

                public void setName(String name) {
                    this.name = name;
                }
            }
            """);

        List<String> command = new ArrayList<>();
        command.add(executable);
        command.add("-d");
        command.add(classesDir.toString());
        command.add("-classpath");
        command.add(compileClasspath);
        command.add(introspectedBeanSource.toString());

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.environment().put("MICRONAUTC_JAVA_HOME", System.getProperty("java.home"));
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        String output = readOutput(process.getInputStream());
        int exitCode = process.waitFor();
        assertEquals(0, exitCode, () -> "micronautc failed:\n" + output);

        Path introspectionRefPath = classesDir.resolve("META-INF/micronaut/io.micronaut.core.beans.BeanIntrospectionReference");
        assertTrue(Files.isDirectory(introspectionRefPath), "BeanIntrospectionReference metadata directory must exist");
        try (Stream<Path> files = Files.list(introspectionRefPath)) {
            assertFalse(files.findAny().isEmpty(), "BeanIntrospectionReference metadata must be generated");
        }

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{classesDir.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> beanClass = classLoader.loadClass("example.IntrospectedBean");
            Class<?> beanIntrospectorClass = classLoader.loadClass("io.micronaut.core.beans.BeanIntrospector");
            Class<?> beanIntrospectionClass = classLoader.loadClass("io.micronaut.core.beans.BeanIntrospection");
            Object beanIntrospector = beanIntrospectorClass.getMethod("forClassLoader", ClassLoader.class).invoke(null, classLoader);
            Object beanIntrospection = beanIntrospectorClass.getMethod("getIntrospection", Class.class).invoke(beanIntrospector, beanClass);
            assertEquals(beanClass, beanIntrospectionClass.getMethod("getBeanType").invoke(beanIntrospection));

            Object bean = beanIntrospectionClass.getMethod("instantiate").invoke(beanIntrospection);
            Object beanProperty = beanIntrospectionClass
                .getMethod("getRequiredProperty", String.class, Class.class)
                .invoke(beanIntrospection, "name", String.class);
            Class<?> beanPropertyClass = classLoader.loadClass("io.micronaut.core.beans.BeanProperty");
            beanPropertyClass.getMethod("set", Object.class, Object.class).invoke(beanProperty, bean, "micronautc");
            assertEquals("micronautc", beanPropertyClass.getMethod("get", Object.class).invoke(beanProperty, bean));
        }
    }

    private static String readOutput(InputStream inputStream) throws IOException {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static String runCommand(List<String> command) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.environment().put("MICRONAUTC_JAVA_HOME", System.getProperty("java.home"));
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        String output = readOutput(process.getInputStream());
        int exitCode = process.waitFor();
        assertEquals(0, exitCode, () -> "command failed " + command + ":\n" + output);
        return output;
    }

    private static String resolveJavacExecutable() {
        String javaHome = System.getProperty("java.home");
        String javac = isWindows() ? "javac.exe" : "javac";
        Path javacPath = Path.of(javaHome, "bin", javac);
        return javacPath.toString();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }

    private static String normalizeOutput(String output) {
        return output.replace("\r\n", "\n").trim();
    }

    private static Path createCustomVisitorJar(Path tempDir, String compileClasspath) throws IOException {
        Path visitorSourcesDir = tempDir.resolve("visitor-src").resolve("custom").resolve("visitor");
        Path visitorClassesDir = tempDir.resolve("visitor-classes");
        Path visitorSource = visitorSourcesDir.resolve("MarkerVisitor.java");

        Files.createDirectories(visitorSourcesDir);
        Files.createDirectories(visitorClassesDir);
        Files.writeString(visitorSource, """
            package custom.visitor;

            import io.micronaut.inject.ast.ClassElement;
            import io.micronaut.inject.visitor.TypeElementVisitor;
            import io.micronaut.inject.visitor.VisitorContext;

            public final class MarkerVisitor implements TypeElementVisitor<Object, Object> {
                @Override
                public VisitorKind getVisitorKind() {
                    return VisitorKind.ISOLATING;
                }

                @Override
                public void visitClass(ClassElement element, VisitorContext context) {
                    if (!"example.HelloService".equals(element.getName())) {
                        return;
                    }
                    throw new IllegalStateException("CUSTOM_VISITOR_RAN");
                }
            }
            """);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertTrue(compiler != null, "System Java compiler not available for test visitor compilation");
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjects(visitorSource.toFile());
            List<String> options = List.of(
                "-classpath", compileClasspath,
                "-d", visitorClassesDir.toString()
            );
            Boolean compiled = compiler.getTask(null, fileManager, null, options, null, compilationUnits).call();
            assertTrue(Boolean.TRUE.equals(compiled), "Failed to compile custom type element visitor");
        }

        Path serviceFile = visitorClassesDir.resolve("META-INF/services/io.micronaut.inject.visitor.TypeElementVisitor");
        Files.createDirectories(serviceFile.getParent());
        Files.writeString(serviceFile, "custom.visitor.MarkerVisitor\n");

        Path visitorJar = tempDir.resolve("custom-visitor.jar");
        createJar(visitorClassesDir, visitorJar);
        return visitorJar;
    }

    private static Path createCustomAnnotationServicesJar(Path tempDir, String compileClasspath) throws IOException {
        Path annotationSourcesDir = tempDir.resolve("annotation-service-src").resolve("custom").resolve("annotation");
        Path annotationClassesDir = tempDir.resolve("annotation-service-classes");
        Path mapperSource = annotationSourcesDir.resolve("MarkerMapper.java");
        Path transformerSource = annotationSourcesDir.resolve("MarkerTransformer.java");

        Files.createDirectories(annotationSourcesDir);
        Files.createDirectories(annotationClassesDir);
        Files.writeString(mapperSource, """
            package custom.annotation;

            import io.micronaut.core.annotation.AnnotationValue;
            import io.micronaut.inject.annotation.NamedAnnotationMapper;
            import io.micronaut.inject.visitor.VisitorContext;

            import java.lang.annotation.Annotation;
            import java.util.List;

            public final class MarkerMapper implements NamedAnnotationMapper {
                @Override
                public String getName() {
                    return "example.TriggerMapper";
                }

                @Override
                public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
                    throw new IllegalStateException("CUSTOM_MAPPER_RAN");
                }
            }
            """);

        Files.writeString(transformerSource, """
            package custom.annotation;

            import io.micronaut.core.annotation.AnnotationValue;
            import io.micronaut.inject.annotation.NamedAnnotationTransformer;
            import io.micronaut.inject.visitor.VisitorContext;

            import java.lang.annotation.Annotation;
            import java.util.List;

            public final class MarkerTransformer implements NamedAnnotationTransformer {
                @Override
                public String getName() {
                    return "example.TriggerTransformer";
                }

                @Override
                public List<AnnotationValue<?>> transform(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
                    throw new IllegalStateException("CUSTOM_TRANSFORMER_RAN");
                }
            }
            """);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertTrue(compiler != null, "System Java compiler not available for annotation service compilation");
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjects(mapperSource.toFile(), transformerSource.toFile());
            List<String> options = List.of(
                "-classpath", compileClasspath,
                "-d", annotationClassesDir.toString()
            );
            Boolean compiled = compiler.getTask(null, fileManager, null, options, null, compilationUnits).call();
            assertTrue(Boolean.TRUE.equals(compiled), "Failed to compile custom annotation services");
        }

        Path mapperServiceFile = annotationClassesDir.resolve("META-INF/services/io.micronaut.inject.annotation.AnnotationMapper");
        Files.createDirectories(mapperServiceFile.getParent());
        Files.writeString(mapperServiceFile, "custom.annotation.MarkerMapper\n");

        Path transformerServiceFile = annotationClassesDir.resolve("META-INF/services/io.micronaut.inject.annotation.AnnotationTransformer");
        Files.createDirectories(transformerServiceFile.getParent());
        Files.writeString(transformerServiceFile, "custom.annotation.MarkerTransformer\n");

        Path serviceJar = tempDir.resolve("custom-annotation-services.jar");
        createJar(annotationClassesDir, serviceJar);
        return serviceJar;
    }

    private static void createJar(Path inputDir, Path jarFile) throws IOException {
        try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarFile));
             Stream<Path> paths = Files.walk(inputDir).sorted(Comparator.naturalOrder())) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                JarEntry entry = new JarEntry(inputDir.relativize(path).toString().replace('\\', '/'));
                try {
                    jarOutputStream.putNextEntry(entry);
                    jarOutputStream.write(Files.readAllBytes(path));
                    jarOutputStream.closeEntry();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }
}
