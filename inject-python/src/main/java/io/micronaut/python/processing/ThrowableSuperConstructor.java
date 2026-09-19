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
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.python.processing.element.AbstractPythonClassElement;
import io.micronaut.python.processing.model.FunctionDef;
import io.micronaut.python.processing.model.SuperArgumentDef;
import io.micronaut.python.processing.visitor.PythonVisitorContext;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.TypeDef;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * Picks the Java super constructor that the generated class of a Python exception extending a Java
 * exception class calls, and builds its arguments from the Python exception's {@code args}.
 * <p>
 * The Python class runs as a plain Python exception, so the arguments of its
 * {@code super().__init__(...)} call end up in {@code args}. The constructor is picked at compile
 * time from the arity of that call and the static types of its arguments as the processor sees them:
 * literals, f-strings, constructor parameters (through their annotations), module constants and
 * calls of a class. An argument of unknown type accepts any parameter, with a preference for a
 * {@code String} parameter, the usual exception message. A constructor without a super call, one
 * that calls the super constructor more than once with different arguments, or one that passes
 * its own {@code *args}/{@code **kwargs} through, falls back to the message constructor of the
 * Java base, fed with {@code str(exception)}, or to its no-argument constructor.
 *
 * @since 5.2.0
 */
@Internal
final class ThrowableSuperConstructor {

    private static final ClassTypeDef PYTHON_EXCEPTIONS = ClassTypeDef.of("io.micronaut.context.python.PythonExceptions");
    private static final String STRING = String.class.getName();

    private final @Nullable ConstructorElement constructor;
    private final boolean messageFallback;

    private ThrowableSuperConstructor(@Nullable ConstructorElement constructor, boolean messageFallback) {
        this.constructor = constructor;
        this.messageFallback = messageFallback;
    }

    /**
     * Resolve the super constructor for a Python class extending a Java {@link Throwable}.
     *
     * @param element The Python class
     * @param superType The Java base class
     * @param visitorContext The visitor context
     * @return The resolved constructor
     * @throws ProcessingException When no constructor of the base matches the super call
     */
    static ThrowableSuperConstructor resolve(ClassElement element, ClassElement superType, PythonVisitorContext visitorContext) {
        List<SuperArgumentDef> superArguments = superArguments(element);
        List<ConstructorElement> constructors = superType.getAccessibleConstructors()
            .stream()
            .filter(candidate -> candidate.isPublic() || candidate.isProtected() || candidate.getDeclaringType().getPackageName().equals(element.getPackageName()))
            .toList();
        if (superArguments == null) {
            ConstructorElement message = constructors.stream()
                .filter(candidate -> candidate.getParameters().length == 1 && STRING.equals(candidate.getParameters()[0].getType().getName()))
                .findFirst()
                .orElse(null);
            if (message != null) {
                return new ThrowableSuperConstructor(message, true);
            }
            ConstructorElement noArguments = constructors.stream()
                .filter(candidate -> candidate.getParameters().length == 0)
                .findFirst()
                .orElse(null);
            if (noArguments != null) {
                return new ThrowableSuperConstructor(noArguments, false);
            }
            throw new ProcessingException(element, "Python class [" + element.getName() + "] extends the Java exception class ["
                + superType.getName() + "], which has neither a no-argument nor a message constructor; declare an __init__ method that calls super().__init__(...) once, with positional arguments matching one of its constructors: "
                + SuperConstructorMatching.signatures(constructors));
        }
        String call = "super().__init__(" + superArguments.stream().map(SuperArgumentDef::source).collect(Collectors.joining(", ")) + ")";
        for (SuperArgumentDef argument : superArguments) {
            if (argument.isKeyword()) {
                throw new ProcessingException(element, "The super constructor call [" + call + "] of Python class [" + element.getName()
                    + "] passes [" + argument.source() + "] by keyword; a Python class extending the Java exception class [" + superType.getName()
                    + "] must pass positional arguments only, so the matching Java constructor can be resolved");
            }
        }
        List<@Nullable ClassElement> argumentTypes = SuperConstructorMatching.argumentTypes(element, superArguments, visitorContext);
        List<ConstructorElement> candidates = constructors.stream()
            .filter(candidate -> candidate.getParameters().length == superArguments.size())
            .toList();
        List<ConstructorElement> best = SuperConstructorMatching.bestMatches(candidates, argumentTypes, visitorContext, false);
        if (best.isEmpty()) {
            throw new ProcessingException(element, "No constructor of the Java exception class [" + superType.getName()
                + "] accepts the arguments of the super constructor call [" + call + "] of Python class [" + element.getName()
                + "] (argument types: " + SuperConstructorMatching.describe(argumentTypes) + "); the constructors of [" + superType.getName() + "] are: " + SuperConstructorMatching.signatures(constructors));
        }
        if (best.size() > 1) {
            throw new ProcessingException(element, "The super constructor call [" + call + "] of Python class [" + element.getName()
                + "] matches more than one constructor of the Java exception class [" + superType.getName() + "]: " + SuperConstructorMatching.signatures(best)
                + "; annotate the constructor parameters or pass literals so a single constructor matches");
        }
        return new ThrowableSuperConstructor(best.get(0), false);
    }

    /**
     * The arguments of the super constructor call, read from the Python exception.
     *
     * @param exception The Python exception value
     * @param converter Converts a polyglot value to a Java type
     * @return The argument expressions
     */
    List<ExpressionDef> arguments(ExpressionDef exception, BiFunction<ClassElement, ExpressionDef, ExpressionDef> converter) {
        if (constructor == null) {
            return List.of();
        }
        if (messageFallback) {
            return List.of(PYTHON_EXCEPTIONS.invokeStatic("message", TypeDef.STRING, exception));
        }
        ParameterElement[] parameters = constructor.getParameters();
        List<ExpressionDef> arguments = new ArrayList<>(parameters.length);
        for (int i = 0; i < parameters.length; i++) {
            ClassElement parameterType = parameters[i].getGenericType();
            ExpressionDef index = ExpressionDef.constant(i);
            if (STRING.equals(parameterType.getName())) {
                arguments.add(PYTHON_EXCEPTIONS.invokeStatic("argumentAsString", TypeDef.STRING, exception, index));
            } else if (parameterType.isPrimitive()) {
                // a missing or None argument cannot become a primitive: fail with a clear message
                ExpressionDef argument = PYTHON_EXCEPTIONS.invokeStatic("primitiveArgument", TypeDef.of(org.graalvm.polyglot.Value.class),
                    exception, index, ExpressionDef.constant(parameterType.getName()));
                arguments.add(converter.apply(parameterType, argument));
            } else {
                ExpressionDef argument = PYTHON_EXCEPTIONS.invokeStatic("argument", TypeDef.of(org.graalvm.polyglot.Value.class), exception, index);
                arguments.add(converter.apply(parameterType, argument));
            }
        }
        return arguments;
    }

    /**
     * The super constructor arguments of the class when they form a fixed argument list; a constructor
     * calling {@code super().__init__(...)} differently on different branches, or spreading its own
     * {@code *args}/{@code **kwargs} into the call, cannot be matched statically and falls back to the
     * message constructor.
     */
    private static @Nullable List<SuperArgumentDef> superArguments(ClassElement element) {
        if (!(element instanceof AbstractPythonClassElement pythonClass)) {
            return null;
        }
        FunctionDef constructor = pythonClass.getNativeType().constructor();
        List<SuperArgumentDef> superArguments = constructor == null ? null : constructor.superArguments();
        if (superArguments != null && superArguments.stream().anyMatch(argument -> argument.isConflicting() || argument.isSpread())) {
            return null;
        }
        return superArguments;
    }
}
