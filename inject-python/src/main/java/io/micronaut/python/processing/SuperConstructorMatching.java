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
import io.micronaut.python.processing.model.SuperArgumentDef;
import io.micronaut.python.processing.visitor.PythonVisitorContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Matches the {@code super().__init__(...)} call of a Python constructor against the constructors of
 * the Java class the Python class extends, shared by {@link JavaSuperConstructor} and
 * {@link ThrowableSuperConstructor}.
 * <p>
 * The static type of each argument is what the processor sees: a constructor parameter has the type
 * of its annotation, a literal its Python type. An argument of unknown type accepts any parameter
 * and prefers {@code String}. Each candidate is scored by how closely the argument types match its
 * parameter types, and the candidates with the best score are returned.
 * </p>
 *
 * @since 5.2.0
 */
@Internal
final class SuperConstructorMatching {

    private static final String STRING = String.class.getName();
    private static final String OBJECT = Object.class.getName();
    private static final String BOOLEAN = "boolean";
    private static final String BYTE = "byte";
    private static final String SHORT = "short";
    private static final String CHAR = "char";
    private static final String INT = "int";
    private static final String LONG = "long";
    private static final String FLOAT = "float";
    private static final String DOUBLE = "double";
    private static final int REJECTED = -1;
    private static final int ACCEPTED = 0;
    private static final int ASSIGNABLE = 1;
    private static final int EXACT = 2;

    private SuperConstructorMatching() {
    }

    /**
     * The static types of the arguments of a super constructor call, {@code null} where unknown.
     *
     * @param element The Python class
     * @param superArguments The arguments of the super constructor call
     * @param visitorContext The visitor context
     * @return The argument types
     */
    static List<@Nullable ClassElement> argumentTypes(ClassElement element, List<SuperArgumentDef> superArguments, PythonVisitorContext visitorContext) {
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

    /**
     * The constructors of the same arity that accept the argument types with the best score.
     *
     * @param constructors The constructors to choose from
     * @param argumentTypes The argument types
     * @param visitorContext The visitor context
     * @param widenToBoxedParameters Whether a primitive argument may widen to a boxed parameter of a
     * wider primitive (a Python {@code int} against a {@code Long} parameter)
     * @return The best matching constructors, empty when none accepts the arguments
     */
    static List<ConstructorElement> bestMatches(List<ConstructorElement> constructors,
                                                List<@Nullable ClassElement> argumentTypes,
                                                PythonVisitorContext visitorContext,
                                                boolean widenToBoxedParameters) {
        int bestScore = REJECTED;
        List<ConstructorElement> best = new ArrayList<>();
        for (ConstructorElement candidate : constructors) {
            if (candidate.getParameters().length != argumentTypes.size()) {
                continue;
            }
            int score = score(candidate, argumentTypes, visitorContext, widenToBoxedParameters);
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
        return best;
    }

    /**
     * Describes argument types for an error message.
     *
     * @param argumentTypes The argument types
     * @return The description
     */
    static String describe(List<@Nullable ClassElement> argumentTypes) {
        return argumentTypes.stream()
            .map(type -> type == null ? "?" : type.getName())
            .collect(Collectors.joining(", ", "[", "]"));
    }

    /**
     * Describes the parameter types of a constructor.
     *
     * @param constructor The constructor
     * @return The description
     */
    static String signature(ConstructorElement constructor) {
        return Arrays.stream(constructor.getParameters())
            .map(parameter -> parameter.getType().getName())
            .collect(Collectors.joining(", ", "(", ")"));
    }

    /**
     * Describes the parameter types of constructors for an error message.
     *
     * @param constructors The constructors
     * @return The description
     */
    static String signatures(List<ConstructorElement> constructors) {
        return constructors.stream()
            .map(SuperConstructorMatching::signature)
            .collect(Collectors.joining(", ", "[", "]"));
    }

    private static int score(ConstructorElement candidate, List<@Nullable ClassElement> argumentTypes, PythonVisitorContext visitorContext, boolean widenToBoxedParameters) {
        ParameterElement[] parameters = candidate.getParameters();
        int total = 0;
        for (int i = 0; i < parameters.length; i++) {
            int score = compatibility(argumentTypes.get(i), parameters[i].getType(), visitorContext, widenToBoxedParameters);
            if (score == REJECTED) {
                return REJECTED;
            }
            total += score;
        }
        return total;
    }

    private static int compatibility(@Nullable ClassElement argument, ClassElement parameter, PythonVisitorContext visitorContext, boolean widenToBoxedParameters) {
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
            return primitiveArgumentCompatibility(argument, parameter, visitorContext, widenToBoxedParameters);
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

    private static int primitiveArgumentCompatibility(ClassElement argument, ClassElement parameter, PythonVisitorContext visitorContext, boolean widenToBoxedParameters) {
        String boxed = boxedName(argument.getName());
        if (boxed == null) {
            return REJECTED;
        }
        if (boxed.equals(parameter.getName())) {
            return EXACT;
        }
        if (widenToBoxedParameters) {
            String unboxedParameter = unboxedName(parameter.getName());
            if (unboxedParameter != null) {
                // a Python int against a Long parameter: GraalPy converts it as it widens the primitive
                return widens(argument.getName(), unboxedParameter) ? ASSIGNABLE : REJECTED;
            }
        }
        ClassElement boxedElement = visitorContext.getClassElement(boxed).orElse(null);
        return boxedElement != null && boxedElement.isAssignable(parameter) ? ASSIGNABLE : REJECTED;
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
            case BOOLEAN -> Boolean.class.getName();
            case SHORT -> Short.class.getName();
            case BYTE -> Byte.class.getName();
            case CHAR -> Character.class.getName();
            default -> null;
        };
    }

    private static @Nullable String unboxedName(String boxed) {
        for (String primitive : List.of(INT, LONG, DOUBLE, FLOAT, BOOLEAN, SHORT, BYTE, CHAR)) {
            if (boxed.equals(boxedName(primitive))) {
                return primitive;
            }
        }
        return null;
    }
}
