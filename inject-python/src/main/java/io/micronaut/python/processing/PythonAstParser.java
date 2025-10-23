package io.micronaut.python.processing;

import io.micronaut.python.processing.visitor.ClassDef;
import io.micronaut.python.processing.visitor.DecoratorDef;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.python.embedding.GraalPyResources;
import org.graalvm.python.embedding.VirtualFileSystem;
import org.intellij.lang.annotations.Language;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public class PythonAstParser {

    public static final String PYTHON = "python";
    public static final String INJECT_RESOURCES = "GRAALPY-VFS/io.micronaut/micronaut-inject-python";

    public PythonEnvironment parse(@Language("python") String sources) {
        try (Context context = GraalPyResources.contextBuilder(VirtualFileSystem.newBuilder()
                .resourceDirectory(INJECT_RESOURCES)
                .build())
            // TODO: constrain this in future
            .allowHostAccess(HostAccess.ALL)
            .allowHostClassLookup(name -> name.startsWith("io.micronaut"))
            .build()) {

            Map<String, DecoratorDef> decorators = new LinkedHashMap<>();
            Map<String, ClassDef> classes = new LinkedHashMap<>();
            context.initialize(PYTHON);
            Value bindings = context.getBindings(PYTHON);
            bindings.putMember("callback", (Function<Object, Object>) o -> {
                if (o instanceof ClassDef classDef) {
                    classes.put(classDef.name(), classDef);
                } else if (o instanceof DecoratorDef decoratorDef) {
                    decorators.put(decoratorDef.annotationName(), decoratorDef);
                }
                return o;
            });
            bindings
                .putMember("src", sources);
            context.eval(Source.create(
                PYTHON,
                getSource()
            ));
            return new PythonEnvironment(
                classes,
                decorators
            );
        }
    }


    private static @Language("python") String getSource() {
        return """
            import ast
            import java
            from micronaut_processor import PrintNodeVisitor

            tree = ast.parse(src)
            PrintNodeVisitor(callback).visit(tree)
            """;
    }
}
