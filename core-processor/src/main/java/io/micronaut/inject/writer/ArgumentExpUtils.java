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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.annotation.AnnotationMetadataGenUtils;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.annotation.AnnotationMetadataReference;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.inject.ast.ArrayableClassElement;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.KotlinParameterElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.ast.WildcardElement;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.TypeDef;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The argument expression utils.
 *
 * @author Denis Stepanov
 * @since 4.8
 */
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

    private static final Method METHOD_CREATE_ARGUMENT_WITH_ANNOTATION_METADATA_CLASS_GENERICS = ReflectionUtils.getRequiredInternalMethod(
        Argument.class,
        "of",
        Class.class,
        AnnotationMetadata.class,
        Class[].class
    );

    /**
     * Creates an argument.
     *
     * @param annotationMetadataWithDefaults The annotation metadata with defaults
     * @param owningType                     The owning type
     * @param declaringType                  The declaring type name
     * @param argument                       The argument
     * @param loadTypeMethods                The load type methods
     * @return The expression
     */
    public static ExpressionDef pushReturnTypeArgument(AnnotationMetadata annotationMetadataWithDefaults,
                                                       ClassTypeDef owningType,
                                                       ClassElement declaringType,
                                                       ClassElement argument,
                                                       Map<String, MethodDef> loadTypeMethods) {
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
            loadTypeMethods
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
     * @param loadTypeMethods                The load type methods
     * @return The expression
     */
    public static ExpressionDef pushCreateArgument(
        AnnotationMetadata annotationMetadataWithDefaults,
        ClassElement declaringType,
        ClassTypeDef owningType,
        String argumentName,
        ClassElement argument,
        Map<String, MethodDef> loadTypeMethods) {

        return pushCreateArgument(
            annotationMetadataWithDefaults,
            declaringType,
            owningType,
            argumentName,
            argument,
            argument.getAnnotationMetadata(),
            argument.getTypeArguments(),
            loadTypeMethods
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
     * @param loadTypeMethods                The load type methods
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
        Map<String, MethodDef> loadTypeMethods) {
        annotationMetadata = MutableAnnotationMetadata.of(annotationMetadata);
        ExpressionDef.Constant argumentTypeConstant = ExpressionDef.constant(TypeDef.erasure(resolveArgument(argumentType)));

        boolean hasAnnotations = !annotationMetadata.isEmpty();
        boolean hasTypeArguments = typeArguments != null && !typeArguments.isEmpty();
        if (argumentType instanceof GenericPlaceholderElement placeholderElement) {
            // Persist resolved placeholder for backward compatibility
            argumentType = placeholderElement.getResolved().orElse(placeholderElement);
        }
        boolean isGenericPlaceholder = argumentType instanceof GenericPlaceholderElement;
        boolean isTypeVariable = isGenericPlaceholder || ((argumentType instanceof ClassElement classElement) && classElement.isTypeVariable());
        String variableName = argumentName;
        if (isGenericPlaceholder) {
            variableName = ((GenericPlaceholderElement) argumentType).getVariableName();
        }
        boolean hasVariableName = !variableName.equals(argumentName);

        List<ExpressionDef> values = new ArrayList<>();

        // 1st argument: The type
        values.add(argumentTypeConstant);
        // 2nd argument: The argument name
        values.add(ExpressionDef.constant(argumentName));

        if (!hasAnnotations && !hasTypeArguments && !isTypeVariable) {
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
                owningType,
                (MutableAnnotationMetadata) annotationMetadata,
                loadTypeMethods
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
                loadTypeMethods
            ));
        } else {
            values.add(ExpressionDef.nullValue());
        }

        if (isTypeVariable) {
            // Argument.create( .. )
            return TYPE_ARGUMENT.invokeStatic(
                hasVariableName ? METHOD_CREATE_GENERIC_PLACEHOLDER_WITH_ANNOTATION_METADATA_GENERICS : METHOD_CREATE_TYPE_VAR_WITH_ANNOTATION_METADATA_GENERICS,
                values
            );
        } else {
            // Argument.create( .. )
            return TYPE_ARGUMENT.invokeStatic(
                METHOD_CREATE_ARGUMENT_WITH_ANNOTATION_METADATA_GENERICS,
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
     * @param loadTypeMethods                The load type methods
     * @return The expression
     */
    static ExpressionDef pushTypeArgumentElements(
        AnnotationMetadata annotationMetadataWithDefaults,
        ClassTypeDef owningType,
        ClassElement declaringType,
        Map<String, ClassElement> types,
        Map<String, MethodDef> loadTypeMethods) {
        if (types == null || types.isEmpty()) {
            return TYPE_ARGUMENT_ARRAY.instantiate();
        }
        return pushTypeArgumentElements(
            annotationMetadataWithDefaults,
            owningType,
            declaringType,
            null,
            types,
            new HashSet<>(5),
            loadTypeMethods);
    }

    @SuppressWarnings("java:S1872")
    private static ExpressionDef pushTypeArgumentElements(
        AnnotationMetadata annotationMetadataWithDefaults,
        ClassTypeDef owningType,
        ClassElement declaringType,
        @Nullable
        ClassElement element,
        Map<String, ClassElement> types,
        Set<Object> visitedTypes,
        Map<String, MethodDef> loadTypeMethods) {
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
            if (CollectionUtils.isNotEmpty(typeArguments) || !classElement.getAnnotationMetadata().isEmpty()) {
                return buildArgumentWithGenerics(
                    annotationMetadataWithDefaults,
                    owningType,
                    argumentName,
                    classElement,
                    typeArguments,
                    visitedTypes,
                    loadTypeMethods
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
     * @param loadTypeMethods                The load type methods
     * @return The expression
     */
    static ExpressionDef buildArgumentWithGenerics(
        AnnotationMetadata annotationMetadataWithDefaults,
        ClassTypeDef owningType,
        String argumentName,
        ClassElement argumentType,
        Map<String, ClassElement> typeArguments,
        Set<Object> visitedTypes,
        Map<String, MethodDef> loadTypeMethods) {
        ExpressionDef.Constant argumentTypeConstant = ExpressionDef.constant(TypeDef.erasure(resolveArgument(argumentType)));

        List<ExpressionDef> values = new ArrayList<>();

        if (argumentType instanceof GenericPlaceholderElement placeholderElement) {
            // Persist resolved placeholder for backward compatibility
            argumentType = placeholderElement.getResolved().orElse(argumentType);
        }

        // Persist only type annotations added to the type argument
        AnnotationMetadata annotationMetadata = MutableAnnotationMetadata.of(argumentType.getTypeAnnotationMetadata());
        boolean hasAnnotationMetadata = !annotationMetadata.isEmpty();

        boolean isRecursiveType = false;
        if (argumentType instanceof GenericPlaceholderElement placeholderElement) {
            // Prevent placeholder recursion
            Object genericNativeType = placeholderElement.getGenericNativeType();
            if (visitedTypes.contains(genericNativeType)) {
                isRecursiveType = true;
            } else {
                visitedTypes.add(genericNativeType);
            }
        }

        boolean typeVariable = argumentType.isTypeVariable();

        // 1st argument: the type
        values.add(argumentTypeConstant);
        // 2nd argument: the name
        values.add(ExpressionDef.constant(argumentName));


        if (isRecursiveType || !typeVariable && !hasAnnotationMetadata && typeArguments.isEmpty()) {
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
                    owningType,
                    (MutableAnnotationMetadata) annotationMetadata,
                    loadTypeMethods
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
                visitedTypes,
                loadTypeMethods
            )
        );

        // Argument.create( .. )
        return TYPE_ARGUMENT.invokeStatic(
            typeVariable ? METHOD_CREATE_TYPE_VAR_WITH_ANNOTATION_METADATA_GENERICS : METHOD_CREATE_ARGUMENT_WITH_ANNOTATION_METADATA_GENERICS,
            values
        );
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

        if (argumentType instanceof GenericPlaceholderElement placeholderElement) {
            // Persist resolved placeholder for backward compatibility
            argumentType = placeholderElement.getResolved().orElse(placeholderElement);
        }

        if (argumentType instanceof GenericPlaceholderElement || argumentType.isTypeVariable()) {
            String variableName = argumentName;
            if (argumentType instanceof GenericPlaceholderElement placeholderElement) {
                variableName = placeholderElement.getVariableName();
            }
            boolean hasVariable = !variableName.equals(argumentName);
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
     * @param loadTypeMethods                The load type methods
     * @return The expression
     */
    public static ExpressionDef pushBuildArgumentsForMethod(
        AnnotationMetadata annotationMetadataWithDefaults,
        ClassElement declaringElement,
        ClassTypeDef owningType,
        Collection<ParameterElement> argumentTypes,
        Map<String, MethodDef> loadTypeMethods) {

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

            if (parameterElement instanceof KotlinParameterElement kp && kp.hasDefault()) {
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
                loadTypeMethods
            );
        }).toList());

    }

}
