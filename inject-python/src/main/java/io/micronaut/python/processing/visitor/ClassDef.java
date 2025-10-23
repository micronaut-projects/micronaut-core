package io.micronaut.python.processing.visitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A ClassDef node represents a class definition.
 * <p>
 * ClassDef(identifier name, list[expr] bases, list[keyword] keywords, list[stmt] body, list[FunctionDef] decorator_list, list[TypeVar] type_params, list[AttributeDef] attributes)
 * </p>
 *
 * @param name The name of the class.
 * @param bases The base class names.
 * @param decorators The decorators.
 * @param typeParams The type parameters.
 * @param functions The functions defined in the class.
 * @param attributes The attributes defined in the class.
 * @see <a href="https://docs.python.org/3/library/ast.html#ast.ClassDef">Python AST ClassDef</a>
 */
public record ClassDef(
    String name,
    List<String> bases,
    List<DecoratorDef> decorators,
    List<TypeVar> typeParams,
    List<FunctionDef> functions,
    List<AttributeDef> attributes
) implements ElementDef {

    public ClassDef(String name) {
        this(name, List.of(), List.of(), List.of(), List.of(), List.of());
    }

    public ClassDef(String name, List<DecoratorDef> decoratorList) {
        this(name, List.of(), decoratorList, List.of(), List.of(), List.of());
    }

    public ClassDef {
        Objects.requireNonNull(name, "Decorator name cannot be null");
        if (bases == null) {
            bases = List.of();
        }
        if (decorators == null) {
            decorators = List.of();
        }
        if (typeParams == null) {
            typeParams = List.of();
        }

        if (functions == null) {
            functions = List.of();
        }

        if (attributes == null) {
            attributes = List.of();
        }
    }

    public ClassDef withFunction(FunctionDef function) {
        Objects.requireNonNull(function, "Function cannot be null");
        List<FunctionDef> functions = new ArrayList<>(this.functions);
        functions.add(function);
        return new ClassDef(name, bases, decorators, typeParams, functions, attributes);
    }

    public ClassDef withAttribute(AttributeDef attribute) {
        Objects.requireNonNull(attribute, "Attribute cannot be null");
        List<AttributeDef> attributes = new ArrayList<>(this.attributes);
        attributes.add(attribute);
        return new ClassDef(name, bases, decorators, typeParams, functions, attributes);
    }
}
