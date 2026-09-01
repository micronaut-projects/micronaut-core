package io.micronaut.annotation.processing;

import io.micronaut.core.io.service.ServiceAggregator;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.ClassWriterOutputVisitor;
import io.micronaut.inject.writer.GeneratedFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.annotation.processing.Filer;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the incremental compilation contract of the generated service aggregator.
 *
 * <p>An aggregator is derived from every bean in the module, which Gradle's <em>isolating</em>
 * annotation processor contract forbids: it allows a generated file only one originating element.
 * Gradle enforces that by inspecting what the processor declares, and when it sees more than one it
 * refuses to compile incrementally and falls back to recompiling the module:</p>
 *
 * <pre>
 * Full recompilation is required because the generated resource
 * 'META-INF/services/io.micronaut.core.io.service.ServiceAggregator in CLASS_OUTPUT'
 * must have exactly one originating element, but had 200.
 * </pre>
 *
 * <p>That fallback is what keeps the output correct, and it depends entirely on the aggregator
 * declaring <em>all</em> of its originating elements. Declaring one - which looks like a harmless
 * simplification, and is what {@code AnnotationProcessingOutputVisitor} does for classes under a
 * Gradle filer - would silence Gradle's check and let it recompile incrementally. It would then
 * regenerate the aggregator from only the types it recompiled, and every other bean in the module
 * would silently disappear from the context at runtime.</p>
 *
 * <p>So this asserts the property Gradle keys off, rather than the compiler behaviour itself, which
 * cannot be reproduced without running Gradle.</p>
 */
class ServiceAggregationIncrementalContractTest {

    private static final String SERVICE = "io.micronaut.inject.BeanDefinitionReference";
    private static final String SERVICES_FILE = "services/" + ServiceAggregator.SERVICE_NAME;

    private Filer filer;
    private VisitorContext visitorContext;

    @BeforeEach
    void setUp() {
        // the filer is only ever a key into the per-compilation state, and the visitor context is
        // only asked for its options, so stubs are enough and keep this free of a mocking dependency
        filer = stub(Filer.class, Map.of());
        visitorContext = stub(VisitorContext.class, Map.of(
            "getOptions", Map.of(ServiceAggregation.OPTION_CLASS_NAME, "com.example.$ServiceAggregator")
        ));
        ServiceAggregation.configure(filer, Map.of(ServiceAggregation.OPTION_ENABLED, "true"));
    }

    @Test
    void declaresEveryOriginatingElementSoGradleFallsBackToAFullRecompile() {
        RecordingOutputVisitor outputVisitor = new RecordingOutputVisitor();
        for (int i = 0; i < 5; i++) {
            ServiceAggregation.add(filer, SERVICE, "com.example.$Bean" + i + "$Definition", element("com.example.Bean" + i));
        }

        ServiceAggregation.write(filer, visitorContext, outputVisitor);

        Element[] declared = outputVisitor.originatingElements.get(SERVICES_FILE);
        assertEquals(5, declared.length,
            "the services entry must declare one originating element per bean; declaring fewer would "
                + "let Gradle compile incrementally and silently drop beans from the aggregator");
    }

    @Test
    void aggregatesEveryBeanIntoOneEntry() {
        RecordingOutputVisitor outputVisitor = new RecordingOutputVisitor();
        for (int i = 0; i < 5; i++) {
            ServiceAggregation.add(filer, SERVICE, "com.example.$Bean" + i + "$Definition", element("com.example.Bean" + i));
        }

        ServiceAggregation.write(filer, visitorContext, outputVisitor);

        assertEquals(List.of("com.example.$ServiceAggregator"), outputVisitor.classesWritten);
        assertTrue(outputVisitor.written.get(SERVICES_FILE).contains("com.example.$ServiceAggregator"));
    }

    @Test
    void declinesFurtherEntriesOnceWritten() {
        RecordingOutputVisitor outputVisitor = new RecordingOutputVisitor();
        ServiceAggregation.add(filer, SERVICE, "com.example.$Bean0$Definition", element("com.example.Bean0"));
        ServiceAggregation.write(filer, visitorContext, outputVisitor);

        // a processor that emits in the final round must fall back to a marker file rather than
        // collide with the aggregator that has already been written
        boolean buffered = ServiceAggregation.add(filer, SERVICE, "com.example.$Late$Definition", element("com.example.Late"));

        assertFalse(buffered, "add() must decline once the aggregator has been written");
    }

    private static Element element(String name) {
        return stub(Element.class, Map.of("getName", name));
    }

    /**
     * A proxy answering the named methods and returning a harmless default for everything else.
     */
    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type, Map<String, Object> answers) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
            Object answer = answers.get(method.getName());
            if (answer != null) {
                return answer;
            }
            return switch (method.getReturnType().getName()) {
                case "boolean" -> false;
                case "int" -> 0;
                case "long" -> 0L;
                default -> null;
            };
        });
    }

    /**
     * Records what was written and, crucially, the originating elements declared with it.
     */
    private static final class RecordingOutputVisitor implements ClassWriterOutputVisitor {

        private final Map<String, Element[]> originatingElements = new LinkedHashMap<>();
        private final Map<String, String> written = new LinkedHashMap<>();
        private final List<String> classesWritten = new ArrayList<>();

        @Override
        public OutputStream visitClass(String classname, Element... originatingElements) {
            classesWritten.add(classname);
            return OutputStream.nullOutputStream();
        }

        @Override
        public void visitServiceDescriptor(String type, String classname) {
        }

        @Override
        public void visitServiceDescriptor(String type, String classname, Element originatingElement) {
        }

        @Override
        public Optional<GeneratedFile> visitMetaInfFile(String path, Element... originatingElements) {
            this.originatingElements.put(path, originatingElements);
            return Optional.of(new RecordingFile(path, written));
        }

        @Override
        public Optional<GeneratedFile> visitGeneratedFile(String path) {
            return Optional.empty();
        }

        @Override
        public Optional<GeneratedFile> visitGeneratedFile(String path, Element... originatingElements) {
            return Optional.empty();
        }

        @Override
        public Optional<GeneratedFile> visitGeneratedSourceFile(String packageName, String fileNameWithoutExtension, Element... originatingElements) {
            return Optional.empty();
        }

        @Override
        public void finish() {
        }

        @Override
        public Map<String, java.util.Set<String>> getServiceEntries() {
            return Map.of();
        }
    }

    private record RecordingFile(String path, Map<String, String> sink) implements GeneratedFile {

        @Override
        public URI toURI() {
            return URI.create("mem:/" + path);
        }

        @Override
        public String getName() {
            return path;
        }

        @Override
        public Writer openWriter() {
            return new StringWriter() {
                @Override
                public void close() {
                    sink.put(path, toString());
                }
            };
        }

        @Override
        public OutputStream openOutputStream() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public java.io.InputStream openInputStream() throws IOException {
            throw new IOException("not readable");
        }

        @Override
        public Reader openReader() throws IOException {
            throw new IOException("not readable");
        }

        @Override
        public CharSequence getTextContent() {
            return sink.getOrDefault(path, "");
        }
    }
}
