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

import java.util.function.Function;

public class PythonProcessor {

    public static final String PYTHON = "python";

    void process(String sources) {
        try (Context context = GraalPyResources.contextBuilder()
            .option("python.PythonPath", "/micronaut/test")

            // TODO: constrain this in future
            .allowHostAccess(HostAccess.ALL)
            .allowHostClassLookup(name -> true)
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


class PrintNodeVisitor(ast.NodeVisitor):
    def visit(self, node: ast.AST) -> ast.AST:
        JavaClassDef = java.type("io.micronaut.python.processing.visitor.ClassDef")
        JavaFuncDef = java.type("io.micronaut.python.processing.visitor.FunctionDef")

        match node:
            case ast.ClassDef():
                print(node.decorator_list)
                decorators = [JavaFuncDef(d.id) for d in node.decorator_list]
                callback.apply(JavaClassDef(node.name, decorators))
                return super().visit(node)
            case ast.FunctionDef():
                decorators = [JavaFuncDef(d.id) for d in node.decorator_list]
                callback.apply(JavaFuncDef(node.name, decorators))
                return super().visit(node)
            case ast.Module():
                callback.apply(node)
                return super().visit(node)
            case _:
                return node


tree = ast.parse(src)
PrintNodeVisitor().visit(tree)
"""
            ));
            System.out.println("val = " + val);
        }
    }
}
