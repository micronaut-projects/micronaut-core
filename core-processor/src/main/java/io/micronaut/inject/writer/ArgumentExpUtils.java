/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.inject.writer;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.TypeInformation;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.annotation.AnnotationMetadataGenUtils;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.annotation.AnnotationMetadataReference;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.inject.ast.ArrayableClassElement;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.ast.WildcardElement;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.TypeDef;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * The argument expression utils.
 *
 * @author Denis Stepanov
 * @since 4.8
 */
@NullUnmarked
@Internal
public final class ArgumentExpUtils {

    public static final ClassTypeDef TYPE_ARGUMENT = ClassTypeDef.of(Argument.class);
    public static final TypeDef.Array TYPE_ARGUMENT_ARRAY = TYPE_ARGUMENT.array();
    public static final Method METHOD_CREATE_ARGUMENT_SIMPLE = ReflectionUtils.getRequiredInternalMethod(
        Argument.class,
        "of",
        Class.class,
        String.class
    );

    private static final String ZERO_ARGUMENTS_CONSTANT = "ZERO_ARGUMENTS";

    private static final Method METHOD_GENERIC_PLACEHOLDER_SIMPLE = ReflectionUtils.getRequiredInternalMethod(
        Argument.class,
        "ofTypeVariable",
        Class.class,
        String.class,
        String.class
    );

    private static final Method METHOD_CREATE_TYPE_VARIABLE_SIMPLE = ReflectionUtils.getRequiredInternalMethod(
        Argument.class,
        "ofTypeVariable",
        Class.class,
        String.class
    );

    private static final Method METHOD_CREATE_ARGUMENT_WITH_ANNOTATION_METADATA_GENERICS = ReflectionUtils.getRequiredInternalMethod(
        Argument.class,
        "of",
        Class.class,
        String.class,
        AnnotationMetadata.class,
        Argument[].class
    );

    private static final Method METHOD_CREATE_TYPE_VAR_WITH_ANNOTATION_METADATA_GENERICS = ReflectionUtils.getRequiredInternalMethod(
        Argument.class,
        "ofTypeVariable",
        Class.class,
        String.class,
        AnnotationMetadata.class,
        Argument[].class
    );

    private static final Method METHOD_CREATE_GENERIC_PLACEHOLDER_WITH_ANNOTATION_METADATA_GENERICS = ReflectionUtils.getRequiredInternalMethod(
        Argument.class,
        "ofTypeVariable",
        Class.class,
        String.class,
        String.class,
        AnnotationMetadata.class,
        Argument[].class
    );

    private static final Method METHOD_CREATE_ARGUMENT_CLASS = ReflectionUtils.getRequiredInternalMethod(
        Argument.class,
        "of",
        Class.class
    );

    private static final Method METHOD_CREATE_TYPE_VAR_WITH_BOUNDS = ReflectionUtils.getRequiredInternalMethod(
        Argument.class,
        "ofTypeVariable",
        Class.class,
        String.class,
        String.class,
        AnnotationMetadata.class,
        Argument[].class,
        Argument[].class
    );

    private static final Method METHOD_CREATE_RESOLVED_TYPE_VAR = ReflectionUtils.getRequiredInternalMethod(
        Argument.class,
        "ofResolvedTypeVariable",
        Class.class,
        String.class,
        String.class,
        AnnotationMetadata.class,
        Argument[].class,
        Argument[].class
    );

    private static final Method METHOD_CREATE_RAW_TYPE = ReflectionUtils.getRequiredInternalMethod(
        Argument.class,
        "ofRawType",
        Class.class,
        String.class,
        AnnotationMetadata.class,
        Argument[].class
    );

    private static final Method METHOD_CREATE_WILDCARD = ReflectionUtils.getRequiredInternalMethod(
        Argument.class,
        "ofWildcard",
        Class.class,
        String.class,
        AnnotationMetadata.class,
        Argument[].class,
        Argument[].class,
        Argument[].class
    );

    private static final Method METHOD_CREATE_ARGUMENT_WITH_ANNOTATION_METADATA_CLASS_GENERICS = ReflectionUtils.getRequiredInternalMethod(
        Argument.class,
        "of",
        Class.class,
        AnnotationMetadata.class,
        Class[].class
    );

    private static final Method METHOD_ARGUMENT_GET_TYPE = ReflectionUtils.getRequiredInternalMethod(
        TypeInformation.class,
        "getType"
    );

    /**
     * Extract the argument's type.
     *
     * @param argument The argument expression
     * @return The type expression
     */
    public static ExpressionDef getTypeExp(ExpressionDef argument) {
        return argument.invoke(METHOD_ARGUMENT_GET_TYPE);
    }

    /**
     * Creates an argument.
     *
     * @param annotationMetadataWithDefaults The annotation metadata with defaults
     * @param owningType                     The owning type
     * @param declaringType                  The declaring type name
     * @param argument                       The argument
     * @param loadClassValueExpressionFn     The load type method fn
     * @return The expression
     */
    public static ExpressionDef pushReturnTypeArgument(AnnotationMetadata annotationMetadataWithDefaults,
                                                       ClassTypeDef owningType,
                                                       ClassElement declaringType,
                                                       ClassElement argument,
                                                       Function<String, ExpressionDef> loadClassValueExpressionFn) {
        // Persist only type annotations added
        AnnotationMetadata annotationMetadata = argument.getTypeAnnotationMetadata();

        if (argument.isVoid()) {
            return TYPE_ARGUMENT.getStaticField("VOID", TYPE_ARGUMENT);
        }
        if (argument.isPrimitive() && !argument.isArray()) {
            String constantName = argument.getName().toUpperCase(Locale.ENGLISH);
            // refer to constant for primitives
            return TYPE_ARGUMENT.getStaticField(constantName, TYPE_ARGUMENT);
        }

        if (annotationMetadata.isEmpty()
            && !argument.isArray()
            && String.class.getName().equals(argument.getType().getName())
            && argument.getName().equals(argument.getType().getName())
            && argument.getAnnotationMetadata().isEmpty()) {
            return TYPE_ARGUMENT.getStaticField("STRING", TYPE_ARGUMENT);
        }

        return pushCreateArgument(
            annotationMetadataWithDefaults,
            declaringType,
            owningType,
            argument.getName(),
            argument,
            annotationMetadata,
            argument.getTypeArguments(),
            loadClassValueExpressionFn
        );
    }

    /**
     * Create a new Argument creation.
     *
     * @param annotationMetadataWithDefaults The annotation metadata with defaults
     * @param declaringType                  The declaring type name
     * @param owningType                     The owning type
     * @param argumentName                   The argument name
     * @param argument                       The argument
     * @param loadClassValueExpressionFn     The load type methods fn
     * @return The expression
     */
    public static ExpressionDef pushCreateArgument(
        AnnotationMetadata annotationMetadataWithDefaults,
        ClassElement declaringType,
        ClassTypeDef owningType,
        String argumentName,
        ClassElement argument,
        Function<String, ExpressionDef> loadClassValueExpressionFn) {

        return pushCreateArgument(
            annotationMetadataWithDefaults,
            declaringType,
            owningType,
            argumentName,
            argument,
            argument.getAnnotationMetadata(),
            argument.getTypeArguments(),
            loadClassValueExpressionFn
        );
    }

    /**
     * Creates a new Argument creation.
     *
     * @param annotationMetadataWithDefaults The annotation metadata with defaults
     * @param declaringType                  The declaring type name
     * @param owningType                     The owning type
     * @param argumentName                   The argument name
     * @param argumentType                   The argument type
     * @param annotationMetadata             The annotation metadata
     * @param typeArguments                  The type arguments
     * @param loadClassValueExpressionFn     The load class value expression fn
     * @return The expression
     */
    static ExpressionDef pushCreateArgument(
        AnnotationMetadata annotationMetadataWithDefaults,
        ClassElement declaringType,
        ClassTypeDef owningType,
        String argumentName,
        TypedElement argumentType,
        AnnotationMetadata annotationMetadata,
        Map<String, ClassElement> typeArguments,
        Function<String, ExpressionDef> loadClassValueExpressionFn) {
        annotationMetadata = MutableAnnotationMetadata.of(annotationMetadata);
        ExpressionDef.Constant argumentTypeConstant = ExpressionDef.constant(TypeDef.erasure(resolveArgument(argumentType)));

        boolean hasAnnotations = !annotationMetadata.isEmpty();
        boolean hasTypeArguments = typeArguments != null && !typeArguments.isEmpty();
        // The bounds are read from the placeholder, before it is replaced by the type it resolves to
        List<? extends ClassElement> bounds = boundsToRecord(argumentType);
        // As is the rawness, which is a property of the usage rather than of the type it resolves to
        boolean isRawType = isRawType(argumentType);
        // And the variable the placeholder ends at, when it was not resolved to a type
        GenericPlaceholderElement variable = unresolvedVariable(argumentType);
        if (argumentType instanceof GenericPlaceholderElement placeholderElement) {
            // Persist resolved placeholder for backward compatibility
            argumentType = placeholderElement.getResolved().orElse(placeholderElement);
        }
        boolean isGenericPlaceholder = argumentType instanceof GenericPlaceholderElement;
        boolean isTypeVariable = isGenericPlaceholder || ((argumentType instanceof ClassElement classElement) && classElement.isTypeVariable());
        String variableName = argumentName;
        if (variable != null) {
            // The name the variable was declared with, where a placeholder resolved to another one ends
            variableName = variable.getVariableName();
        } else if (isGenericPlaceholder) {
            variableName = ((GenericPlaceholderElement) argumentType).getVariableName();
        }
        boolean hasVariableName = !variableName.equals(argumentName);
        // A type that took the place of a variable, which is still written as a placeholder of the variable
        boolean isResolvedTypeVariable = isTypeVariable && variable == null;

        List<ExpressionDef> values = new ArrayList<>();

        // 1st argument: The type
        values.add(argumentTypeConstant);
        // 2nd argument: The argument name
        values.add(ExpressionDef.constant(argumentName));

        if (!hasAnnotations && !hasTypeArguments && !isTypeVariable && !isRawType) {
            return TYPE_ARGUMENT.invokeStatic(
                METHOD_CREATE_ARGUMENT_SIMPLE,
                values.stream().toList()
            );
        }

        if (isTypeVariable && hasVariableName) {
            values.add(ExpressionDef.constant(variableName));
        }

        // 3rd argument: The annotation metadata
        if (hasAnnotations) {
            MutableAnnotationMetadata.contributeDefaults(
                annotationMetadataWithDefaults,
                annotationMetadata
            );

            values.add(AnnotationMetadataGenUtils.instantiateNewMetadata(
                (MutableAnnotationMetadata) annotationMetadata,
                loadClassValueExpressionFn
            ));
        } else {
            values.add(ExpressionDef.nullValue());
        }

        // 4th argument: The generic types
        if (hasTypeArguments) {
            values.add(pushTypeArgumentElements(
                annotationMetadataWithDefaults,
                owningType,
                declaringType,
                typeArguments,
                loadClassValueExpressionFn
            ));
        } else {
            values.add(ExpressionDef.nullValue());
        }

        if (isTypeVariable) {
            if (!bounds.isEmpty() || isResolvedTypeVariable) {
                Set<Object> visitedTypes = new HashSet<>(5);
                if (variable != null) {
                    // A bound naming the variable it bounds names it rather than repeating it
                    visitedTypes.add(variable.getGenericNativeType());
                    visitedTypes.add(variableKey(variable.getVariableName()));
                }
                // Argument.ofTypeVariable( .. ) keeping the bounds declared for the variable, or
                // Argument.ofResolvedTypeVariable( .. ) for a type resolved in place of it
                return TYPE_ARGUMENT.invokeStatic(
                    isResolvedTypeVariable ? METHOD_CREATE_RESOLVED_TYPE_VAR : METHOD_CREATE_TYPE_VAR_WITH_BOUNDS,
                    argumentTypeConstant,
                    ExpressionDef.constant(argumentName),
                    hasVariableName ? ExpressionDef.constant(variableName) : ExpressionDef.nullValue(),
                    values.get(values.size() - 2),
                    values.get(values.size() - 1),
                    pushBounds(annotationMetadataWithDefaults, owningType, bounds, visitedTypes, loadClassValueExpressionFn)
                );
            }
            // Argument.create( .. )
            return TYPE_ARGUMENT.invokeStatic(
                hasVariableName ? METHOD_CREATE_GENERIC_PLACEHOLDER_WITH_ANNOTATION_METADATA_GENERICS : METHOD_CREATE_TYPE_VAR_WITH_ANNOTATION_METADATA_GENERICS,
                values
            );
        } else {
            // Argument.ofRawType( .. ) / Argument.create( .. )
            return TYPE_ARGUMENT.invokeStatic(
                isRawType ? METHOD_CREATE_RAW_TYPE : METHOD_CREATE_ARGUMENT_WITH_ANNOTATION_METADATA_GENERICS,
                values
            );
        }
    }

    private static TypedElement resolveArgument(TypedElement argumentType) {
        if (argumentType instanceof GenericPlaceholderElement placeholderElement) {
            ClassElement resolved = placeholderElement.getResolved().orElse(
                placeholderElement.getBounds().get(0)
            );
            TypedElement typedElement = resolveArgument(
                resolved
            );
            if (argumentType.isArray()) {
                if (typedElement instanceof ArrayableClassElement arrayableClassElement) {
                    return arrayableClassElement.withArrayDimensions(argumentType.getArrayDimensions());
                }
                return typedElement;
            }
            return typedElement;
        }
        if (argumentType instanceof WildcardElement wildcardElement) {
            return resolveArgument(
                wildcardElement.getResolved().orElseGet(() -> {
                        if (!wildcardElement.getLowerBounds().isEmpty()) {
                            return wildcardElement.getLowerBounds().get(0);
                        }
                        if (!wildcardElement.getUpperBounds().isEmpty()) {
                            return wildcardElement.getUpperBounds().get(0);
                        }
                        return ClassElement.of(Object.class);
                    }
                )
            );
        }
        return argumentType;
    }

    /**
     * Creates type arguments onto the stack.
     *
     * @param annotationMetadataWithDefaults The annotation metadata with defaults
     * @param owningType                     The owning type
     * @param declaringType                  The declaring class element of the generics
     * @param types                          The type references
     * @param loadClassValueExpressionFn     The load type expression fn
     * @return The expression
     */
    public static ExpressionDef pushTypeArgumentElements(
        AnnotationMetadata annotationMetadataWithDefaults,
        ClassTypeDef owningType,
        ClassElement declaringType,
        Map<String, ClassElement> types,
        Function<String, ExpressionDef> loadClassValueExpressionFn) {
        if (types == null || types.isEmpty()) {
            return TYPE_ARGUMENT_ARRAY.instantiate();
        }
        return pushTypeArgumentElements(
            annotationMetadataWithDefaults,
            owningType,
            declaringType,
            null,
            types,
            new Visit(new HashSet<>(5), false),
            loadClassValueExpressionFn);
    }

    @SuppressWarnings("java:S1872")
    private static ExpressionDef pushTypeArgumentElements(
        AnnotationMetadata annotationMetadataWithDefaults,
        ClassTypeDef owningType,
        ClassElement declaringType,
        @Nullable
        ClassElement element,
        Map<String, ClassElement> types,
        Visit visit,
        Function<String, ExpressionDef> loadClassValueExpressionFn) {
        Set<Object> visitedTypes = visit.visitedTypes();
        boolean inBounds = visit.inBounds();
        if (element == null) {
            if (visitedTypes.contains(declaringType.getName())) {
                return TYPE_ARGUMENT.getStaticField(ZERO_ARGUMENTS_CONSTANT, TYPE_ARGUMENT_ARRAY);
            } else {
                visitedTypes.add(declaringType.getName());
            }
        }

        return TYPE_ARGUMENT_ARRAY.instantiate(types.entrySet().stream().map(entry -> {
            String argumentName = entry.getKey();
            ClassElement classElement = entry.getValue();
            Map<String, ClassElement> typeArguments = classElement.getTypeArguments();
            if (CollectionUtils.isNotEmpty(typeArguments)
                || !classElement.getAnnotationMetadata().isEmpty()
                || classElement instanceof WildcardElement
                || isRawType(classElement)
                || !boundsToRecord(classElement).isEmpty()) {
                return buildArgumentWithGenerics(
                    annotationMetadataWithDefaults,
                    owningType,
                    argumentName,
                    classElement,
                    typeArguments,
                    visit,
                    loadClassValueExpressionFn
                );
            }
            return buildArgument(argumentName, classElement);
        }).toList());
    }

    /**
     * Builds generic type arguments recursively.
     *
     * @param annotationMetadataWithDefaults The annotation metadata with defaults
     * @param owningType                     The owning type
     * @param argumentName                   The argument name
     * @param argumentType                   The argument type
     * @param typeArguments                  The nested type arguments
     * @param visitedTypes                   The visited types
     * @param loadClassValueExpressionFn     The load type method fn
     * @return The expression
     */
    static ExpressionDef buildArgumentWithGenerics(
        AnnotationMetadata annotationMetadataWithDefaults,
        ClassTypeDef owningType,
        String argumentName,
        ClassElement argumentType,
        Map<String, ClassElement> typeArguments,
        Set<Object> visitedTypes,
        Function<String, ExpressionDef> loadClassValueExpressionFn) {
        return buildArgumentWithGenerics(
            annotationMetadataWithDefaults,
            owningType,
            argumentName,
            argumentType,
            typeArguments,
            new Visit(visitedTypes, false),
            loadClassValueExpressionFn
        );
    }

    /**
     * Builds generic type arguments recursively.
     *
     * @param annotationMetadataWithDefaults The annotation metadata with defaults
     * @param owningType                     The owning type
     * @param argumentName                   The argument name, {@code null} for a bound
     * @param argumentType                   The argument type
     * @param typeArguments                  The nested type arguments
     * @param visit                          The visited types, and whether the argument is inside bounds
     * @param loadClassValueExpressionFn     The load type method fn
     * @return The expression
     */
    private static ExpressionDef buildArgumentWithGenerics(
        AnnotationMetadata annotationMetadataWithDefaults,
        ClassTypeDef owningType,
        @Nullable String argumentName,
        ClassElement argumentType,
        Map<String, ClassElement> typeArguments,
        Visit visit,
        Function<String, ExpressionDef> loadClassValueExpressionFn) {
        // A variable is visited for as long as its own argument is being written, which is what stops at the T
        // within the bounds of T extends Comparable<T>. It is not visited for what is written after it, so that a
        // variable is written the same way wherever it is met: the second T of Map<T, T> is the variable again, and
        // the U within the bounds of T extends Comparable<U> keeps its own bounds
        Set<Object> visitedTypes = visit.visitedTypes();
        Set<Object> visitedBefore = new HashSet<>(visitedTypes);
        try {
            return buildVisitedArgumentWithGenerics(
                annotationMetadataWithDefaults,
                owningType,
                argumentName,
                argumentType,
                typeArguments,
                visit,
                loadClassValueExpressionFn
            );
        } finally {
            visitedTypes.retainAll(visitedBefore);
        }
    }

    private static ExpressionDef buildVisitedArgumentWithGenerics(
        AnnotationMetadata annotationMetadataWithDefaults,
        ClassTypeDef owningType,
        @Nullable String argumentName,
        ClassElement argumentType,
        Map<String, ClassElement> typeArguments,
        Visit visit,
        Function<String, ExpressionDef> loadClassValueExpressionFn) {
        Set<Object> visitedTypes = visit.visitedTypes();
        boolean inBounds = visit.inBounds();
        ExpressionDef.Constant argumentTypeConstant = ExpressionDef.constant(TypeDef.erasure(resolveArgument(argumentType)));

        List<ExpressionDef> values = new ArrayList<>();

        // The bounds and the variable's own name are read from the placeholder, before it is replaced by the
        // type it resolves to
        List<? extends ClassElement> bounds = boundsToRecord(argumentType);
        // As is the rawness, which is a property of the usage rather than of the type it resolves to
        boolean isRawType = isRawType(argumentType);
        // And the variable the placeholder ends at, when it was not resolved to a type: a placeholder resolved to
        // another placeholder is that other variable
        GenericPlaceholderElement variable = unresolvedVariable(argumentType);
        String variableName;
        if (variable != null) {
            variableName = variable.getVariableName();
        } else {
            variableName = argumentType instanceof GenericPlaceholderElement placeholder
                ? placeholder.getVariableName() : null;
        }

        // Persist only type annotations added to the type argument
        // A placeholder combines annotations on its binding and on this occurrence. Snapshot that
        // metadata before resolving it, without adding occurrence annotations to the shared bound type.
        MutableAnnotationMetadata annotationMetadata = MutableAnnotationMetadata.of(argumentType.getTypeAnnotationMetadata());
        if (argumentType instanceof GenericPlaceholderElement placeholderElement) {
            // Persist resolved placeholder for backward compatibility
            argumentType = placeholderElement.getResolved().orElse(argumentType);
        }

        boolean hasAnnotationMetadata = !annotationMetadata.isEmpty();
        boolean isWildcard = argumentType instanceof WildcardElement;

        boolean isRecursiveType = false;
        if (argumentType instanceof GenericPlaceholderElement placeholderElement) {
            // Prevent placeholder recursion
            Object genericNativeType = placeholderElement.getGenericNativeType();
            if (visitedTypes.contains(genericNativeType) || visitedTypes.contains(variableKey(placeholderElement.getVariableName()))) {
                isRecursiveType = true;
            } else {
                visitedTypes.add(genericNativeType);
            }
        }
        if (variable != null && !isRecursiveType) {
            // The variable the bounds are written for, which a bound naming it names rather than repeats
            visitedTypes.add(variable.getGenericNativeType());
            visitedTypes.add(variableKey(variable.getVariableName()));
        }

        boolean typeVariable = argumentType.isTypeVariable();
        // A type that took the place of a variable, which is still written as a placeholder of the variable
        boolean isResolvedTypeVariable = typeVariable && !isWildcard && variable == null;

        // 1st argument: the type
        values.add(argumentTypeConstant);
        // 2nd argument: the name
        values.add(nullableConstant(argumentName));

        if (isRecursiveType && inBounds && variableName != null) {
            // Inside a bound the variable met again is the variable, the T of the Comparable<T> that bounds T,
            // which a plain argument of its erasure would make look resolved
            // Argument.ofTypeVariable( .. )
            return TYPE_ARGUMENT.invokeStatic(
                METHOD_GENERIC_PLACEHOLDER_SIMPLE,
                values.get(0),
                values.get(1),
                ExpressionDef.constant(variableName)
            );
        }

        if (isRecursiveType || !isWildcard && !typeVariable && !isRawType && !hasAnnotationMetadata && typeArguments.isEmpty()) {
            // Argument.create( .. )
            return TYPE_ARGUMENT.invokeStatic(
                METHOD_CREATE_ARGUMENT_SIMPLE,
                values
            );
        }

        // 3rd argument: annotation metadata
        if (hasAnnotationMetadata) {
            MutableAnnotationMetadata.contributeDefaults(
                annotationMetadataWithDefaults,
                annotationMetadata
            );

            values.add(
                AnnotationMetadataGenUtils.instantiateNewMetadata(
                    annotationMetadata,
                    loadClassValueExpressionFn
                )
            );
        } else {
            values.add(ExpressionDef.nullValue());
        }

        // 4th argument, more generics
        values.add(
            pushTypeArgumentElements(
                annotationMetadataWithDefaults,
                owningType,
                argumentType,
                argumentType,
                typeArguments,
                visit,
                loadClassValueExpressionFn
            )
        );

        if (argumentType instanceof WildcardElement wildcardElement) {
            // The argument is the bound the wildcard resolves to; the bounds are kept the way
            // java.lang.reflect.WildcardType reports them: Object above unless declared otherwise
            List<? extends ClassElement> upperBounds = List.of();
            List<? extends ClassElement> lowerBounds = List.of();
            if (wildcardElement.hasExplicitLowerBound()) {
                lowerBounds = wildcardElement.getLowerBounds();
            } else if (wildcardElement.hasExplicitUpperBound()) {
                upperBounds = wildcardElement.getUpperBounds();
            }
            // 5th and 6th arguments: the bounds
            values.add(pushBounds(annotationMetadataWithDefaults, owningType, upperBounds, visitedTypes, loadClassValueExpressionFn));
            values.add(pushBounds(annotationMetadataWithDefaults, owningType, lowerBounds, visitedTypes, loadClassValueExpressionFn));
            // Argument.ofWildcard( .. )
            return TYPE_ARGUMENT.invokeStatic(METHOD_CREATE_WILDCARD, values);
        }

        // The name the variable was declared with, which is not the name of the argument: a type argument is
        // named after the parameter it stands in for - the E of List<E> - while the variable is the M of List<M>
        boolean hasVariableName = typeVariable && variableName != null && !variableName.equals(argumentName);

        if (typeVariable && (!bounds.isEmpty() || isResolvedTypeVariable)) {
            // Argument.ofTypeVariable( .. ) keeping the bounds declared for the variable, or
            // Argument.ofResolvedTypeVariable( .. ) for a type resolved in place of it
            return TYPE_ARGUMENT.invokeStatic(
                isResolvedTypeVariable ? METHOD_CREATE_RESOLVED_TYPE_VAR : METHOD_CREATE_TYPE_VAR_WITH_BOUNDS,
                values.get(0),
                values.get(1),
                hasVariableName ? ExpressionDef.constant(variableName) : ExpressionDef.nullValue(),
                values.get(2),
                values.get(3),
                pushBounds(annotationMetadataWithDefaults, owningType, bounds, visitedTypes, loadClassValueExpressionFn)
            );
        }

        if (isRawType && !typeVariable) {
            // Argument.ofRawType( .. )
            return TYPE_ARGUMENT.invokeStatic(METHOD_CREATE_RAW_TYPE, values);
        }

        if (hasVariableName) {
            // Argument.ofTypeVariable( .. ) named after the variable
            return TYPE_ARGUMENT.invokeStatic(
                METHOD_CREATE_GENERIC_PLACEHOLDER_WITH_ANNOTATION_METADATA_GENERICS,
                values.get(0),
                values.get(1),
                ExpressionDef.constant(variableName),
                values.get(2),
                values.get(3)
            );
        }

        // Argument.create( .. )
        return TYPE_ARGUMENT.invokeStatic(
            typeVariable ? METHOD_CREATE_TYPE_VAR_WITH_ANNOTATION_METADATA_GENERICS : METHOD_CREATE_ARGUMENT_WITH_ANNOTATION_METADATA_GENERICS,
            values
        );
    }

    /**
     * A constant for a value that may be missing, the name of a bound for one.
     *
     * @param value The value
     * @return The expression
     */
    private static ExpressionDef nullableConstant(@Nullable String value) {
        return value == null ? ExpressionDef.nullValue() : ExpressionDef.constant(value);
    }

    /**
     * Whether the element is a type variable left unresolved, the {@code T} of a factory method returning it.
     *
     * @param element The element
     * @return Whether it is an unresolved variable
     */
    public static boolean isUnresolvedVariable(TypedElement element) {
        return element instanceof GenericPlaceholderElement && unresolvedVariable(element) == element;
    }

    /**
     * The type variable an element stands for when it was left unresolved: the placeholder itself, or, for a
     * placeholder resolved to another placeholder, the one it ends at. A placeholder that ends at a type, and
     * an element that is not a placeholder, stand for no variable.
     *
     * @param element The element
     * @return The unresolved variable, or {@code null}
     */
    @Nullable
    private static GenericPlaceholderElement unresolvedVariable(TypedElement element) {
        TypedElement current = element;
        // a bounded walk: a placeholder is resolved to another one at most a few times over
        for (int i = 0; i < 16 && current instanceof GenericPlaceholderElement placeholder; i++) {
            Optional<ClassElement> resolved = placeholder.getResolved();
            if (resolved.isEmpty()) {
                return placeholder;
            }
            current = resolved.get();
        }
        return null;
    }

    /**
     * Whether the element is a type written without its type arguments, a raw type.
     *
     * <p>A raw usage compiles to the type arguments the declaring type declares, so it is otherwise
     * indistinguishable from a usage written with those variables. Java marks the usage itself, while Groovy
     * marks only the placeholders it compiles to, so both are asked. A wildcard is not asked: Kotlin marks the
     * argument of a star projection raw, and {@code List<*>} is {@code List<?>} rather than a raw usage.</p>
     *
     * @param element The element
     * @return true if the type is raw
     */
    public static boolean isRawType(TypedElement element) {
        if (element instanceof GenericPlaceholderElement || element instanceof WildcardElement
            || !(element instanceof ClassElement classElement)) {
            // a variable or a wildcard is written as such, it is never a raw usage of a type
            return false;
        }
        if (classElement.isRawType()) {
            return true;
        }
        Collection<ClassElement> typeArguments = classElement.getTypeArguments().values();
        return !typeArguments.isEmpty() && typeArguments.stream()
            .allMatch(typeArgument -> typeArgument instanceof GenericPlaceholderElement && typeArgument.isRawType());
    }

    /**
     * The bounds to record for an element that is written as a placeholder: those of the variable it was left
     * as, or, for a type resolved in place of a variable, those of the placeholder that was resolved.
     *
     * @param element The element
     * @return The bounds to record
     */
    private static List<? extends ClassElement> boundsToRecord(TypedElement element) {
        GenericPlaceholderElement variable = unresolvedVariable(element);
        return recordedBounds(variable != null ? variable : element);
    }

    /**
     * The bounds to record for a type variable, empty when the type the variable compiles to already says what
     * they are: a variable with a single bound erases to that bound, so only several bounds, a resolved variable
     * whose erasure is no longer its bound, or a bound that names a type variable - which the type arguments of
     * the erasure cut short where the variable names itself - need them written out.
     *
     * @param element The element
     * @return The bounds to record
     */
    private static List<? extends ClassElement> recordedBounds(TypedElement element) {
        if (element instanceof WildcardElement || !(element instanceof GenericPlaceholderElement placeholderElement)) {
            return List.of();
        }
        List<? extends ClassElement> bounds = placeholderElement.getBounds();
        if (bounds.size() < 2 && placeholderElement.getResolved().isEmpty()
            // a bound that is itself a variable, the U of T extends U, is not what the erasure says
            && bounds.stream().noneMatch(GenericPlaceholderElement.class::isInstance)
            && !namesTypeVariable(bounds, 0)) {
            return List.of();
        }
        return bounds;
    }

    /**
     * Whether any of the types names a type variable among its type arguments, at any depth.
     *
     * @param types The types
     * @param depth The depth reached
     * @return Whether a type variable is named
     */
    private static boolean namesTypeVariable(Collection<? extends ClassElement> types, int depth) {
        if (depth > 8) {
            return false;
        }
        for (ClassElement type : types) {
            for (ClassElement typeArgument : type.getTypeArguments().values()) {
                if (typeArgument instanceof WildcardElement wildcard) {
                    if (isOrNamesTypeVariable(wildcard.getUpperBounds(), depth) || isOrNamesTypeVariable(wildcard.getLowerBounds(), depth)) {
                        return true;
                    }
                } else if (typeArgument instanceof GenericPlaceholderElement
                    || namesTypeVariable(List.of(typeArgument), depth + 1)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isOrNamesTypeVariable(Collection<? extends ClassElement> types, int depth) {
        for (ClassElement type : types) {
            if (type instanceof GenericPlaceholderElement || namesTypeVariable(List.of(type), depth + 1)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The key a variable is visited by, besides the native type it was met as: the native type of a variable named
     * within its own bounds is not always the one the variable itself has.
     */
    private static String variableKey(String variableName) {
        return "<" + variableName + ">";
    }

    private static ExpressionDef pushBounds(AnnotationMetadata annotationMetadataWithDefaults,
                                            ClassTypeDef owningType,
                                            List<? extends ClassElement> bounds,
                                            Set<Object> visitedTypes,
                                            Function<String, ExpressionDef> loadClassValueExpressionFn) {
        if (bounds.isEmpty()) {
            return ExpressionDef.nullValue();
        }
        return TYPE_ARGUMENT_ARRAY.instantiate(bounds.stream().map(bound -> {
            if (unresolvedVariable(bound) != null) {
                // A bound that is itself a type variable - the U of T extends U, or of ? super U - is the variable,
                // with its own name and bounds, rather than the type it erases to
                return buildArgumentWithGenerics(
                    annotationMetadataWithDefaults,
                    owningType,
                    null,
                    bound,
                    bound.getTypeArguments(),
                    new Visit(visitedTypes, true),
                    loadClassValueExpressionFn
                );
            }
            ExpressionDef.Constant boundTypeConstant = ExpressionDef.constant(TypeDef.erasure(resolveArgument(bound)));
            Map<String, ClassElement> boundTypeArguments = bound.getTypeArguments();
            // A bound written raw - the List of ? extends List - keeps the variables its type declares, and says so
            boolean rawBound = isRawType(bound);
            // Persist only type annotations added to the bound
            MutableAnnotationMetadata boundAnnotationMetadata = MutableAnnotationMetadata.of(bound.getTypeAnnotationMetadata());
            if (boundTypeArguments.isEmpty() && boundAnnotationMetadata.isEmpty()) {
                // Argument.of(Class)
                return TYPE_ARGUMENT.invokeStatic(METHOD_CREATE_ARGUMENT_CLASS, boundTypeConstant);
            }
            ExpressionDef annotationMetadataExp;
            if (boundAnnotationMetadata.isEmpty()) {
                annotationMetadataExp = ExpressionDef.nullValue();
            } else {
                MutableAnnotationMetadata.contributeDefaults(annotationMetadataWithDefaults, boundAnnotationMetadata);
                annotationMetadataExp = AnnotationMetadataGenUtils.instantiateNewMetadata(boundAnnotationMetadata, loadClassValueExpressionFn);
            }
            // Argument.of(Class, null, AnnotationMetadata, Argument[]) / Argument.ofRawType( .. )
            return TYPE_ARGUMENT.invokeStatic(
                rawBound ? METHOD_CREATE_RAW_TYPE : METHOD_CREATE_ARGUMENT_WITH_ANNOTATION_METADATA_GENERICS,
                boundTypeConstant,
                ExpressionDef.nullValue(),
                annotationMetadataExp,
                boundTypeArguments.isEmpty() ? ExpressionDef.nullValue() : pushTypeArgumentElements(
                    annotationMetadataWithDefaults,
                    owningType,
                    bound,
                    bound,
                    boundTypeArguments,
                    new Visit(visitedTypes, true),
                    loadClassValueExpressionFn
                )
            );
        }).toList());
    }

    /**
     * Builds an argument instance.
     *
     * @param argumentName The argument name
     * @param argumentType The argument type
     * @return The expression
     */
    private static ExpressionDef buildArgument(String argumentName, ClassElement argumentType) {
        ExpressionDef.Constant argumentTypeConstant = ExpressionDef.constant(TypeDef.erasure(resolveArgument(argumentType)));
        ExpressionDef.Constant argumentNameConstant = ExpressionDef.constant(argumentName);
        GenericPlaceholderElement variable = unresolvedVariable(argumentType);

        if (argumentType instanceof GenericPlaceholderElement placeholderElement) {
            // Persist resolved placeholder for backward compatibility
            argumentType = placeholderElement.getResolved().orElse(placeholderElement);
        }

        if (argumentType instanceof GenericPlaceholderElement || argumentType.isTypeVariable()) {
            String variableName = argumentName;
            if (variable != null) {
                variableName = variable.getVariableName();
            } else if (argumentType instanceof GenericPlaceholderElement placeholderElement) {
                variableName = placeholderElement.getVariableName();
            }
            boolean hasVariable = !variableName.equals(argumentName);
            if (variable == null && !(argumentType instanceof WildcardElement)) {
                // A type that took the place of a variable, which is still written as a placeholder of the variable
                // Argument.ofResolvedTypeVariable( .. )
                return TYPE_ARGUMENT.invokeStatic(
                    METHOD_CREATE_RESOLVED_TYPE_VAR,
                    argumentTypeConstant,
                    argumentNameConstant,
                    hasVariable ? ExpressionDef.constant(variableName) : ExpressionDef.nullValue(),
                    ExpressionDef.nullValue(),
                    ExpressionDef.nullValue(),
                    ExpressionDef.nullValue()
                );
            }
            if (hasVariable) {
                return TYPE_ARGUMENT.invokeStatic(
                    METHOD_GENERIC_PLACEHOLDER_SIMPLE,

                    // 1st argument: the type
                    argumentTypeConstant,
                    // 2nd argument: the name
                    argumentNameConstant,
                    // 3nd argument: the variable
                    ExpressionDef.constant(variableName)
                );
            }
            // Argument.create( .. )
            return TYPE_ARGUMENT.invokeStatic(
                METHOD_CREATE_TYPE_VARIABLE_SIMPLE,
                // 1st argument: the type
                argumentTypeConstant,
                // 2nd argument: the name
                argumentNameConstant
            );
        }
        // Argument.create( .. )
        return TYPE_ARGUMENT.invokeStatic(
            METHOD_CREATE_ARGUMENT_SIMPLE,
            // 1st argument: the type
            argumentTypeConstant,
            // 2nd argument: the name
            argumentNameConstant
        );
    }

    /**
     * Builds generic type arguments recursively.
     *
     * @param type               The type that declares the generics
     * @param annotationMetadata The annotation metadata reference
     * @param generics           The generics
     * @return The expression
     */
    public static ExpressionDef buildArgumentWithGenerics(TypeDef type,
                                                          AnnotationMetadataReference annotationMetadata,
                                                          ClassElement[] generics) {

        return TYPE_ARGUMENT.invokeStatic(
            METHOD_CREATE_ARGUMENT_WITH_ANNOTATION_METADATA_CLASS_GENERICS,

            // 1st argument: the type
            ExpressionDef.constant(type),
            // 2nd argument: the annotation metadata
            AnnotationMetadataGenUtils.annotationMetadataReference(annotationMetadata),
            // 3rd argument: generics
            ClassTypeDef.of(Class.class).array().instantiate(
                Arrays.stream(generics).map(g -> ExpressionDef.constant(TypeDef.erasure(g))).toList()
            )
        );
    }

    /**
     * @param annotationMetadataWithDefaults The annotation metadata with defaults
     * @param declaringElement               The declaring element name
     * @param owningType                     The owning type
     * @param argumentTypes                  The argument types
     * @param loadClassValueExpressionFn     The load type method expression fn
     * @return The expression
     */
    public static ExpressionDef pushBuildArgumentsForMethod(AnnotationMetadata annotationMetadataWithDefaults,
                                                            ClassElement declaringElement,
                                                            ClassTypeDef owningType,
                                                            Collection<ParameterElement> argumentTypes,
                                                            Function<String, ExpressionDef> loadClassValueExpressionFn) {

        return TYPE_ARGUMENT_ARRAY.instantiate(argumentTypes.stream().map(parameterElement -> {
            ClassElement genericType = parameterElement.getGenericType();

            MutableAnnotationMetadata.contributeDefaults(
                annotationMetadataWithDefaults,
                parameterElement.getAnnotationMetadata()
            );
            MutableAnnotationMetadata.contributeDefaults(
                annotationMetadataWithDefaults,
                genericType.getTypeAnnotationMetadata()
            );

            String argumentName = parameterElement.getName();
            MutableAnnotationMetadata annotationMetadata = new AnnotationMetadataHierarchy(
                parameterElement.getAnnotationMetadata(),
                genericType.getTypeAnnotationMetadata()
            ).merge();

            if (parameterElement.hasDefault()) {
                annotationMetadata.removeAnnotation(AnnotationUtil.NON_NULL);
                annotationMetadata.addAnnotation(AnnotationUtil.NULLABLE, Map.of());
                annotationMetadata.addDeclaredAnnotation(AnnotationUtil.NULLABLE, Map.of());
            }

            Map<String, ClassElement> typeArguments = genericType.getTypeArguments();
            return pushCreateArgument(
                annotationMetadataWithDefaults,
                declaringElement,
                owningType,
                argumentName,
                genericType,
                annotationMetadata,
                typeArguments,
                loadClassValueExpressionFn
            );
        }).toList());

    }

    /**
     * Where the writing of a type argument stands.
     *
     * @param visitedTypes The types visited so far, to stop at a type that names itself
     * @param inBounds     Whether the argument is written inside the bounds of a variable or a wildcard, where a
     *                     variable met again is written as the variable
     */
    private record Visit(Set<Object> visitedTypes, boolean inBounds) {
    }

}
