package io.micronaut.python.processing.visitor;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import io.micronaut.python.processing.PythonProcessingEnvironment;
import io.micronaut.python.processing.util.GraalPyUtil;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a Python method/function as a Micronaut {@link MethodElement}.
 * <p>
 * This class wraps a {@link FunctionDef} node of the Python AST, providing full
 * MethodElement interface implementation including parameters, return types, and visibility.
 * </p>
 *
 * @author Micronaut Team
 * @since 5.0.0
 * @see FunctionDef
 * @see <a href="https://docs.python.org/3/library/ast.html#ast.FunctionDef">Python AST FunctionDef</a>
 */
public final class PythonMethodElement extends AbstractPythonElement implements MethodElement {
    private final PythonProcessingEnvironment environment;
    private final PythonClassElement declaringType;
    private final ClassElement returnType;
    private final ParameterElement[] parameters;

    /**
     * Constructs a new {@code PythonMethodElement} from the given {@code FunctionDef}.
     *
     * @param functionDef the function definition node; must not be {@code null}
     * @param environment the Python processing environment; must not be {@code null}
     * @param declaringType the class that declares this method; must not be {@code null}
     * @param metadataFactory the annotation metadata factory; must not be {@code null}
     * @throws NullPointerException if any parameter is {@code null}
     */
    public PythonMethodElement(FunctionDef functionDef,
                               PythonProcessingEnvironment environment,
                               PythonClassElement declaringType,
                               ElementAnnotationMetadataFactory metadataFactory) {
        super(Objects.requireNonNull(functionDef, "FunctionDef cannot be null").name(), functionDef, metadataFactory);
        this.environment = Objects.requireNonNull(environment, "PythonProcessingEnvironment cannot be null");
        this.declaringType = Objects.requireNonNull(declaringType, "Declaring type cannot be null");

        // Resolve return type
        this.returnType = resolveReturnType(functionDef);

        // Create parameter elements
        this.parameters = createParameters(functionDef);
    }

    /**
     * Returns the native {@link FunctionDef} object that backs this element.
     *
     * @return the underlying {@code FunctionDef} node
     */
    @Override
    public FunctionDef getNativeType() {
        return (FunctionDef) super.getNativeType();
    }

    @Override
    public ClassElement getReturnType() {
        return returnType;
    }

    @Override
    public ParameterElement[] getParameters() {
        return parameters.clone();
    }

    @Override
    public MethodElement withParameters(ParameterElement... newParameters) {
        // Since PythonMethodElement is based on parsed Python code,
        // we create a synthetic MethodElement with the new parameters
        return MethodElement.of(
            getOwningType(),
            getAnnotationMetadata(),
            getReturnType(),
            getGenericReturnType(),
            getName(),
            newParameters
        );
    }

    @Override
    public boolean isPublic() {
        // Python considers methods/attributes starting with '_' as private
        return !getName().startsWith("_");
    }

    @Override
    public boolean isPrivate() {
        return getName().startsWith("_");
    }

    @Override
    public ClassElement getDeclaringType() {
        return declaringType;
    }

    @Override
    public ClassElement getOwningType() {
        return getDeclaringType();
    }

    /**
     * Returns a string representation of the Python function, including its name.
     *
     * @return a string in the format "Python Function: <functionName>"
     */
    @Override
    public String toString() {
        return "Python Function: " + getNativeType().name();
    }

    private ClassElement resolveReturnType(FunctionDef functionDef) {
        if (functionDef.returnType() != null && functionDef.returnType().typeAnnotation() != null) {
            return GraalPyUtil.resolvePythonTypeToJava(functionDef.returnType().typeAnnotation(), environment.visitorContext());
        }
        // Fall back to void/Object
        return environment.visitorContext().getClassElement(Object.class).orElse(ClassElement.of(Object.class));
    }

    private ParameterElement[] createParameters(FunctionDef functionDef) {
        List<ArgumentDef> arguments = functionDef.arguments().arguments();
        ParameterElement[] parameters = new ParameterElement[arguments.size()];

        for (int i = 0; i < arguments.size(); i++) {
            ArgumentDef argDef = arguments.get(i);
            parameters[i] = new PythonParameterElement(argDef, environment, getElementAnnotationMetadataFactory());
        }

        return parameters;
    }

    @Override
    public Optional<String> getDocumentation(boolean parseContent) {
        String doc = getNativeType().documentation();
        if (doc == null) {
            return Optional.empty();
        }
        if (parseContent) {
            // Parse Python docstring to extract main description
            return Optional.of(GraalPyUtil.parsePythonDocstring(doc));
        }
        return Optional.of(doc);
    }
}
