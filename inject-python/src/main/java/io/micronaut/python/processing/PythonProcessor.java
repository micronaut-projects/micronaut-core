package io.micronaut.python.processing;

import io.micronaut.inject.ast.Element;
import io.micronaut.python.processing.visitor.ClassDef;
import io.micronaut.python.processing.visitor.FunctionDef;
import io.micronaut.python.processing.visitor.PythonClassElement;
import io.micronaut.python.processing.visitor.PythonMethodElement;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.python.embedding.GraalPyResources;
import org.graalvm.python.embedding.VirtualFileSystem;

import java.util.function.Function;

public class PythonProcessor {

    public static final String PYTHON = "python";
    public static final String INJECT_RESOURCES = "GRAALPY-VFS/io.micronaut/micronaut-inject-python";

    void process(String sources) {
        try (Context context = GraalPyResources.contextBuilder(VirtualFileSystem.newBuilder()
                .resourceDirectory(INJECT_RESOURCES)
                .build())
            .option("python.PythonPath", "/micronaut/test")

            // TODO: constrain this in future
            .allowHostAccess(HostAccess.ALL)
            .allowHostClassLookup(name -> name.startsWith("io.micronaut"))
            .build()) {

            context.initialize(PYTHON);
            Value bindings = context.getBindings(PYTHON);
            bindings.putMember("callback", (Function<Object, Object>) o -> {
                Element element = null;

                if (o instanceof ClassDef classDef) {
                    element = new PythonClassElement(classDef);
                } else if (o instanceof FunctionDef functionDef) {
                    // top level function could be a decorator
                    element = new PythonMethodElement(functionDef);
                }
                System.out.println("o = " + element);
                return o;
            });
            bindings
                .putMember("src", sources);
            Value val = context.eval(Source.create(
                PYTHON,
                """
import ast
import java
import micronaut_processor


tree = ast.parse(src)
PrintNodeVisitor().visit(tree)
"""
            ));
            System.out.println("val = " + val);
        }
    }
}
