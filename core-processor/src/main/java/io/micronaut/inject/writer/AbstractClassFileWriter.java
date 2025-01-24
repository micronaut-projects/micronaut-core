/*
 * Copyright 2017-2020 original authors
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
import io.micronaut.core.annotation.Generated;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.annotation.AnnotationMetadataReference;
import io.micronaut.inject.annotation.AnnotationMetadataWriter;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.KotlinParameterElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.processing.JavaModelUtils;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.micronaut.core.util.StringUtils.EMPTY_STRING_ARRAY;
import static io.micronaut.inject.annotation.AnnotationMetadataWriter.isSupportedMapValue;

/**
 * Abstract class that writes generated classes to disk and provides convenience methods for building classes.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public abstract class AbstractClassFileWriter implements Opcodes, OriginatingElements, ClassOutputWriter {

    protected static final Type TYPE_ARGUMENT = Type.getType(Argument.class);
    protected static final Type TYPE_ARGUMENT_ARRAY = Type.getType(Argument[].class);
    protected static final String ZERO_ARGUMENTS_CONSTANT = "ZERO_ARGUMENTS";
    protected static final String CONSTRUCTOR_NAME = "<init>";
    protected static final String DESCRIPTOR_DEFAULT_CONSTRUCTOR = "()V";
    protected static final Method METHOD_DEFAULT_CONSTRUCTOR = new Method(CONSTRUCTOR_NAME, DESCRIPTOR_DEFAULT_CONSTRUCTOR);
    protected static final Type TYPE_OBJECT = Type.getType(Object.class);
    protected static final Type TYPE_CLASS = Type.getType(Class.class);
    protected static final int DEFAULT_MAX_STACK = 23;
    protected static final Type TYPE_GENERATED = Type.getType(Generated.class);
    protected static final Pattern ARRAY_PATTERN = Pattern.compile("(\\[\\])+$");

    protected static final Method METHOD_CREATE_ARGUMENT_SIMPLE = Method.getMethod(
            ReflectionUtils.getRequiredInternalMethod(
                    Argument.class,
                    "of",
                    Class.class,
                    String.class
            )
    );
    protected static final Method METHOD_GENERIC_PLACEHOLDER_SIMPLE = Method.getMethod(
            ReflectionUtils.getRequiredInternalMethod(
                    Argument.class,
                    "ofTypeVariable",
                    Class.class,
                    String.class,
                    String.class
            )
    );
    protected static final Method METHOD_CREATE_TYPE_VARIABLE_SIMPLE = Method.getMethod(
            ReflectionUtils.getRequiredInternalMethod(
                    Argument.class,
                    "ofTypeVariable",
                    Class.class,
                    String.class
            )
    );
    private static final Method METHOD_CREATE_ARGUMENT_WITH_ANNOTATION_METADATA_GENERICS = Method.getMethod(
            ReflectionUtils.getRequiredInternalMethod(
                    Argument.class,
                    "of",
                    Class.class,
                    String.class,
                    AnnotationMetadata.class,
                    Argument[].class
            )
    );
    private static final Method METHOD_CREATE_TYPE_VAR_WITH_ANNOTATION_METADATA_GENERICS = Method.getMethod(
            ReflectionUtils.getRequiredInternalMethod(
                    Argument.class,
                    "ofTypeVariable",
                    Class.class,
                    String.class,
                    AnnotationMetadata.class,
                    Argument[].class
            )
    );
    private static final Method METHOD_CREATE_GENERIC_PLACEHOLDER_WITH_ANNOTATION_METADATA_GENERICS = Method.getMethod(
            ReflectionUtils.getRequiredInternalMethod(
                    Argument.class,
                    "ofTypeVariable",
                    Class.class,
                    String.class,
                    String.class,
                    AnnotationMetadata.class,
                    Argument[].class
            )
    );
    private static final Method METHOD_CREATE_ARGUMENT_WITH_ANNOTATION_METADATA_CLASS_GENERICS = Method.getMethod(
            ReflectionUtils.getRequiredInternalMethod(
                    Argument.class,
                    "of",
                    Class.class,
                    AnnotationMetadata.class,
                    Class[].class
            )
    );

    private static final Type MAP_TYPE = Type.getType(Map.class);
    private static final Type LIST_TYPE = Type.getType(List.class);

    private static final org.objectweb.asm.commons.Method[] MAP_OF;
    private static final org.objectweb.asm.commons.Method[] LIST_OF;
    private static final org.objectweb.asm.commons.Method MAP_BY_ARRAY;
    private static final org.objectweb.asm.commons.Method MAP_ENTRY;
    private static final org.objectweb.asm.commons.Method LIST_BY_ARRAY;

    static {
        MAP_OF = new Method[11];
        for (int i = 0; i < MAP_OF.length; i++) {
            Class[] mapArgs = new Class[i * 2];
            for (int k = 0; k < i * 2; k += 2) {
                mapArgs[k] = Object.class;
                mapArgs[k + 1] = Object.class;
            }
            MAP_OF[i] = org.objectweb.asm.commons.Method.getMethod(ReflectionUtils.getRequiredMethod(Map.class, "of", mapArgs));
        }
        MAP_ENTRY = org.objectweb.asm.commons.Method.getMethod(ReflectionUtils.getRequiredMethod(Map.class, "entry", Object.class, Object.class));
        MAP_BY_ARRAY = org.objectweb.asm.commons.Method.getMethod(ReflectionUtils.getRequiredMethod(Map.class, "ofEntries", Map.Entry[].class));
    }

    static {
        LIST_OF = new Method[11];
        for (int i = 0; i < LIST_OF.length; i++) {
            Class[] listArgs = new Class[i];
            for (int k = 0; k < i; k += 1) {
                listArgs[k] = Object.class;
            }
            LIST_OF[i] = org.objectweb.asm.commons.Method.getMethod(ReflectionUtils.getRequiredMethod(List.class, "of", listArgs));
        }
        LIST_BY_ARRAY = org.objectweb.asm.commons.Method.getMethod(ReflectionUtils.getRequiredMethod(List.class, "of", Object[].class));
    }

    protected final OriginatingElements originatingElements;

    /**
     * @param originatingElements The originating elements
     */
    protected AbstractClassFileWriter(Element... originatingElements) {
        this(OriginatingElements.of(originatingElements));
    }

    /**
     * @param originatingElements The originating elements
     */
    protected AbstractClassFileWriter(OriginatingElements originatingElements) {
        this.originatingElements = Objects.requireNonNull(originatingElements, "The originating elements cannot be null");
    }

    @NonNull
    @Override
    public Element[] getOriginatingElements() {
        return originatingElements.getOriginatingElements();
    }

    @Override
    public void addOriginatingElement(@NonNull Element element) {
        originatingElements.addOriginatingElement(element);
    }

    /**
     * Pushes type arguments onto the stack.
     *
     * @param annotationMetadataWithDefaults The annotation metadata with defaults
     * @param owningType           The owning type
     * @param owningTypeWriter     The declaring class writer
     * @param generatorAdapter     The generator adapter
     * @param declaringElementName The declaring class element of the generics
     * @param types                The type references
     * @param defaults             The annotation defaults
     * @param loadTypeMethods      The load type methods
     */
    protected static void pushTypeArgumentElements(
            AnnotationMetadata annotationMetadataWithDefaults,
            Type owningType,
            ClassWriter owningTypeWriter,
            GeneratorAdapter generatorAdapter,
            String declaringElementName,
            Map<String, ClassElement> types,
            Map<String, Integer> defaults,
            Map<String, GeneratorAdapter> loadTypeMethods) {
        if (types == null || types.isEmpty()) {
            pushNewArray(generatorAdapter, Argument.class, 0);
            return;
        }
        pushTypeArgumentElements(annotationMetadataWithDefaults, owningType, owningTypeWriter, generatorAdapter, declaringElementName, null, types, new HashSet<>(5), defaults, loadTypeMethods);
    }

    @SuppressWarnings("java:S1872")
    private static void pushTypeArgumentElements(
            AnnotationMetadata annotationMetadataWithDefaults,
            Type owningType,
            ClassWriter declaringClassWriter,
            GeneratorAdapter generatorAdapter,
            String declaringElementName,
            @Nullable
            ClassElement element,
            Map<String, ClassElement> types,
            Set<Object> visitedTypes,
            Map<String, Integer> defaults,
            Map<String, GeneratorAdapter> loadTypeMethods) {
        if (element == null) {
            if (visitedTypes.contains(declaringElementName)) {
                generatorAdapter.getStatic(
                        TYPE_ARGUMENT,
                        ZERO_ARGUMENTS_CONSTANT,
                        TYPE_ARGUMENT_ARRAY
                );
                return;
            } else {
                visitedTypes.add(declaringElementName);
            }
        }

        // Build calls to Argument.create(...)
        pushNewArray(generatorAdapter, Argument.class, types.entrySet(), entry -> {
            String argumentName = entry.getKey();
            ClassElement classElement = entry.getValue();
            Type classReference = JavaModelUtils.getTypeReference(classElement);
            Map<String, ClassElement> typeArguments = classElement.getTypeArguments();
            if (CollectionUtils.isNotEmpty(typeArguments) || !classElement.getAnnotationMetadata().isEmpty()) {
                buildArgumentWithGenerics(
                    annotationMetadataWithDefaults,
                    owningType,
                    declaringClassWriter,
                    generatorAdapter,
                    argumentName,
                    classReference,
                    classElement,
                    typeArguments,
                    visitedTypes,
                    defaults,
                    loadTypeMethods
                );
            } else {
                buildArgument(generatorAdapter, argumentName, classElement);
            }
        });
    }

    /**
     * Builds an argument instance.
     *
     * @param generatorAdapter The generator adapter.
     * @param argumentName     The argument name
     * @param objectType       The object type
     */
    protected static void buildArgument(GeneratorAdapter generatorAdapter, String argumentName, Type objectType) {
        // 1st argument: the type
        generatorAdapter.push(objectType);
        // 2nd argument: the name
        generatorAdapter.push(argumentName);

        // Argument.create( .. )
        invokeInterfaceStaticMethod(
                generatorAdapter,
                Argument.class,
                METHOD_CREATE_ARGUMENT_SIMPLE
        );
    }

    /**
     * Builds an argument instance.
     *
     * @param generatorAdapter The generator adapter.
     * @param argumentName     The argument name
     * @param objectType       The object type
     */
    protected static void buildArgument(GeneratorAdapter generatorAdapter, String argumentName, ClassElement objectType) {
        // 1st argument: the type
        generatorAdapter.push(getTypeReference(objectType));
        // 2nd argument: the name
        generatorAdapter.push(argumentName);

        if (objectType instanceof GenericPlaceholderElement placeholderElement) {
            // Persist resolved placeholder for backward compatibility
            objectType = placeholderElement.getResolved().orElse(placeholderElement);
        }

        if (objectType instanceof GenericPlaceholderElement || objectType.isTypeVariable()) {
            String variableName = argumentName;
            if (objectType instanceof GenericPlaceholderElement placeholderElement) {
                variableName = placeholderElement.getVariableName();
            }
            boolean hasVariable = !variableName.equals(argumentName);
            if (hasVariable) {
                generatorAdapter.push(variableName);
            }
            // Argument.create( .. )
            invokeInterfaceStaticMethod(
                    generatorAdapter,
                    Argument.class,
                    hasVariable ? METHOD_GENERIC_PLACEHOLDER_SIMPLE : METHOD_CREATE_TYPE_VARIABLE_SIMPLE
            );
        } else {
            // Argument.create( .. )
            invokeInterfaceStaticMethod(
                    generatorAdapter,
                    Argument.class,
                    METHOD_CREATE_ARGUMENT_SIMPLE
            );
        }
    }

    /**
     * Builds generic type arguments recursively.
     *
     * @param annotationMetadataWithDefaults The annotation metadata with defaults
     * @param owningType        The owning type
     * @param owningClassWriter The declaring writer
     * @param generatorAdapter  The generator adapter to use
     * @param argumentName      The argument name
     * @param typeReference     The type name
     * @param classElement      The class element that declares the generics
     * @param typeArguments     The nested type arguments
     * @param visitedTypes      The visited types
     * @param defaults          The annotation defaults
     * @param loadTypeMethods   The load type methods
     */
    protected static void buildArgumentWithGenerics(
            AnnotationMetadata annotationMetadataWithDefaults,
            Type owningType,
            ClassWriter owningClassWriter,
            GeneratorAdapter generatorAdapter,
            String argumentName,
            Type typeReference,
            ClassElement classElement,
            Map<String, ClassElement> typeArguments,
            Set<Object> visitedTypes,
            Map<String, Integer> defaults,
            Map<String, GeneratorAdapter> loadTypeMethods) {
        // 1st argument: the type
        generatorAdapter.push(typeReference);
        // 2nd argument: the name
        generatorAdapter.push(argumentName);

        if (classElement instanceof GenericPlaceholderElement placeholderElement) {
            // Persist resolved placeholder for backward compatibility
            classElement = placeholderElement.getResolved().orElse(classElement);
        }

        // Persist only type annotations added to the type argument
        AnnotationMetadata annotationMetadata = MutableAnnotationMetadata.of(classElement.getTypeAnnotationMetadata());
        boolean hasAnnotationMetadata = !annotationMetadata.isEmpty();

        boolean isRecursiveType = false;
        if (classElement instanceof GenericPlaceholderElement placeholderElement) {
            // Prevent placeholder recursion
            Object genericNativeType = placeholderElement.getGenericNativeType();
            if (visitedTypes.contains(genericNativeType)) {
                isRecursiveType = true;
            } else {
                visitedTypes.add(genericNativeType);
            }
        }

        boolean typeVariable = classElement.isTypeVariable();

        if (isRecursiveType || !typeVariable && !hasAnnotationMetadata && typeArguments.isEmpty()) {
            invokeInterfaceStaticMethod(
                    generatorAdapter,
                    Argument.class,
                    METHOD_CREATE_ARGUMENT_SIMPLE
            );
            return;
        }

        // 3rd argument: annotation metadata
        if (!hasAnnotationMetadata) {
            generatorAdapter.push((String) null);
        } else {
            MutableAnnotationMetadata.contributeDefaults(
                annotationMetadataWithDefaults,
                annotationMetadata
            );

            AnnotationMetadataWriter.instantiateNewMetadata(
                    owningType,
                    owningClassWriter,
                    generatorAdapter,
                    (MutableAnnotationMetadata) annotationMetadata,
                    defaults,
                    loadTypeMethods
            );
        }

        // 4th argument, more generics
        pushTypeArgumentElements(
                annotationMetadataWithDefaults,
                owningType,
                owningClassWriter,
                generatorAdapter,
                classElement.getName(),
                classElement,
                typeArguments,
                visitedTypes,
                defaults,
                loadTypeMethods
        );

        // Argument.create( .. )
        invokeInterfaceStaticMethod(
                generatorAdapter,
                Argument.class,
                typeVariable ? METHOD_CREATE_TYPE_VAR_WITH_ANNOTATION_METADATA_GENERICS : METHOD_CREATE_ARGUMENT_WITH_ANNOTATION_METADATA_GENERICS
        );
    }

    /**
     * Builds generic type arguments recursively.
     *
     * @param generatorAdapter   The generator adapter to use
     * @param type               The type that declares the generics
     * @param annotationMetadata The annotation metadata reference
     * @param generics           The generics
     * @since 3.0.0
     */
    protected static void buildArgumentWithGenerics(
            GeneratorAdapter generatorAdapter,
            Type type,
            AnnotationMetadataReference annotationMetadata,
            ClassElement[] generics) {
        // 1st argument: the type
        generatorAdapter.push(type);
        // 2nd argument: the annotation metadata
        AnnotationMetadataWriter.pushAnnotationMetadataReference(
                generatorAdapter,
                annotationMetadata
        );

        // 3rd argument, the generics
        pushNewArray(generatorAdapter, Class.class, generics, g -> generatorAdapter.push(getTypeReference(g)));

        // Argument.create( .. )
        invokeInterfaceStaticMethod(
                generatorAdapter,
                Argument.class,
                METHOD_CREATE_ARGUMENT_WITH_ANNOTATION_METADATA_CLASS_GENERICS
        );
    }

    /**
     * @param annotationMetadataWithDefaults The annotation metadata with defaults
     * @param declaringElementName           The declaring element name
     * @param owningType                     The owning type
     * @param declaringClassWriter           The declaring class writer
     * @param generatorAdapter               The {@link GeneratorAdapter}
     * @param argumentTypes                  The argument types
     * @param defaults                       The annotation defaults
     * @param loadTypeMethods                The load type methods
     */
    protected static void pushBuildArgumentsForMethod(
            AnnotationMetadata annotationMetadataWithDefaults,
            String declaringElementName,
            Type owningType,
            ClassWriter declaringClassWriter,
            GeneratorAdapter generatorAdapter,
            Collection<ParameterElement> argumentTypes,
            Map<String, Integer> defaults,
            Map<String, GeneratorAdapter> loadTypeMethods) {
        pushNewArray(generatorAdapter, Argument.class, argumentTypes, entry -> {

            ClassElement genericType = entry.getGenericType();

            MutableAnnotationMetadata.contributeDefaults(
                annotationMetadataWithDefaults,
                entry.getAnnotationMetadata()
            );
            MutableAnnotationMetadata.contributeDefaults(
                annotationMetadataWithDefaults,
                genericType.getTypeAnnotationMetadata()
            );

            String argumentName = entry.getName();
            MutableAnnotationMetadata annotationMetadata = new AnnotationMetadataHierarchy(
                entry.getAnnotationMetadata(),
                genericType.getTypeAnnotationMetadata()
            ).merge();

            if (entry instanceof KotlinParameterElement kp && kp.hasDefault()) {
                annotationMetadata.removeAnnotation(AnnotationUtil.NON_NULL);
                annotationMetadata.addAnnotation(AnnotationUtil.NULLABLE, Map.of());
                annotationMetadata.addDeclaredAnnotation(AnnotationUtil.NULLABLE, Map.of());
            }

            Map<String, ClassElement> typeArguments = genericType.getTypeArguments();
            pushCreateArgument(
                annotationMetadataWithDefaults,
                declaringElementName,
                owningType,
                declaringClassWriter,
                generatorAdapter,
                argumentName,
                genericType,
                annotationMetadata,
                typeArguments, defaults, loadTypeMethods
            );
        });
    }

    /**
     * Pushes an argument.
     *
     * @param annotationMetadataWithDefaults The annotation metadata with defaults
     * @param owningType                     The owning type
     * @param classWriter                    The declaring class writer
     * @param generatorAdapter               The generator adapter
     * @param declaringTypeName              The declaring type name
     * @param argument                       The argument
     * @param defaults                       The annotation defaults
     * @param loadTypeMethods                The load type methods
     */
    protected void pushReturnTypeArgument(AnnotationMetadata annotationMetadataWithDefaults,
                                          Type owningType,
                                          ClassWriter classWriter,
                                          GeneratorAdapter generatorAdapter,
                                          String declaringTypeName,
                                          ClassElement argument,
                                          Map<String, Integer> defaults,
                                          Map<String, GeneratorAdapter> loadTypeMethods) {
        // Persist only type annotations added
        AnnotationMetadata annotationMetadata = argument.getTypeAnnotationMetadata();

        Type argumentType = Type.getType(Argument.class);
        if (argument.isVoid()) {
            generatorAdapter.getStatic(argumentType, "VOID", argumentType);
            return;
        }
        if (argument.isPrimitive() && !argument.isArray()) {
            String constantName = argument.getName().toUpperCase(Locale.ENGLISH);
            // refer to constant for primitives
            generatorAdapter.getStatic(argumentType, constantName, argumentType);
            return;
        }

        if (annotationMetadata.isEmpty()
                && !argument.isArray()
                && String.class.getName().equals(argument.getType().getName())
                && argument.getName().equals(argument.getType().getName())
                && argument.getAnnotationMetadata().isEmpty()) {
            generatorAdapter.getStatic(argumentType, "STRING", argumentType);
            return;
        }

        pushCreateArgument(
                annotationMetadataWithDefaults,
                declaringTypeName,
                owningType,
                classWriter,
                generatorAdapter,
                argument.getName(),
                argument,
                annotationMetadata,
                argument.getTypeArguments(),
                defaults,
                loadTypeMethods
        );
    }

    /**
     * Pushes a new Argument creation.
     *
     * @param annotationMetadataWithDefaults The annotation metadata with defaults
     * @param declaringTypeName    The declaring type name
     * @param owningType           The owning type
     * @param classWriter          The class writer
     * @param generatorAdapter     The generator adapter
     * @param argumentName         The argument name
     * @param classElement         The typed element
     * @param defaults             The annotation defaults
     * @param loadTypeMethods      The load type methods
     */
    protected static void pushCreateArgument(
        AnnotationMetadata annotationMetadataWithDefaults,
        String declaringTypeName,
        Type owningType,
        ClassWriter classWriter,
        GeneratorAdapter generatorAdapter,
        String argumentName,
        ClassElement classElement,
        Map<String, Integer> defaults,
        Map<String, GeneratorAdapter> loadTypeMethods) {

        pushCreateArgument(
            annotationMetadataWithDefaults,
            declaringTypeName,
            owningType,
            classWriter,
            generatorAdapter,
            argumentName,
            classElement,
            classElement.getAnnotationMetadata(),
            classElement.getTypeArguments(),
            defaults,
            loadTypeMethods
        );
    }

    /**
     * Pushes a new Argument creation.
     *
     * @param annotationMetadataWithDefaults The annotation metadata with defaults
     * @param declaringTypeName    The declaring type name
     * @param owningType           The owning type
     * @param declaringClassWriter The declaring class writer
     * @param generatorAdapter     The generator adapter
     * @param argumentName         The argument name
     * @param typedElement         The typed element
     * @param annotationMetadata   The annotation metadata
     * @param typeArguments        The type arguments
     * @param defaults             The annotation defaults
     * @param loadTypeMethods      The load type methods
     */
    protected static void pushCreateArgument(
            AnnotationMetadata annotationMetadataWithDefaults,
            String declaringTypeName,
            Type owningType,
            ClassWriter declaringClassWriter,
            GeneratorAdapter generatorAdapter,
            String argumentName,
            TypedElement typedElement,
            AnnotationMetadata annotationMetadata,
            Map<String, ClassElement> typeArguments,
            Map<String, Integer> defaults,
            Map<String, GeneratorAdapter> loadTypeMethods) {
        annotationMetadata = MutableAnnotationMetadata.of(annotationMetadata);

        Type argumentType = JavaModelUtils.getTypeReference(typedElement);

        // 1st argument: The type
        generatorAdapter.push(argumentType);

        // 2nd argument: The argument name
        generatorAdapter.push(argumentName);

        boolean hasAnnotations = !annotationMetadata.isEmpty();
        boolean hasTypeArguments = typeArguments != null && !typeArguments.isEmpty();
        if (typedElement instanceof GenericPlaceholderElement placeholderElement) {
            // Persist resolved placeholder for backward compatibility
            typedElement = placeholderElement.getResolved().orElse(placeholderElement);
        }
        boolean isGenericPlaceholder = typedElement instanceof GenericPlaceholderElement;
        boolean isTypeVariable = isGenericPlaceholder || ((typedElement instanceof ClassElement classElement) && classElement.isTypeVariable());
        String variableName = argumentName;
        if (isGenericPlaceholder) {
            variableName = ((GenericPlaceholderElement) typedElement).getVariableName();
        }
        boolean hasVariableName = !variableName.equals(argumentName);

        if (!hasAnnotations && !hasTypeArguments && !isTypeVariable) {
            invokeInterfaceStaticMethod(
                    generatorAdapter,
                    Argument.class,
                    METHOD_CREATE_ARGUMENT_SIMPLE
            );
            return;
        }

        if (isTypeVariable && hasVariableName) {
            generatorAdapter.push(variableName);
        }

        // 3rd argument: The annotation metadata
        if (hasAnnotations) {
            MutableAnnotationMetadata.contributeDefaults(
                annotationMetadataWithDefaults,
                annotationMetadata
            );

            AnnotationMetadataWriter.instantiateNewMetadata(
                    owningType,
                    declaringClassWriter,
                    generatorAdapter,
                    (MutableAnnotationMetadata) annotationMetadata,
                    defaults,
                    loadTypeMethods
            );
        } else {
            generatorAdapter.push((String) null);
        }

        // 4th argument: The generic types
        if (hasTypeArguments) {
            pushTypeArgumentElements(
                    annotationMetadataWithDefaults,
                    owningType,
                    declaringClassWriter,
                    generatorAdapter,
                    declaringTypeName,
                    typeArguments,
                    defaults,
                    loadTypeMethods
            );
        } else {
            generatorAdapter.visitInsn(ACONST_NULL);
        }

        if (isTypeVariable) {
            // Argument.create( .. )
            invokeInterfaceStaticMethod(
                    generatorAdapter,
                    Argument.class,
                    hasVariableName ? METHOD_CREATE_GENERIC_PLACEHOLDER_WITH_ANNOTATION_METADATA_GENERICS : METHOD_CREATE_TYPE_VAR_WITH_ANNOTATION_METADATA_GENERICS
            );
        } else {

            // Argument.create( .. )
            invokeInterfaceStaticMethod(
                    generatorAdapter,
                    Argument.class,
                    METHOD_CREATE_ARGUMENT_WITH_ANNOTATION_METADATA_GENERICS
            );
        }
    }

    /**
     * Write the class to the target directory.
     *
     * @param targetDir The target directory
     * @throws IOException if there is an error writing the file
     */
    public void writeTo(File targetDir) throws IOException {
        accept(newClassWriterOutputVisitor(targetDir));
    }

    /**
     * @return The originating element
     */
    public @Nullable
    Element getOriginatingElement() {
        Element[] originatingElements = getOriginatingElements();
        if (ArrayUtils.isNotEmpty(originatingElements)) {
            return originatingElements[0];
        } else {
            return null;
        }
    }

    /**
     * Implements a method called "getInterceptedType" for the given type and class writer.
     *
     * @param interceptedType The intercepted type
     * @param classWriter     The class writer
     */
    protected void implementInterceptedTypeMethod(Type interceptedType, ClassWriter classWriter) {
        GeneratorAdapter getTargetTypeMethod = startPublicMethodZeroArgs(
                classWriter,
                Class.class,
                "getInterceptedType"
        );
        getTargetTypeMethod.loadThis();
        getTargetTypeMethod.push(interceptedType);
        getTargetTypeMethod.returnValue();
        getTargetTypeMethod.endMethod();
    }

    /**
     * Returns the descriptor corresponding to the given class.
     *
     * @param type The type
     * @return The descriptor for the class
     */
    protected static String getTypeDescriptor(TypedElement type) {
        return JavaModelUtils.getTypeReference(type).getDescriptor();
    }

    /**
     * Returns the descriptor corresponding to the given class.
     *
     * @param type The type
     * @return The descriptor for the class
     */
    protected static String getTypeDescriptor(Class<?> type) {
        return Type.getDescriptor(type);
    }

    /**
     * Returns the descriptor corresponding to the given class.
     *
     * @param type The type
     * @return The descriptor for the class
     */
    protected static String getTypeDescriptor(String type) {
        return getTypeDescriptor(type, EMPTY_STRING_ARRAY);
    }

    /**
     * Returns the Type reference corresponding to the given class.
     *
     * @param className    The class name
     * @param genericTypes The generic types
     * @return The {@link Type}
     */
    protected static Type getTypeReferenceForName(String className, String... genericTypes) {
        Type type = JavaModelUtils.NAME_TO_REAL_TYPE_MAP.get(className);
        if (type != null) {
            return type;
        }
        String referenceString = getTypeDescriptor(className, genericTypes);
        return Type.getType(referenceString);
    }

    /**
     * Return the type reference for a class.
     *
     * @param type The type
     * @return The {@link Type}
     */
    protected static Type getTypeReference(TypedElement type) {
        return JavaModelUtils.getTypeReference(type);
    }

    /**
     * @param fieldType           The field type
     * @param injectMethodVisitor The {@link GeneratorAdapter}
     */
    protected static void pushBoxPrimitiveIfNecessary(Type fieldType, GeneratorAdapter injectMethodVisitor) {
        if (JavaModelUtils.isPrimitive(fieldType)) {
            injectMethodVisitor.valueOf(fieldType);
        }
    }

    /**
     * @param fieldType           The field type
     * @param injectMethodVisitor The {@link MethodVisitor}
     */
    protected static void pushBoxPrimitiveIfNecessary(Class<?> fieldType, GeneratorAdapter injectMethodVisitor) {
        pushBoxPrimitiveIfNecessary(Type.getType(fieldType), injectMethodVisitor);
    }

    /**
     * @param fieldType           The field type
     * @param injectMethodVisitor The {@link GeneratorAdapter}
     */
    protected static void pushBoxPrimitiveIfNecessary(TypedElement fieldType, GeneratorAdapter injectMethodVisitor) {
        pushBoxPrimitiveIfNecessary(JavaModelUtils.getTypeReference(fieldType), injectMethodVisitor);
    }

    /**
     * @param ga   The {@link GeneratorAdapter}
     * @param type The type
     */
    protected static void pushCastToType(GeneratorAdapter ga, Type type) {
        if (JavaModelUtils.isPrimitive(type)) {
            ga.unbox(type);
        } else {
            ga.checkCast(type);
        }
    }

    /**
     * @param ga   The {@link MethodVisitor}
     * @param type The type
     */
    protected static void pushCastToType(GeneratorAdapter ga, TypedElement type) {
        pushCastToType(ga, JavaModelUtils.getTypeReference(type));
    }

    /**
     * @param ga   The {@link MethodVisitor}
     * @param to The type
     */
    protected static void pushCastFromObjectToType(GeneratorAdapter ga, TypedElement to) {
        Type toType = JavaModelUtils.getTypeReference(to);
        if (JavaModelUtils.isPrimitive(toType)) {
            ga.unbox(toType);
        } else if (!to.getName().equals(Object.class.getName())) {
            ga.checkCast(toType);
        }
    }

    /**
     * Cast from one type to another.
     *
     * @param ga   The {@link MethodVisitor}
     * @param from The from type
     * @param to   The to type
     */
    protected static void pushCastToType(GeneratorAdapter ga, TypedElement from, TypedElement to) {
        Type toType = JavaModelUtils.getTypeReference(to);
        if (from.isPrimitive() && to.isPrimitive()) {
            if (!from.getType().equals(to.getType())) {
                ga.cast(JavaModelUtils.getTypeReference(from), toType);
            }
        } else if (from.isPrimitive()) {
            ga.box(JavaModelUtils.getTypeReference(from));
        } else if (!to.getType().getName().equals(Object.class.getName())) {
            pushCastToType(ga, toType);
        }
    }

    /**
     * @param ga   The {@link MethodVisitor}
     * @param type The type
     */
    protected static void pushCastToType(GeneratorAdapter ga, Class<?> type) {
        pushCastToType(ga, Type.getType(type));
    }

    /**
     * @param methodVisitor The method visitor as {@link GeneratorAdapter}
     * @param methodName    The method name
     * @param argumentTypes The argument types
     */
    protected static void pushMethodNameAndTypesArguments(GeneratorAdapter methodVisitor, String methodName, Collection<ClassElement> argumentTypes) {
        // and the method name
        methodVisitor.push(methodName);
        pushNewArray(methodVisitor, Class.class, argumentTypes, item -> pushType(methodVisitor, item));
    }

    /**
     * @param methodVisitor The method visitor as {@link GeneratorAdapter}
     * @param arrayType     The array class
     * @param collection    The collection
     * @param itemConsumer  The item consumer
     * @param <T> The type
     */
    protected static <T> void pushNewArray(GeneratorAdapter methodVisitor,
                                           Class<?> arrayType,
                                           Collection<T> collection,
                                           Consumer<T> itemConsumer) {
        final Type type = Type.getType(arrayType);
        // the size of the array
        int size = collection.size();
        methodVisitor.push(size);
        // define the array
        methodVisitor.newArray(type);
        // add a reference to the array on the stack
        if (size > 0) {
            methodVisitor.dup();
            int index = 0;
            for (T item : collection) {
                // the array index position
                methodVisitor.push(index);
                // Push value
                itemConsumer.accept(item);
                // store the value in the position
                methodVisitor.arrayStore(type);
                if (index != (size - 1)) {
                    // if we are not at the end of the array duplicate array onto the stack
                    methodVisitor.dup();
                }
                index++;
            }
        }
    }

    /**
     * @param methodVisitor The method visitor as {@link GeneratorAdapter}
     * @param arrayType     The array class
     * @param collection    The collection
     * @param itemConsumer  The item consumer
     * @param <T> The type
     */
    protected static <T> void pushNewArrayIndexed(GeneratorAdapter methodVisitor,
                                                  Class<?> arrayType,
                                                  Collection<T> collection,
                                                  BiConsumer<Integer, T> itemConsumer) {
        final Type type = Type.getType(arrayType);
        // the size of the array
        int size = collection.size();
        methodVisitor.push(size);
        // define the array
        methodVisitor.newArray(type);
        // add a reference to the array on the stack
        if (size > 0) {
            methodVisitor.dup();
            int index = 0;
            for (T item : collection) {
                // the array index position
                methodVisitor.push(index);
                // Push value
                itemConsumer.accept(index, item);
                // store the value in the position
                methodVisitor.arrayStore(type);
                if (index != (size - 1)) {
                    // if we are not at the end of the array duplicate array onto the stack
                    methodVisitor.dup();
                }
                index++;
            }
        }
    }

    /**
     * @param methodVisitor The method visitor as {@link GeneratorAdapter}
     * @param arrayType     The array class
     * @param array    The collection
     * @param itemConsumer  The item consumer
     * @param <T> The type
     */
    protected static <T> void pushNewArray(GeneratorAdapter methodVisitor,
                                           Class<?> arrayType,
                                           T[] array,
                                           Consumer<T> itemConsumer) {
        pushNewArray(methodVisitor, arrayType, Arrays.asList(array), itemConsumer);
    }

    /**
     * @param methodVisitor The method visitor as {@link GeneratorAdapter}
     * @param arrayType     The array class
     * @param size          The size
     */
    protected static void pushNewArray(GeneratorAdapter methodVisitor, Class<?> arrayType, int size) {
        final Type t = Type.getType(arrayType);
        pushNewArray(methodVisitor, t, size);
    }

    /**
     * @param methodVisitor The method visitor as {@link org.objectweb.asm.commons.GeneratorAdapter}
     * @param arrayType     The array class
     * @param size          The size
     */
    protected static void pushNewArray(GeneratorAdapter methodVisitor, Type arrayType, int size) {
        // the size of the array
        methodVisitor.push(size);
        // define the array
        methodVisitor.newArray(arrayType);
        // add a reference to the array on the stack
        if (size > 0) {
            methodVisitor.dup();
        }
    }

    /**
     * @param methodVisitor The method visitor as {@link org.objectweb.asm.commons.GeneratorAdapter}
     * @param arrayType     The array class
     * @param size          The size
     * @param itemConsumer  The item consumer
     */
    protected static void pushNewArray(GeneratorAdapter methodVisitor, Type arrayType, int size, Consumer<Integer> itemConsumer) {
        // the size of the array
        methodVisitor.push(size);
        // define the array
        methodVisitor.newArray(arrayType);
        // add a reference to the array on the stack
        if (size > 0) {
            methodVisitor.dup();
            for (int i = 0; i < size; i++) {
                // the array index position
                methodVisitor.push(i);
                // Push value
                itemConsumer.accept(i);
                // store the value in the position
                methodVisitor.arrayStore(arrayType);
                if (i != (size - 1)) {
                    // if we are not at the end of the array duplicate array onto the stack
                    methodVisitor.dup();
                }
            }
        }
    }

    /**
     * @param methodVisitor The method visitor as {@link GeneratorAdapter}
     * @param index         The index
     * @param size          The size
     * @param runnable      The runnable
     */
    protected static void pushStoreInArray(GeneratorAdapter methodVisitor, int index, int size, Runnable runnable) {
        pushStoreInArray(methodVisitor, TYPE_OBJECT, index, size, runnable);
    }

    /**
     * @param methodVisitor The method visitor as {@link GeneratorAdapter}
     * @param type          The type of the array
     * @param index         The index
     * @param size          The size
     * @param runnable      The runnable
     */
    protected static void pushStoreInArray(GeneratorAdapter methodVisitor, Type type, int index, int size, Runnable runnable) {
        // the array index position
        methodVisitor.push(index);
        // load the constant string
        runnable.run();
        // store the string in the position
        methodVisitor.arrayStore(type);
        if (index != (size - 1)) {
            // if we are not at the end of the array duplicate array onto the stack
            methodVisitor.dup();
        }
    }

    private static void pushType(GeneratorAdapter methodVisitor, ClassElement type) {
        if (type.isPrimitive()) {
            Class<?> typeClass = ClassUtils.getPrimitiveType(type.getName()).orElse(null);
            if (typeClass != null) {
                if (type.isArray()) {
                    Type arrayType = Type.getType(Array.newInstance(typeClass, 0).getClass());
                    methodVisitor.push(arrayType);
                } else {
                    Type wrapperType = Type.getType(ReflectionUtils.getWrapperType(typeClass));
                    methodVisitor.getStatic(wrapperType, "TYPE", Type.getType(Class.class));
                }
            } else {
                methodVisitor.push(JavaModelUtils.getTypeReference(type));
            }
        } else {
            methodVisitor.push(JavaModelUtils.getTypeReference(type));
        }
    }

    /**
     * @param type The type
     * @return The {@link Type} for the object type
     */
    protected static Type getObjectType(Object type) {
        if (type instanceof TypedElement element) {
            String name = element.getType().getName();
            String internalName = getTypeDescriptor(name);
            return Type.getType(internalName);
        } else if (type instanceof Class class1) {
            return Type.getType(class1);
        } else if (type instanceof String) {
            String className = type.toString();

            String internalName = getTypeDescriptor(className);
            return Type.getType(internalName);
        } else {
            throw new IllegalArgumentException("Type reference [" + type + "] should be a Class or a String representing the class name");
        }
    }

    /**
     * @param className    The class name
     * @param genericTypes The generic types
     * @return The type descriptor as String
     */
    protected static String getTypeDescriptor(String className, String... genericTypes) {
        if (JavaModelUtils.NAME_TO_TYPE_MAP.containsKey(className)) {
            return JavaModelUtils.NAME_TO_TYPE_MAP.get(className);
        } else {
            String internalName = getInternalName(className);
            StringBuilder start = new StringBuilder(40);
            Matcher matcher = ARRAY_PATTERN.matcher(className);
            if (matcher.find()) {
                int dimensions = matcher.group(0).length() / 2;
                for (int i = 0; i < dimensions; i++) {
                    start.append('[');
                }
            }
            start.append('L').append(internalName);
            if (genericTypes != null && genericTypes.length > 0) {
                start.append('<');
                for (String genericType : genericTypes) {
                    start.append(getTypeDescriptor(genericType));
                }
                start.append('>');
            }
            return start.append(';').toString();
        }
    }

    /**
     * @param returnType    The return type
     * @param argumentTypes The argument types
     * @return The method descriptor
     */
    protected static String getMethodDescriptor(String returnType, String... argumentTypes) {
        StringBuilder builder = new StringBuilder();
        builder.append('(');

        for (String argumentType : argumentTypes) {
            builder.append(getTypeDescriptor(argumentType));
        }

        builder.append(')');

        builder.append(getTypeDescriptor(returnType));
        return builder.toString();
    }

    /**
     * @param returnType    The return type
     * @param argumentTypes The argument types
     * @return The method descriptor
     */
    protected static String getMethodDescriptor(TypedElement returnType, Collection<? extends TypedElement> argumentTypes) {
        StringBuilder builder = new StringBuilder();
        builder.append('(');

        for (TypedElement argumentType : argumentTypes) {
            builder.append(getTypeDescriptor(argumentType));
        }

        builder.append(')');

        builder.append(getTypeDescriptor(returnType));
        return builder.toString();
    }

    /**
     * @param returnType    The return type
     * @param argumentTypes The argument types
     * @return The method descriptor
     */
    protected static String getMethodDescriptorForReturnType(Type returnType, Collection<? extends TypedElement> argumentTypes) {
        StringBuilder builder = new StringBuilder();
        builder.append('(');

        for (TypedElement argumentType : argumentTypes) {
            builder.append(getTypeDescriptor(argumentType));
        }

        builder.append(')');

        builder.append(returnType.getDescriptor());
        return builder.toString();
    }

    /**
     * @param returnType    The return type
     * @param argumentTypes The argument types
     * @return The method descriptor
     */
    protected static String getMethodDescriptor(Class<?> returnType, Collection<Class<?>> argumentTypes) {
        StringBuilder builder = new StringBuilder();
        builder.append('(');

        for (Class<?> argumentType : argumentTypes) {
            builder.append(Type.getDescriptor(argumentType));
        }

        builder.append(')');

        builder.append(Type.getDescriptor(returnType));
        return builder.toString();
    }

    /**
     * @param returnType    The return type
     * @param argumentTypes The argument types
     * @return The method descriptor
     */
    protected static String getMethodDescriptor(Type returnType, Collection<Type> argumentTypes) {
        StringBuilder builder = new StringBuilder();
        builder.append('(');

        for (Type argumentType : argumentTypes) {
            builder.append(argumentType.getDescriptor());
        }

        builder.append(')');

        builder.append(returnType.getDescriptor());
        return builder.toString();
    }

    /**
     * @param returnTypeReference The return type reference
     * @param argReferenceTypes   The argument reference types
     * @return The method signature
     */
    protected static String getMethodSignature(String returnTypeReference, String... argReferenceTypes) {
        StringBuilder builder = new StringBuilder();
        builder.append('(');

        for (String argumentType : argReferenceTypes) {
            builder.append(argumentType);
        }

        builder.append(')');

        builder.append(returnTypeReference);
        return builder.toString();
    }

    /**
     * @param argumentTypes The argument types
     * @return The constructor descriptor
     */
    protected static String getConstructorDescriptor(Class<?>... argumentTypes) {
        StringBuilder builder = new StringBuilder();
        builder.append('(');

        for (Class<?> argumentType : argumentTypes) {
            builder.append(getTypeDescriptor(argumentType));
        }

        return builder.append(")V").toString();
    }

    /**
     * @param argumentTypes The argument types
     * @return The constructor descriptor
     */
    protected static String getConstructorDescriptor(Type[] argumentTypes) {
        StringBuilder builder = new StringBuilder();
        builder.append('(');

        for (Type argumentType : argumentTypes) {
            builder.append(argumentType.getDescriptor());
        }

        return builder.append(")V").toString();
    }

    /**
     * @param argList The argument list
     * @return The constructor descriptor
     */
    protected static String getConstructorDescriptor(Collection<ParameterElement> argList) {
        StringBuilder builder = new StringBuilder();
        builder.append('(');

        for (ParameterElement argumentType : argList) {
            builder.append(getTypeDescriptor(argumentType));
        }

        return builder.append(")V").toString();
    }

    /**
     * @param out         The output stream
     * @param classWriter The current class writer
     * @throws IOException if there is a problem writing the class to disk
     */
    protected void writeClassToDisk(OutputStream out, ClassWriter classWriter) throws IOException {
        byte[] bytes = classWriter.toByteArray();
        out.write(bytes);
    }

    /**
     * @param classWriter The current class writer
     * @return The {@link GeneratorAdapter} for the constructor
     */
    protected GeneratorAdapter startConstructor(ClassVisitor classWriter) {
        MethodVisitor defaultConstructor = classWriter.visitMethod(ACC_PUBLIC, CONSTRUCTOR_NAME, DESCRIPTOR_DEFAULT_CONSTRUCTOR, null, null);
        return new GeneratorAdapter(defaultConstructor, ACC_PUBLIC, CONSTRUCTOR_NAME, DESCRIPTOR_DEFAULT_CONSTRUCTOR);
    }

    /**
     * @param classWriter   The current class writer
     * @param argumentTypes The argument types
     * @return The {@link GeneratorAdapter} for the constructor
     */
    protected GeneratorAdapter startConstructor(ClassVisitor classWriter, Class<?>... argumentTypes) {
        String descriptor = getConstructorDescriptor(argumentTypes);
        return new GeneratorAdapter(classWriter.visitMethod(ACC_PUBLIC, CONSTRUCTOR_NAME, descriptor, null, null), ACC_PUBLIC, CONSTRUCTOR_NAME, descriptor);
    }

    /**
     * @param classWriter The current class writer
     * @param className   The class name
     * @param superType   The super type
     */
    protected void startClass(ClassVisitor classWriter, String className, Type superType) {
        classWriter.visit(V17, ACC_SYNTHETIC, className, null, superType.getInternalName(), null);
        classWriter.visitAnnotation(TYPE_GENERATED.getDescriptor(), false);
    }

    /**
     * @param classWriter The current class writer
     * @param className   The class name
     * @param superType   The super type
     */
    protected void startPublicClass(ClassVisitor classWriter, String className, Type superType) {
        classWriter.visit(V17, ACC_PUBLIC | ACC_SYNTHETIC, className, null, superType.getInternalName(), null);
        classWriter.visitAnnotation(TYPE_GENERATED.getDescriptor(), false);
    }

    /**
     * @param classWriter       The current class writer
     * @param serviceType       The service type
     * @param internalClassName The class name
     * @param superType         The super type
     */
    protected void startService(ClassVisitor classWriter, Class<?> serviceType, String internalClassName, Type superType) {
        startService(classWriter, serviceType.getName(), internalClassName, superType);
    }

    /**
     * @param classWriter       The current class writer
     * @param serviceName       The service name
     * @param internalClassName The class name
     * @param superType         The super type
     * @param interfaces        The interfaces
     */
    protected void startService(ClassVisitor classWriter, String serviceName, String internalClassName, Type superType, String... interfaces) {
        classWriter.visit(V17, ACC_PUBLIC | ACC_FINAL | ACC_SYNTHETIC, internalClassName, null, superType.getInternalName(), interfaces);
        annotateAsGeneratedAndService(classWriter, serviceName);
    }

    protected final void annotateAsGeneratedAndService(ClassVisitor classWriter, String serviceName) {
        AnnotationVisitor annotationVisitor = classWriter.visitAnnotation(TYPE_GENERATED.getDescriptor(), false);
        annotationVisitor.visit("service", serviceName);
        annotationVisitor.visitEnd();
    }

    /**
     * @param cv            The constructor visitor
     * @param superClass    The super class
     * @param argumentTypes The argument types
     */
    protected void invokeConstructor(GeneratorAdapter cv, Class<?> superClass, Class<?>... argumentTypes) {
        cv.invokeConstructor(Type.getType(superClass), new Method(CONSTRUCTOR_NAME, getConstructorDescriptor(argumentTypes)));
    }

    /**
     * @param visitor    The interface visitor
     * @param targetType The target type
     * @param method     The method
     */
    protected static void invokeInterfaceStaticMethod(MethodVisitor visitor, Class<?> targetType, Method method) {
        Type type = Type.getType(targetType);
        String owner = type.getSort() == Type.ARRAY ? type.getDescriptor()
                : type.getInternalName();
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, owner, method.getName(),
                method.getDescriptor(), true);
    }

    /**
     * @param classWriter The current class writer
     * @param returnType  The return type
     * @param methodName  The method name
     * @return TheThe {@link GeneratorAdapter} for the method
     */
    protected GeneratorAdapter startPublicMethodZeroArgs(ClassWriter classWriter, Class<?> returnType, String methodName) {
        Type methodType = Type.getMethodType(Type.getType(returnType));

        return new GeneratorAdapter(classWriter.visitMethod(ACC_PUBLIC, methodName, methodType.getDescriptor(), null, null), ACC_PUBLIC, methodName, methodType.getDescriptor());
    }

    /**
     * @param classWriter The current class writer
     * @param returnType  The return type
     * @param methodName  The method name
     * @return TheThe {@link GeneratorAdapter} for the method
     */
    protected GeneratorAdapter startPublicFinalMethodZeroArgs(ClassWriter classWriter, Class<?> returnType, String methodName) {
        Type methodType = Type.getMethodType(Type.getType(returnType));

        return new GeneratorAdapter(
                classWriter.visitMethod(
                        ACC_PUBLIC | ACC_FINAL,
                        methodName,
                        methodType.getDescriptor(),
                        null,
                        null
                ), ACC_PUBLIC, methodName, methodType.getDescriptor());
    }

    /**
     * @param className The class name
     * @return The internal name
     */
    protected static String getInternalName(String className) {
        String newClassName = className.replace('.', '/');
        Matcher matcher = ARRAY_PATTERN.matcher(newClassName);
        if (matcher.find()) {
            newClassName = matcher.replaceFirst("");
        }
        return newClassName;
    }

    /**
     * @param compilationDir The compilation directory
     * @return The directory class writer output visitor
     */
    protected ClassWriterOutputVisitor newClassWriterOutputVisitor(File compilationDir) {
        return new DirectoryClassWriterOutputVisitor(compilationDir);
    }

    /**
     * @param classWriter The current class writer
     * @return The {@link GeneratorAdapter}
     */
    protected GeneratorAdapter visitStaticInitializer(ClassVisitor classWriter) {
        MethodVisitor mv = classWriter.visitMethod(ACC_STATIC, "<clinit>", DESCRIPTOR_DEFAULT_CONSTRUCTOR, null, null);
        return new GeneratorAdapter(mv, ACC_STATIC, "<clinit>", DESCRIPTOR_DEFAULT_CONSTRUCTOR);
    }

    /**
     * @param writer        The class writer
     * @param methodName    The method name
     * @param returnType    The return type
     * @param argumentTypes The argument types
     * @return The {@link GeneratorAdapter}
     */
    protected GeneratorAdapter startPublicMethod(ClassWriter writer, String methodName, String returnType, String... argumentTypes) {
        return new GeneratorAdapter(writer.visitMethod(
                ACC_PUBLIC,
                methodName,
                getMethodDescriptor(returnType, argumentTypes),
                null,
                null
        ), ACC_PUBLIC,
                methodName,
                getMethodDescriptor(returnType, argumentTypes));
    }

    /**
     * @param writer        The class writer
     * @param methodName    The method name
     * @param returnType    The return type
     * @param argumentTypes The argument types
     * @return The {@link GeneratorAdapter}
     */
    protected GeneratorAdapter startPublicMethod(ClassWriter writer, String methodName, Class<?> returnType, Class<?>... argumentTypes) {
        return new GeneratorAdapter(writer.visitMethod(
                ACC_PUBLIC,
                methodName,
                getMethodDescriptor(returnType, List.of(argumentTypes)),
                null,
                null
        ), ACC_PUBLIC,
                methodName,
                getMethodDescriptor(returnType, List.of(argumentTypes)));
    }

    /**
     * @param writer    The class writer
     * @param asmMethod The asm method
     * @return The {@link GeneratorAdapter}
     * @since 2.3.0
     */
    protected GeneratorAdapter startPublicMethod(ClassWriter writer, Method asmMethod) {
        String methodName = asmMethod.getName();
        return new GeneratorAdapter(writer.visitMethod(
                ACC_PUBLIC,
                methodName,
                asmMethod.getDescriptor(),
                null,
                null
        ), ACC_PUBLIC,
                methodName,
                asmMethod.getDescriptor());
    }

    /**
     * @param writer        The class writer
     * @param methodName    The method name
     * @param returnType    The return type
     * @param argumentTypes The argument types
     * @return The {@link GeneratorAdapter}
     */
    protected GeneratorAdapter startProtectedMethod(ClassWriter writer, String methodName, String returnType, String... argumentTypes) {
        return new GeneratorAdapter(writer.visitMethod(
                ACC_PROTECTED,
                methodName,
                getMethodDescriptor(returnType, argumentTypes),
                null,
                null
        ), ACC_PROTECTED,
                methodName,
                getMethodDescriptor(returnType, argumentTypes));
    }

    /**
     * Push the instantiation of the given type.
     *
     * @param generatorAdapter  The generator adaptor
     * @param typeToInstantiate The type to instantiate.
     */
    protected void pushNewInstance(GeneratorAdapter generatorAdapter, Type typeToInstantiate) {
        generatorAdapter.newInstance(typeToInstantiate);
        generatorAdapter.dup();
        generatorAdapter.invokeConstructor(typeToInstantiate, METHOD_DEFAULT_CONSTRUCTOR);
    }

    public static <T> void pushStringMapOf(GeneratorAdapter generatorAdapter,
                                           Map<? extends CharSequence, T> annotationData,
                                           boolean skipEmpty,
                                           T empty,
                                           Consumer<T> pushValue) {
        Set<? extends Map.Entry<String, T>> entrySet = annotationData != null ? annotationData.entrySet()
                .stream()
                .filter(e -> !skipEmpty || (e.getKey() != null && isSupportedMapValue(e.getValue())))
                .map(e -> e.getValue() == null && empty != null ? new AbstractMap.SimpleEntry<>(e.getKey().toString(), empty) : new AbstractMap.SimpleEntry<>(e.getKey().toString(), e.getValue()))
                .collect(Collectors.toCollection(() -> new TreeSet<>(Map.Entry.comparingByKey()))) : null;
        if (entrySet == null || entrySet.isEmpty()) {
            invokeInterfaceStatic(generatorAdapter, MAP_TYPE, MAP_OF[0]);
            return;
        }
        if (entrySet.size() < MAP_OF.length) {
            for (Map.Entry<String, T> entry : entrySet) {
                generatorAdapter.push(entry.getKey());
                pushValue.accept(entry.getValue());
            }
            invokeInterfaceStatic(generatorAdapter, MAP_TYPE, MAP_OF[entrySet.size()]);
        } else {
            // start a new array
            pushNewArray(generatorAdapter, Map.Entry.class, entrySet, entry -> {
                generatorAdapter.push(entry.getKey());
                pushValue.accept(entry.getValue());
                invokeInterfaceStatic(generatorAdapter, MAP_TYPE, MAP_ENTRY);
            });
            invokeInterfaceStatic(generatorAdapter, MAP_TYPE, MAP_BY_ARRAY);
        }
    }

    public static void pushListOfString(GeneratorAdapter methodVisitor, List<String> names) {
        if (names != null) {
            names = names.stream().filter(Objects::nonNull).toList();
        }
        if (names == null || names.isEmpty()) {
            invokeInterfaceStatic(methodVisitor, LIST_TYPE, LIST_OF[0]);
            return;
        }
        if (names.size() < LIST_OF.length) {
            for (String name : names) {
                methodVisitor.push(name);
            }
            invokeInterfaceStatic(methodVisitor, LIST_TYPE, LIST_OF[names.size()]);
        } else {
            pushNewArray(methodVisitor, String.class, names, methodVisitor::push);
            invokeInterfaceStatic(methodVisitor, LIST_TYPE, LIST_BY_ARRAY);
        }
    }

    private static void invokeInterfaceStatic(GeneratorAdapter methodVisitor, Type type, org.objectweb.asm.commons.Method method) {
        methodVisitor.visitMethodInsn(INVOKESTATIC, type.getInternalName(), method.getName(), method.getDescriptor(), true);
    }

    /**
     * @param p The class element
     * @return The string representation
     */
    protected static String toTypeString(ClassElement p) {
        String name = p.getName();
        if (p.isArray()) {
            return name + IntStream.range(0, p.getArrayDimensions()).mapToObj(ignore -> "[]").collect(Collectors.joining());
        }
        return name;
    }

}
