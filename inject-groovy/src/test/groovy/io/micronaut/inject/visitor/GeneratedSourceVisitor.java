package io.micronaut.inject.visitor;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.writer.GeneratedFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Generates, as source, a singleton class next to each class annotated with {@link GenerateSource}.
 */
public class GeneratedSourceVisitor implements TypeElementVisitor<GenerateSource, Object> {

    private static List<String> visited = new ArrayList<>();
    /** What reading a streamed file back returned: uri, text content, reader content, input stream content. */
    public static List<String> readBack = new ArrayList<>();
    /** Whether visitGeneratedSourceFile returned a file when called from finish(). */
    public static Boolean fileOfferedInFinish;
    private GeneratedFile lateFile;
    private String lateSource;

    public static List<String> getVisited() {
        return Collections.unmodifiableList(visited);
    }

    public static void clearVisited() {
        visited = new ArrayList<>();
        readBack = new ArrayList<>();
        fileOfferedInFinish = null;
    }

    @Override
    public void finish(VisitorContext context) {
        fileOfferedInFinish = context.visitGeneratedSourceFile("test", "OfferedInFinish").isPresent();
        if (lateFile != null) {
            try {
                lateFile.write(writer -> writer.write(lateSource));
            } catch (Exception e) {
                throw new IllegalStateException(e.getMessage(), e);
            } finally {
                lateFile = null;
            }
        }
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        visited.add(element.getName());
        boolean chain = element.booleanValue(GenerateSource.class, "chain").orElse(false);
        boolean stream = element.booleanValue(GenerateSource.class, "stream").orElse(false);
        String late = element.stringValue(GenerateSource.class, "late").orElse("");
        String body = element.stringValue(GenerateSource.class, "body").orElse("");
        String generatedName = element.getSimpleName() + "Generated";
        Optional<GeneratedFile> file = stream
            ? context.visitGeneratedSourceFile(element.getPackageName(), generatedName)
            : context.visitGeneratedSourceFile(element.getPackageName(), generatedName, element);
        file.ifPresent(sourceFile -> {
                String source = """
                        package %s

                        @jakarta.inject.Singleton
                        @io.micronaut.inject.visitor.GeneratedFrom("%s")
                        %s
                        class %s {
                            %s
                            String origin() { "%s" }
                        }
                        """.formatted(
                        element.getPackageName(),
                        element.getName(),
                        chain ? "@io.micronaut.inject.visitor.GenerateSource" : "",
                        generatedName,
                        body,
                        element.getName());
                try {
                    if (late.equals("write")) {
                        lateFile = sourceFile;
                        lateSource = source;
                        return;
                    }
                    if (stream) {
                        try (OutputStream out = sourceFile.openOutputStream()) {
                            out.write(source.getBytes(StandardCharsets.UTF_8));
                        }
                        readBack.add(sourceFile.toURI().toString());
                        readBack.add(sourceFile.getTextContent().toString());
                        try (Reader reader = sourceFile.openReader()) {
                            StringWriter content = new StringWriter();
                            reader.transferTo(content);
                            readBack.add(content.toString());
                        }
                        try (InputStream in = sourceFile.openInputStream()) {
                            readBack.add(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                        }
                    } else {
                        sourceFile.write(writer -> writer.write(source));
                    }
                    if (late.equals("rewrite")) {
                        lateFile = sourceFile;
                        lateSource = source;
                    }
                } catch (Exception e) {
                    throw new ProcessingException(element, "Failed to generate " + generatedName + ": " + e.getMessage(), e);
                }
            });
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}
