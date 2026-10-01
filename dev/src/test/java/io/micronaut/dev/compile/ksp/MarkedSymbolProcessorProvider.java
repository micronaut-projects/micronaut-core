package io.micronaut.dev.compile.ksp;

import com.google.devtools.ksp.processing.CodeGenerator;
import com.google.devtools.ksp.processing.Dependencies;
import com.google.devtools.ksp.processing.Resolver;
import com.google.devtools.ksp.processing.SymbolProcessor;
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment;
import com.google.devtools.ksp.processing.SymbolProcessorProvider;
import com.google.devtools.ksp.symbol.KSAnnotated;
import com.google.devtools.ksp.symbol.KSClassDeclaration;
import com.google.devtools.ksp.symbol.KSFile;
import kotlin.sequences.SequencesKt;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A test symbol processor: for every class annotated {@code @example.Marked} it generates a Kotlin
 * class, a Java class and a resource, each credited to the annotated class's file.
 */
public final class MarkedSymbolProcessorProvider implements SymbolProcessorProvider {

    @Override
    public SymbolProcessor create(SymbolProcessorEnvironment environment) {
        return new MarkedProcessor(environment.getCodeGenerator());
    }

    private static final class MarkedProcessor implements SymbolProcessor {

        private final CodeGenerator generator;
        private final Set<String> done = new HashSet<>();
        private final java.util.TreeSet<String> marked = new java.util.TreeSet<>();

        MarkedProcessor(CodeGenerator generator) {
            this.generator = generator;
        }

        @Override
        public List<KSAnnotated> process(Resolver resolver) {
            for (KSAnnotated symbol : SequencesKt.toList(resolver.getSymbolsWithAnnotation("example.Marked", false))) {
                if (symbol instanceof KSClassDeclaration declaration && declaration.getContainingFile() != null) {
                    String packageName = declaration.getPackageName().asString();
                    String name = declaration.getSimpleName().asString();
                    marked.add(packageName + "." + name);
                    if (!done.add(packageName + "." + name)) {
                        continue;
                    }
                    KSFile file = declaration.getContainingFile();
                    Dependencies dependencies = new Dependencies(false, file);
                    write(dependencies, packageName, name + "Generated", "kt",
                        "package " + packageName + "\nclass " + name + "Generated { fun name(): String = \"generated:" + name + "\" }\n");
                    write(dependencies, packageName, name + "Support", "java",
                        "package " + packageName + ";\npublic class " + name + "Support { public String name() { return \"java:" + name + "\"; } }\n");
                    write(dependencies, "META-INF/generated", packageName + "." + name, "", name);
                }
            }
            return List.of();
        }

        @Override
        public void finish() {
            // an output over every source: which classes are marked, in one file
            write(Dependencies.Companion.getALL_FILES(), "META-INF/generated", "all-marked", "", String.join("\n", marked));
        }

        private void write(Dependencies dependencies, String packageName, String fileName, String extension, String content) {
            try (OutputStream out = generator.createNewFile(dependencies, packageName, fileName, extension)) {
                out.write(content.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
