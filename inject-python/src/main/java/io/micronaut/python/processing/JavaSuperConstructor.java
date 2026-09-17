/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.python.processing;

import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.python.processing.element.AbstractPythonClassElement;
import io.micronaut.python.processing.model.FunctionDef;
import io.micronaut.python.processing.model.SuperArgumentDef;
import io.micronaut.python.processing.visitor.PythonVisitorContext;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.TypeDef;
import org.graalvm.polyglot.Value;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * The Java super constructor a Python class extending a Java class calls, resolved at compile time
 * from the {@code super().__init__(...)} call of its constructor.
 * <p>
 * The generated Java class calls the resolved constructor with the arguments the Python
 * constructor passed to {@code super().__init__(...)}, read back from the Python object once its
 * {@code __init__} has run. The constructor is chosen by the arity of that call and the static
 * types of its arguments: a constructor parameter has the type of its annotation, a literal its
 * Python type, and an argument of unknown type accepts any parameter and prefers {@code String}.
 * A class whose constructor does not call {@code super().__init__(...)} uses the no-argument
 * constructor of the base.
 * </p>
 *
 * @author Micronaut Team
 * @since 5.2.0
 */
@Internal
final class JavaSuperConstructor {

    private static final ClassTypeDef PYTHON_JAVA_BASES = ClassTypeDef.of("io.micronaut.context.python.PythonJavaBases");
    private static final TypeDef POLYGLOT_VALUE = TypeDef.of(Value.class);
    private static final String STRING = String.class.getName();
    private static final String OBJECT = Object.class.getName();
    private static final String PYTHON_CLASS = "Python class [";
    private static final String OF_PYTHON_CLASS = "] of Python class [";
    private static final String SHORT = "short";
    private static final String BYTE = "byte";
    private static final String CHAR = "char";
    private static final String INT = "int";
    private static final String LONG = "long";
    private static final String FLOAT = "float";
    private static final String DOUBLE = "double";
    private static final int REJECTED = -1;
    private static final int ACCEPTED = 0;
    private static final int ASSIGNABLE = 1;
    private static final int EXACT = 2;

    private final ConstructorElement constructor;

    private JavaSuperConstructor(ConstructorElement constructor) {
        this.constructor = constructor;
    }

    /**
     * The problem that prevents a Python class from extending a Java class, or {@code null} when
     * the class can be extended: the class is final, or it has no constructor the generated class
     * can call.
     *
     * @param element The Python class
     * @param superType The Java base class
     * @return The problem, or {@code null}
     */
    static @Nullable String refusal(ClassElement element, ClassElement superType) {
        if (superType.isFinal()) {
            return PYTHON_CLASS + element.getSimpleName() + "] cannot extend the final Java class [" + superType.getName() + "]";
        }
        if (superType.isInner() && !superType.isStatic()) {
            return PYTHON_CLASS + element.getSimpleName() + "] cannot extend the inner Java class [" + superType.getName()
                + "]; only static nested classes can be extended";
        }
        if (accessibleConstructors(element, superType).isEmpty()) {
            return PYTHON_CLASS + element.getSimpleName() + "] cannot extend the Java class [" + superType.getName()
                + "], which has no public or protected constructor";
        }
        return null;
    }

    /**
     * Resolve the super constructor for a Python class extending a Java class.
     *
     * @param element The Python class
     * @param superType The Java base class
     * @param visitorContext The visitor context
     * @return The resolved constructor, or {@code null} with the problem reported through the context
     */
    static @Nullable JavaSuperConstructor resolve(ClassElement element, ClassElement superType, PythonVisitorContext visitorContext) {
        List<ConstructorElement> constructors = accessibleConstructors(element, superType);
        List<SuperArgumentDef> superArguments = superArguments(element);
        if (superArguments == null) {
            ConstructorElement noArguments = constructors.stream()
                .filter(candidate -> candidate.getParameters().length == 0)
                .findFirst()
                .orElse(null);
            if (noArguments != null) {
                return new JavaSuperConstructor(noArguments);
            }
            visitorContext.fail(PYTHON_CLASS + element.getSimpleName() + "] extends the Java class ["
                + superType.getName() + "], which has no no-argument constructor; declare an __init__ method that calls super().__init__(...) with the arguments of one of its constructors: "
                + signatures(constructors), element);
            return null;
        }
        String call = "super().__init__(" + superArguments.stream().map(SuperArgumentDef::source).collect(Collectors.joining(", ")) + ")";
        for (SuperArgumentDef argument : superArguments) {
            if (argument.isKeyword()) {
                visitorContext.fail("The super constructor call [" + call + OF_PYTHON_CLASS + element.getSimpleName()
                    + "] passes [" + argument.source() + "]; a Python class extending the Java class [" + superType.getName()
                    + "] must pass positional arguments only, so the matching Java constructor can be resolved", element);
                return null;
            }
        }
        List<@Nullable ClassElement> argumentTypes = argumentTypes(element, superArguments, visitorContext);
        int bestScore = REJECTED;
        List<ConstructorElement> best = new ArrayList<>();
        for (ConstructorElement candidate : constructors) {
            if (candidate.getParameters().length != superArguments.size()) {
                continue;
            }
            int score = score(candidate, argumentTypes, visitorContext);
            if (score == REJECTED) {
                continue;
            }
            if (score > bestScore) {
                bestScore = score;
                best.clear();
            }
            if (score == bestScore) {
                best.add(candidate);
            }
        }
        if (best.isEmpty()) {
            visitorContext.fail("No constructor of the Java class [" + superType.getName()
                + "] accepts the arguments of the super constructor call [" + call + OF_PYTHON_CLASS + element.getSimpleName()
                + "] (argument types: " + describe(argumentTypes) + "); the constructors of [" + superType.getName() + "] are: " + signatures(constructors), element);
            return null;
        }
        if (best.size() > 1) {
            visitorContext.fail("The super constructor call [" + call + OF_PYTHON_CLASS + element.getSimpleName()
                + "] matches more than one constructor of the Java class [" + superType.getName() + "]: " + signatures(best)
                + "; annotate the constructor parameters or pass literals so a single constructor matches", element);
            return null;
        }
        return new JavaSuperConstructor(best.get(0));
    }

    /**
     * Resolve the super constructor for a Python test class extending a Java class. A test class
     * is instantiated by the test framework before the application context exists and creates its
     * Python object lazily, so the base can only be constructed without arguments.
     *
     * @param element The Python test class
     * @param superType The Java base class
     * @param visitorContext The visitor context
     * @return The no-argument constructor, or {@code null} with the problem reported through the context
     */
    static @Nullable JavaSuperConstructor resolveForTest(ClassElement element, ClassElement superType, PythonVisitorContext visitorContext) {
        List<ConstructorElement> constructors = accessibleConstructors(element, superType);
        ConstructorElement noArguments = constructors.stream()
            .filter(candidate -> candidate.getParameters().length == 0)
            .findFirst()
            .orElse(null);
        if (noArguments == null) {
            visitorContext.fail("Python test class [" + element.getSimpleName() + "] extends the Java class ["
                + superType.getName() + "], which has no no-argument constructor; a test class is instantiated before its Python object exists, so the base cannot receive the arguments of super().__init__(...): "
                + signatures(constructors), element);
            return null;
        }
        return new JavaSuperConstructor(noArguments);
    }

    /**
     * The arguments of the super constructor call, read from the Python object.
     *
     * @param pythonObject The Python object
     * @param converter Converts a polyglot value to a Java type
     * @return The argument expressions
     */
    List<ExpressionDef> arguments(ExpressionDef pythonObject, BiFunction<ClassElement, ExpressionDef, ExpressionDef> converter) {
        ParameterElement[] parameters = constructor.getParameters();
        List<ExpressionDef> arguments = new ArrayList<>(parameters.length);
        String description = signature(constructor);
        for (int i = 0; i < parameters.length; i++) {
            ExpressionDef argument = PYTHON_JAVA_BASES.invokeStatic("argument", POLYGLOT_VALUE, pythonObject, ExpressionDef.constant(i), ExpressionDef.constant(description));
            arguments.add(converter.apply(parameters[i].getGenericType(), argument));
        }
        return arguments;
    }

    private static List<ConstructorElement> accessibleConstructors(ClassElement element, ClassElement superType) {
        return superType.getAccessibleConstructors()
            .stream()
            .filter(candidate -> candidate.isPublic() || candidate.isProtected() || candidate.getDeclaringType().getPackageName().equals(element.getPackageName()))
            .toList();
    }

    private static @Nullable List<SuperArgumentDef> superArguments(ClassElement element) {
        if (!(element instanceof AbstractPythonClassElement pythonClass)) {
            return null;
        }
        FunctionDef constructor = pythonClass.getNativeType().constructor();
        return constructor == null ? null : constructor.superArguments();
    }

    private static List<@Nullable ClassElement> argumentTypes(ClassElement element, List<SuperArgumentDef> superArguments, PythonVisitorContext visitorContext) {
        Map<String, ParameterElement> parameters = element.getPrimaryConstructor()
            .map(constructor -> Arrays.stream(constructor.getParameters()).collect(Collectors.toMap(ParameterElement::getName, parameter -> parameter, (left, right) -> left)))
            .orElse(Map.of());
        List<@Nullable ClassElement> types = new ArrayList<>(superArguments.size());
        for (SuperArgumentDef argument : superArguments) {
            ClassElement type = null;
            if (argument.isParameter()) {
                ParameterElement parameter = parameters.get(argument.parameterName());
                if (parameter != null) {
                    type = parameter.getGenericType();
                }
            } else if (argument.type() != null) {
                type = visitorContext.getTypeResolver().resolve(argument.type(), Map.of());
            }
            if (type != null && !type.isPrimitive() && OBJECT.equals(type.getName())) {
                type = null;
            }
            types.add(type);
        }
        return types;
    }

    private static int score(ConstructorElement candidate, List<@Nullable ClassElement> argumentTypes, PythonVisitorContext visitorContext) {
        ParameterElement[] parameters = candidate.getParameters();
        int total = 0;
        for (int i = 0; i < parameters.length; i++) {
            int score = compatibility(argumentTypes.get(i), parameters[i].getType(), visitorContext);
            if (score == REJECTED) {
                return REJECTED;
            }
            total += score;
        }
        return total;
    }

    private static int compatibility(@Nullable ClassElement argument, ClassElement parameter, PythonVisitorContext visitorContext) {
        if (argument == null) {
            return STRING.equals(parameter.getName()) ? ASSIGNABLE : ACCEPTED;
        }
        if (argument.isPrimitive() && "void".equals(argument.getName())) {
            // None: any reference parameter
            return parameter.isPrimitive() ? REJECTED : ACCEPTED;
        }
        if (OBJECT.equals(parameter.getName())) {
            return ASSIGNABLE;
        }
        if (argument.isPrimitive() && parameter.isPrimitive()) {
            if (argument.getName().equals(parameter.getName())) {
                return EXACT;
            }
            return widens(argument.getName(), parameter.getName()) ? ASSIGNABLE : REJECTED;
        }
        if (argument.isPrimitive()) {
            String boxed = boxedName(argument.getName());
            if (boxed == null) {
                return REJECTED;
            }
            if (boxed.equals(parameter.getName())) {
                return EXACT;
            }
            ClassElement boxedElement = visitorContext.getClassElement(boxed).orElse(null);
            return boxedElement != null && boxedElement.isAssignable(parameter) ? ASSIGNABLE : REJECTED;
        }
        if (parameter.isPrimitive()) {
            String boxed = boxedName(parameter.getName());
            if (argument.getName().equals(boxed)) {
                return EXACT;
            }
            String unboxed = unboxedName(argument.getName());
            return unboxed != null && widens(unboxed, parameter.getName()) ? ASSIGNABLE : REJECTED;
        }
        if (argument.getName().equals(parameter.getName())) {
            return EXACT;
        }
        return argument.isAssignable(parameter) ? ASSIGNABLE : REJECTED;
    }

    private static boolean widens(String from, String to) {
        return switch (from) {
            case BYTE -> List.of(SHORT, INT, LONG, FLOAT, DOUBLE).contains(to);
            case SHORT, CHAR -> List.of(INT, LONG, FLOAT, DOUBLE).contains(to);
            case INT -> List.of(LONG, FLOAT, DOUBLE).contains(to);
            case LONG -> List.of(FLOAT, DOUBLE).contains(to);
            case FLOAT -> DOUBLE.equals(to);
            default -> false;
        };
    }

    private static @Nullable String boxedName(String primitive) {
        return switch (primitive) {
            case INT -> Integer.class.getName();
            case LONG -> Long.class.getName();
            case DOUBLE -> Double.class.getName();
            case FLOAT -> Float.class.getName();
            case "boolean" -> Boolean.class.getName();
            case SHORT -> Short.class.getName();
            case BYTE -> Byte.class.getName();
            case CHAR -> Character.class.getName();
            default -> null;
        };
    }

    private static @Nullable String unboxedName(String boxed) {
        for (String primitive : List.of(INT, LONG, DOUBLE, FLOAT, "boolean", SHORT, BYTE, CHAR)) {
            if (boxed.equals(boxedName(primitive))) {
                return primitive;
            }
        }
        return null;
    }

    private static String describe(List<@Nullable ClassElement> argumentTypes) {
        return argumentTypes.stream()
            .map(type -> type == null ? "?" : type.getName())
            .collect(Collectors.joining(", ", "[", "]"));
    }

    private static String signature(ConstructorElement constructor) {
        return Arrays.stream(constructor.getParameters())
            .map(parameter -> parameter.getType().getName())
            .collect(Collectors.joining(", ", "(", ")"));
    }

    private static String signatures(List<ConstructorElement> constructors) {
        return constructors.stream()
            .map(JavaSuperConstructor::signature)
            .collect(Collectors.joining(", ", "[", "]"));
    }
}
