package io.micronaut.dev.compile.ksp;

import com.google.devtools.ksp.processing.CodeGenerator;
import com.google.devtools.ksp.processing.Dependencies;
import com.google.devtools.ksp.processing.KSPLogger;
import com.google.devtools.ksp.processing.Resolver;
import com.google.devtools.ksp.processing.SymbolProcessor;
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment;
import com.google.devtools.ksp.processing.SymbolProcessorProvider;
import com.google.devtools.ksp.symbol.KSAnnotated;
import com.google.devtools.ksp.symbol.KSClassDeclaration;
import com.google.devtools.ksp.symbol.KSFile;
import com.google.devtools.ksp.symbol.KSFunctionDeclaration;
import com.google.devtools.ksp.symbol.KSPropertyDeclaration;
import kotlin.sequences.SequencesKt;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * A test symbol processor: for every class annotated {@code @example.Marked} it generates a Kotlin
 * class, a Java class and resources, each credited to the annotated class's file: the Kotlin class by its
 * dependencies, the others by every association the code generator offers. A class named {@code Warned} draws a
 * warning, one named {@code Refused} an error, and the processor options are written to a resource.
 */
public final class MarkedSymbolProcessorProvider implements SymbolProcessorProvider {

    @Override
    public SymbolProcessor create(SymbolProcessorEnvironment environment) {
        return new MarkedProcessor(environment.getCodeGenerator(), environment.getLogger(), environment.getOptions());
    }

    private static final class MarkedProcessor implements SymbolProcessor {

        private final CodeGenerator generator;
        private final KSPLogger logger;
        private final Map<String, String> options;
        private final Set<String> done = new HashSet<>();
        private final java.util.TreeSet<String> marked = new java.util.TreeSet<>();

        MarkedProcessor(CodeGenerator generator, KSPLogger logger, Map<String, String> options) {
            this.generator = generator;
            this.logger = logger;
            this.options = options;
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
                    if (name.equals("Warned")) {
                        logger.warn("marked class " + name + " is warned", declaration);
                    } else if (name.equals("Refused")) {
                        logger.error("marked class " + name + " is refused", declaration);
                    }
                    KSFile file = declaration.getContainingFile();
                    Dependencies none = new Dependencies(false);
                    write(new Dependencies(false, file), packageName, name + "Generated", "kt",
                        "package " + packageName + "\nclass " + name + "Generated { fun name(): String = \"generated:" + name + "\" }\n");
                    write(none, packageName, name + "Support", "java",
                        "package " + packageName + ";\npublic class " + name + "Support { public String name() { return \"java:" + name + "\"; } }\n");
                    generator.associateWithClasses(List.of(declaration), packageName, name + "Support", "java");
                    String resource = "META-INF/generated/" + packageName + "." + name;
                    writeByPath(none, resource, name);
                    generator.associate(List.of(file), "META-INF/generated", packageName + "." + name, "");
                    List<KSFunctionDeclaration> functions = SequencesKt.toList(SequencesKt.filterIsInstance(declaration.getDeclarations(), KSFunctionDeclaration.class));
                    List<KSPropertyDeclaration> properties = SequencesKt.toList(declaration.getAllProperties());
                    writeByPath(none, resource + ".members", functions.size() + " functions, " + properties.size() + " properties");
                    generator.associateWithFunctions(functions, "META-INF/generated", packageName + "." + name + ".members", "");
                    generator.associateWithProperties(properties, "META-INF/generated", packageName + "." + name + ".members", "");
                    generator.associateByPath(List.of(file), resource + ".members", "");
                }
            }
            return List.of();
        }

        @Override
        public void finish() {
            // an output over every source: which classes are marked, in one file
            write(Dependencies.Companion.getALL_FILES(), "META-INF/generated", "all-marked", "", String.join("\n", marked));
            if (!options.isEmpty()) {
                StringBuilder written = new StringBuilder();
                new TreeMap<>(options).forEach((key, value) -> written.append(key).append('=').append(value).append('\n'));
                write(Dependencies.Companion.getALL_FILES(), "META-INF/generated", "options", "", written.toString());
            }
        }

        private void write(Dependencies dependencies, String packageName, String fileName, String extension, String content) {
            write(generator.createNewFile(dependencies, packageName, fileName, extension), content);
        }

        private void writeByPath(Dependencies dependencies, String path, String content) {
            write(generator.createNewFileByPath(dependencies, path, ""), content);
        }

        private static void write(OutputStream stream, String content) {
            try (OutputStream out = stream) {
                out.write(content.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
