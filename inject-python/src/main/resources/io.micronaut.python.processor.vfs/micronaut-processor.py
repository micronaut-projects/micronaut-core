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
