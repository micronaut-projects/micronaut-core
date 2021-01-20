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

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Generated;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.annotation.AnnotationMetadataWriter;
import io.micronaut.inject.annotation.DefaultAnnotationMetadata;
import io.micronaut.inject.ast.*;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Abstract class that writes generated classes to disk and provides convenience methods for building classes.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public abstract class AbstractClassFileWriter implements Opcodes, OriginatingElements {

    protected static final Type TYPE_ARGUMENT = Type.getType(Argument.class);
    protected static final Type TYPE_ARGUMENT_ARRAY = Type.getType(Argument[].class);
    protected static final String ZERO_ARGUMENTS_CONSTANT = "ZERO_ARGUMENTS";
    protected static final String CONSTRUCTOR_NAME = "<init>";
    protected static final String DESCRIPTOR_DEFAULT_CONSTRUCTOR = "()V";
    protected static final Method METHOD_DEFAULT_CONSTRUCTOR = new Method(CONSTRUCTOR_NAME, DESCRIPTOR_DEFAULT_CONSTRUCTOR);
    protected static final Type TYPE_OBJECT = Type.getType(Object.class);
    protected static final Type TYPE_CLASS = Type.getType(Class.class);
    protected static final int DEFAULT_MAX_STACK = 13;
    protected static final Type TYPE_GENERATED = Type.getType(Generated.class);
    protected static final Pattern ARRAY_PATTERN = Pattern.compile("(\\[\\])+$");

    protected static final Map<String, String> NAME_TO_TYPE_MAP = new HashMap<>();
    private static final Method METHOD_CREATE_ARGUMENT_SIMPLE = Method.getMethod(
            ReflectionUtils.getRequiredInternalMethod(
                    Argument.class,
                    "of",
                    Class.class,
                    String.class
            )
    );
    private static final Method METHOD_CREATE_ARGUMENT_WITH_GENERICS = Method.getMethod(
            ReflectionUtils.getRequiredInternalMethod(
                    Argument.class,
                    "of",
                    Class.class,
                    String.class,
                    Argument[].class
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

    static {
        NAME_TO_TYPE_MAP.put("void", "V");
        NAME_TO_TYPE_MAP.put("boolean", "Z");
        NAME_TO_TYPE_MAP.put("char", "C");
        NAME_TO_TYPE_MAP.put("int", "I");
        NAME_TO_TYPE_MAP.put("byte", "B");
        NAME_TO_TYPE_MAP.put("long", "J");
        NAME_TO_TYPE_MAP.put("double", "D");
        NAME_TO_TYPE_MAP.put("float", "F");
        NAME_TO_TYPE_MAP.put("short", "S");
    }

    private final OriginatingElements originatingElements;

    /**
     * @param originatingElement The originating element
     * @deprecated Use {@link #AbstractClassFileWriter(Element...)} instead
     */
    @Deprecated
    protected AbstractClassFileWriter(Element originatingElement) {
        this(OriginatingElements.of(originatingElement));
    }

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

    @NotNull
    @Override
    public Element[] getOriginatingElements() {
        return originatingElements.getOriginatingElements();
    }

    @Override
    public void addOriginatingElement(@NotNull Element element) {
        originatingElements.addOriginatingElement(element);
    }

    /**
     * Pushes type arguments onto the stack.
     *
     * @param owningType           The owning type
     * @param owningTypeWriter The declaring class writer
     * @param generatorAdapter     The generator adapter
     * @param declaringElementName The declaring class element of the generics
     * @param types                The type references
     * @param loadTypeMethods      The load type methods
     */
    protected static void pushTypeArgumentElements(
            Type owningType,
            ClassWriter owningTypeWriter,
            GeneratorAdapter generatorAdapter,
            String declaringElementName,
            Map<String, ClassElement> types,
            Map<String, GeneratorAdapter> loadTypeMethods) {
        if (types == null || types.isEmpty()) {
            generatorAdapter.visitInsn(ACONST_NULL);
            return;
        }
        Set<String> visitedTypes = new HashSet<>(5);
        pushTypeArgumentElements(owningType, owningTypeWriter, generatorAdapter, declaringElementName, types, visitedTypes, loadTypeMethods);
    }

    private static void pushTypeArgumentElements(
            Type owningType,
            ClassWriter declaringClassWriter,
            GeneratorAdapter generatorAdapter,
            String declaringElementName,
            Map<String, ClassElement> types,
            Set<String> visitedTypes,
            Map<String, GeneratorAdapter> loadTypeMethods) {
        if (visitedTypes.contains(declaringElementName)) {
            generatorAdapter.getStatic(
                    TYPE_ARGUMENT,
                    ZERO_ARGUMENTS_CONSTANT,
                    TYPE_ARGUMENT_ARRAY
            );
        } else {
            visitedTypes.add(declaringElementName);

            int len = types.size();
            // Build calls to Argument.create(...)
            pushNewArray(generatorAdapter, Argument.class, len);
            int i = 0;
            for (Map.Entry<String, ClassElement> entry : types.entrySet()) {
                // the array index
                generatorAdapter.push(i);
                String argumentName = entry.getKey();
                ClassElement classElement = entry.getValue();
                Type classReference = getTypeReference(classElement);
                Map<String, ClassElement> typeArguments = classElement.getTypeArguments();
                if (CollectionUtils.isNotEmpty(typeArguments) || classElement.getAnnotationMetadata() != AnnotationMetadata.EMPTY_METADATA) {
                    buildArgumentWithGenerics(
                            owningType,
                            declaringClassWriter,
                            generatorAdapter,
                            argumentName,
                            classReference,
                            classElement,
                            typeArguments,
                            visitedTypes,
                            loadTypeMethods
                    );
                } else {
                    buildArgument(generatorAdapter, argumentName, classReference);
                }

                // store the type reference
                generatorAdapter.visitInsn(AASTORE);
                // if we are not at the end of the array duplicate array onto the stack
                if (i != (len - 1)) {
                    generatorAdapter.visitInsn(DUP);
                }
                i++;
            }
        }
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
     * Builds generic type arguments recursively.
     *
     * @param owningType           The owning type
     * @param owningClassWriter The declaring writer
     * @param generatorAdapter     The generator adapter to use
     * @param argumentName         The argument name
     * @param typeReference        The type name
     * @param classElement         The class element that declares the generics
     * @param typeArguments        The nested type arguments
     * @param visitedTypes         The visited types
     * @param loadTypeMethods      The load type methods
     */
    protected static void buildArgumentWithGenerics(
            Type owningType,
            ClassWriter owningClassWriter,
            GeneratorAdapter generatorAdapter,
            String argumentName,
            Type typeReference,
            ClassElement classElement,
            Map<String, ClassElement> typeArguments,
            Set<String> visitedTypes,
            Map<String, GeneratorAdapter> loadTypeMethods) {
        // 1st argument: the type
        generatorAdapter.push(typeReference);
        // 2nd argument: the name
        generatorAdapter.push(argumentName);
        // 3rd argument: annotation metadata
        AnnotationMetadata annotationMetadata = classElement.getAnnotationMetadata();
        if (annotationMetadata == AnnotationMetadata.EMPTY_METADATA) {
            generatorAdapter.visitInsn(ACONST_NULL);
        } else {
            AnnotationMetadataWriter.instantiateNewMetadata(
                    owningType,
                    owningClassWriter,
                    generatorAdapter,
                    (DefaultAnnotationMetadata) annotationMetadata,
                    loadTypeMethods
            );
        }

        // 4th argument, more generics
        pushTypeArgumentElements(
                owningType,
                owningClassWriter,
                generatorAdapter,
                classElement.getName(),
                typeArguments,
                visitedTypes,
                loadTypeMethods
        );

        // Argument.create( .. )
        invokeInterfaceStaticMethod(
                generatorAdapter,
                Argument.class,
                METHOD_CREATE_ARGUMENT_WITH_ANNOTATION_METADATA_GENERICS
        );
    }

    /**
     * @param declaringElementName       The declaring element name
     * @param owningType                 The owning type
     * @param declaringClassWriter       The declaring class writer
     * @param generatorAdapter           The {@link GeneratorAdapter}
     * @param argumentTypes              The argument types
     * @param loadTypeMethods            The load type methods
     */
    protected static void pushBuildArgumentsForMethod(
            String declaringElementName,
            Type owningType,
            ClassWriter declaringClassWriter,
            GeneratorAdapter generatorAdapter,
            Collection<ParameterElement> argumentTypes,
            Map<String, GeneratorAdapter> loadTypeMethods) {
        int len = argumentTypes.size();
        pushNewArray(generatorAdapter, Argument.class, len);
        int i = 0;
        for (ParameterElement entry : argumentTypes) {
            // the array index position
            generatorAdapter.push(i);

            ClassElement classElement = entry.getGenericType();
            String argumentName = entry.getName();
            AnnotationMetadata annotationMetadata = entry.getAnnotationMetadata();
            Map<String, ClassElement> typeArguments = classElement.getTypeArguments();
            pushCreateArgument(
                    declaringElementName,
                    owningType,
                    declaringClassWriter,
                    generatorAdapter,
                    argumentName,
                    classElement,
                    annotationMetadata,
                    typeArguments, loadTypeMethods
            );
            // store the type reference
            generatorAdapter.visitInsn(AASTORE);
            // if we are not at the end of the array duplicate array onto the stack
            if (i != (len - 1)) {
                generatorAdapter.visitInsn(DUP);
            }
            i++;
        }
    }

    /**
     * Pushes a new Argument creation.
     * @param declaringTypeName The declaring type name
     * @param owningType The owning type
     * @param declaringClassWriter The declaring class writer
     * @param generatorAdapter The generator adapter
     * @param argumentName The argument name
     * @param classElement The class name
     * @param annotationMetadata The annotation metadata
     * @param typeArguments The type arguments
     * @param loadTypeMethods The load type methods
     */
    protected static void pushCreateArgument(
            String declaringTypeName,
            Type owningType,
            ClassWriter declaringClassWriter,
            GeneratorAdapter generatorAdapter,
            String argumentName,
            ClassElement classElement,
            AnnotationMetadata annotationMetadata,
            Map<String, ClassElement> typeArguments,
            Map<String, GeneratorAdapter> loadTypeMethods) {
        Type argumentType = getTypeReference(classElement);

        // 1st argument: The type
        generatorAdapter.push(argumentType);

        // 2nd argument: The argument name
        generatorAdapter.push(argumentName);

        // 3rd argument: The annotation metadata
        if (annotationMetadata instanceof DefaultAnnotationMetadata) {
            AnnotationMetadataWriter.instantiateNewMetadata(
                    owningType,
                    declaringClassWriter,
                    generatorAdapter,
                    (DefaultAnnotationMetadata) annotationMetadata,
                    loadTypeMethods
            );
        } else {
            generatorAdapter.visitInsn(ACONST_NULL);
        }

        // 4th argument: The generic types
        if (typeArguments != null) {
            pushTypeArgumentElements(
                    owningType,
                    declaringClassWriter,
                    generatorAdapter,
                    declaringTypeName,
                    typeArguments,
                    loadTypeMethods
            );
        } else {
            generatorAdapter.visitInsn(ACONST_NULL);
        }

        // Argument.create( .. )
        invokeInterfaceStaticMethod(
                generatorAdapter,
                Argument.class,
                METHOD_CREATE_ARGUMENT_WITH_ANNOTATION_METADATA_GENERICS
        );
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
     * Writes a method that returns a boolean value with the value supplied by the given supplier.
     *
     * @param classWriter   The class writer
     * @param methodName    The method name
     * @param valueSupplier The supplier
     */
    protected void writeBooleanMethod(ClassWriter classWriter, String methodName, Supplier<Boolean> valueSupplier) {
        GeneratorAdapter isSingletonMethod = startPublicMethodZeroArgs(
                classWriter,
                boolean.class,
                methodName
        );
        isSingletonMethod.loadThis();
        isSingletonMethod.push(valueSupplier.get());
        isSingletonMethod.returnValue();
        isSingletonMethod.visitMaxs(1, 1);
        isSingletonMethod.visitEnd();
    }

    /**
     * @return The originating element
     */
    @Deprecated
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
     * Accept a ClassWriterOutputVisitor to write this writer to disk.
     *
     * @param classWriterOutputVisitor The {@link ClassWriterOutputVisitor}
     * @throws IOException if there is an error writing to disk
     */
    public abstract void accept(ClassWriterOutputVisitor classWriterOutputVisitor) throws IOException;

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
        getTargetTypeMethod.visitMaxs(1, 1);
        getTargetTypeMethod.visitEnd();
    }

    /**
     * Returns the descriptor corresponding to the given class.
     *
     * @param type The type
     * @return The descriptor for the class
     */
    protected static String getTypeDescriptor(TypedElement type) {
        return getTypeReference(type).getDescriptor();
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
        return getTypeDescriptor(type, new String[0]);
    }

    /**
     * Returns the Type reference corresponding to the given class.
     *
     * @param className    The class name
     * @param genericTypes The generic types
     * @return The {@link Type}
     */
    protected static Type getTypeReferenceForName(String className, String... genericTypes) {
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
        ClassElement classElement = type.getType();
        if (type.isPrimitive()) {
            String internalName = NAME_TO_TYPE_MAP.get(classElement.getName());
            if (type.isArray()) {
                StringBuilder name = new StringBuilder(internalName);
                for (int i = 0; i < type.getArrayDimensions(); i++) {
                    name.insert(0, "[");
                }
                return Type.getObjectType(name.toString());
            } else {
                return Type.getType(internalName);
            }
        } else {
            Object nativeType = type.getNativeType();
            if (nativeType instanceof Class) {
                Class<?> t = (Class<?>) nativeType;
                return Type.getType(t);
            } else {
                String internalName = type.getType().getName().replace('.', '/');
                if (type.isArray()) {
                    StringBuilder name = new StringBuilder(internalName);
                    name.insert(0, "L");
                    for (int i = 0; i < type.getArrayDimensions(); i++) {
                        name.insert(0, "[");
                    }
                    name.append(";");
                    return Type.getObjectType(name.toString());
                } else {
                    return Type.getObjectType(internalName);
                }
            }
        }
    }

    /**
     * @param fieldType           The field type
     * @param injectMethodVisitor The {@link MethodVisitor}
     */
    protected static void pushBoxPrimitiveIfNecessary(Type fieldType, MethodVisitor injectMethodVisitor) {
        final Optional<Class> pt = ClassUtils.getPrimitiveType(fieldType.getClassName());
        Class<?> wrapperType = pt.map(ReflectionUtils::getWrapperType).orElse(null);
        if (wrapperType != null && wrapperType != Void.class) {
            Type wrapper = Type.getType(wrapperType);
            String primitiveName = fieldType.getClassName();
            String sig = wrapperType.getName() + " valueOf(" + primitiveName + ")";
            org.objectweb.asm.commons.Method valueOfMethod = org.objectweb.asm.commons.Method.getMethod(sig);
            injectMethodVisitor.visitMethodInsn(INVOKESTATIC, wrapper.getInternalName(), "valueOf", valueOfMethod.getDescriptor(), false);
        }
    }

    /**
     * @param fieldType           The field type
     * @param injectMethodVisitor The {@link MethodVisitor}
     */
    protected static void pushBoxPrimitiveIfNecessary(Class<?> fieldType, MethodVisitor injectMethodVisitor) {
        Class<?> wrapperType = ReflectionUtils.getWrapperType(fieldType);
        if (wrapperType != null && wrapperType != Void.class) {
            Type wrapper = Type.getType(wrapperType);
            String primitiveName = fieldType.getName();
            String sig = wrapperType.getName() + " valueOf(" + primitiveName + ")";
            org.objectweb.asm.commons.Method valueOfMethod = org.objectweb.asm.commons.Method.getMethod(sig);
            injectMethodVisitor.visitMethodInsn(INVOKESTATIC, wrapper.getInternalName(), "valueOf", valueOfMethod.getDescriptor(), false);
        }
    }

    /**
     * @param fieldType           The field type
     * @param injectMethodVisitor The {@link MethodVisitor}
     */
    protected static void pushBoxPrimitiveIfNecessary(TypedElement fieldType, MethodVisitor injectMethodVisitor) {
        ClassElement type = fieldType.getType();
        if (type.isPrimitive() && !type.isArray()) {
            String primitiveName = type.getName();
            final Optional<Class> pt = ClassUtils.getPrimitiveType(primitiveName);
            Class<?> wrapperType = pt.map(ReflectionUtils::getWrapperType).orElse(null);
            if (wrapperType != null && wrapperType != Void.class) {
                Type wrapper = Type.getType(wrapperType);
                String sig = wrapperType.getName() + " valueOf(" + primitiveName + ")";
                org.objectweb.asm.commons.Method valueOfMethod = org.objectweb.asm.commons.Method.getMethod(sig);
                injectMethodVisitor.visitMethodInsn(INVOKESTATIC, wrapper.getInternalName(), "valueOf", valueOfMethod.getDescriptor(), false);
            }
        }
    }

    /**
     * @param methodVisitor The {@link MethodVisitor}
     * @param type          The type
     */
    protected static void pushCastToType(MethodVisitor methodVisitor, Type type) {
        String internalName = getInternalNameForCast(type);
        methodVisitor.visitTypeInsn(CHECKCAST, internalName);
        Type primitiveType = null;
        final Optional<Class> pt = ClassUtils.getPrimitiveType(type.getClassName());
        if (pt.isPresent()) {
            primitiveType = Type.getType(pt.get());
        }

        pushPrimitiveCastIfRequired(methodVisitor, internalName, primitiveType);
    }

    private static void pushPrimitiveCastIfRequired(MethodVisitor methodVisitor, String internalName, Type primitiveType) {
        if (primitiveType != null) {
            Method valueMethod = null;
            switch (primitiveType.getSort()) {
                case Type.BOOLEAN:
                    valueMethod = Method.getMethod("boolean booleanValue()");
                    break;
                case Type.CHAR:
                    valueMethod = Method.getMethod("char charValue()");
                    break;
                case Type.BYTE:
                    valueMethod = Method.getMethod("byte byteValue()");
                    break;
                case Type.SHORT:
                    valueMethod = Method.getMethod("short shortValue()");
                    break;
                case Type.INT:
                    valueMethod = Method.getMethod("int intValue()");
                    break;
                case Type.LONG:
                    valueMethod = Method.getMethod("long longValue()");
                    break;
                case Type.DOUBLE:
                    valueMethod = Method.getMethod("double doubleValue()");
                    break;
                case Type.FLOAT:
                    valueMethod = Method.getMethod("float floatValue()");
                    break;
                default:
                    // no-ip
            }

            if (valueMethod != null) {
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, internalName, valueMethod.getName(), valueMethod.getDescriptor(), false);
            }
        }
    }

    /**
     * @param methodVisitor The {@link MethodVisitor}
     * @param type          The type
     */
    protected static void pushCastToType(MethodVisitor methodVisitor, TypedElement type) {
        String internalName = getInternalNameForCast(type);
        methodVisitor.visitTypeInsn(CHECKCAST, internalName);
        Type primitiveType = null;
        if (type.isPrimitive() && !type.isArray()) {
            final Optional<Class> pt = ClassUtils.getPrimitiveType(type.getType().getName());
            if (pt.isPresent()) {
                primitiveType = Type.getType(pt.get());
            }
        }

        pushPrimitiveCastIfRequired(methodVisitor, internalName, primitiveType);
    }

    /**
     * @param methodVisitor The {@link MethodVisitor}
     * @param type          The type
     */
    protected static void pushCastToType(MethodVisitor methodVisitor, Class<?> type) {
        String internalName = getInternalNameForCast(type);
        methodVisitor.visitTypeInsn(CHECKCAST, internalName);
        Type primitiveType = null;
        if (type.isPrimitive()) {
            primitiveType = Type.getType(type);
        }

        pushPrimitiveCastIfRequired(methodVisitor, internalName, primitiveType);
    }

    /**
     * @param methodVisitor The {@link MethodVisitor}
     * @param type          The type
     */
    protected static void pushReturnValue(MethodVisitor methodVisitor, TypedElement type) {
        Class<?> primitiveTypeClass = null;

        if (type.isPrimitive() && !type.isArray()) {
            primitiveTypeClass = ClassUtils.getPrimitiveType(type.getType().getName()).orElse(null);
        }

        if (primitiveTypeClass == null) {
            methodVisitor.visitInsn(ARETURN);
        } else {

            Type primitiveType = Type.getType(primitiveTypeClass);
            switch (primitiveType.getSort()) {
                case Type.BOOLEAN:
                case Type.INT:
                case Type.CHAR:
                case Type.BYTE:
                case Type.SHORT:
                    methodVisitor.visitInsn(IRETURN);
                    break;
                case Type.VOID:
                    methodVisitor.visitInsn(RETURN);
                    break;
                case Type.LONG:
                    methodVisitor.visitInsn(LRETURN);
                    break;
                case Type.DOUBLE:
                    methodVisitor.visitInsn(DRETURN);
                    break;
                case Type.FLOAT:
                    methodVisitor.visitInsn(FRETURN);
                    break;
                default:
                    //no-op
            }
        }
    }

    /**
     * @param methodVisitor The method visitor as {@link GeneratorAdapter}
     * @param methodName    The method name
     * @param argumentTypes The argument types
     */
    protected static void pushMethodNameAndTypesArguments(GeneratorAdapter methodVisitor, String methodName, Collection<ClassElement> argumentTypes) {
        // and the method name
        methodVisitor.visitLdcInsn(methodName);

        int argTypeCount = argumentTypes.size();
        if (!argumentTypes.isEmpty()) {
            pushNewArray(methodVisitor, Class.class, argTypeCount);
            Iterator<ClassElement> argIterator = argumentTypes.iterator();
            for (int i = 0; i < argTypeCount; i++) {
                pushStoreTypeInArray(methodVisitor, i, argTypeCount, argIterator.next());
            }
        } else {
            // no arguments
            pushNewArray(methodVisitor, Class.class, 0);
        }
    }

    /**
     * @param methodVisitor The method visitor as {@link GeneratorAdapter}
     * @param arrayType     The array class
     * @param size          The size
     */
    protected static void pushNewArray(GeneratorAdapter methodVisitor, Class<?> arrayType, int size) {
        // the size of the array
        methodVisitor.push(size);
        // define the array
        methodVisitor.visitTypeInsn(ANEWARRAY, Type.getInternalName(arrayType));
        // add a reference to the array on the stack
        if (size > 0) {
            methodVisitor.visitInsn(DUP);
        }
    }

    /**
     * @param methodVisitor The method visitor as {@link GeneratorAdapter}
     * @param index         The index
     * @param size          The size
     * @param string        The string
     */
    protected static void pushStoreStringInArray(GeneratorAdapter methodVisitor, int index, int size, String string) {
        // the array index position
        methodVisitor.push(index);
        // load the constant string
        methodVisitor.push(string);
        // store the string in the position
        methodVisitor.visitInsn(AASTORE);
        if (index != (size - 1)) {
            // if we are not at the end of the array duplicate array onto the stack
            methodVisitor.dup();
        }
    }

    /**
     * @param methodVisitor The method visitor as {@link GeneratorAdapter}
     * @param index         The index
     * @param size          The size
     * @param runnable      The runnable
     */
    protected static void pushStoreInArray(GeneratorAdapter methodVisitor, int index, int size, Runnable runnable) {
        // the array index position
        methodVisitor.push(index);
        // load the constant string
        runnable.run();
        // store the string in the position
        methodVisitor.visitInsn(AASTORE);
        if (index != (size - 1)) {
            // if we are not at the end of the array duplicate array onto the stack
            methodVisitor.dup();
        }
    }

    /**
     * @param methodVisitor The method visitor as {@link GeneratorAdapter}
     * @param index         The index
     * @param size          The size
     * @param type          The type
     */
    protected static void pushStoreTypeInArray(GeneratorAdapter methodVisitor, int index, int size, ClassElement type) {
        // the array index position
        methodVisitor.push(index);
        // the type reference
        if (type.isPrimitive()) {
            Class<?> typeClass = ClassUtils.getPrimitiveType(type.getName()).orElse(null);
            if (typeClass != null) {
                if (type.isArray()) {
                    Type arrayType = Type.getType(Array.newInstance(typeClass, 0).getClass());
                    methodVisitor.push(arrayType);
                } else {
                    Type wrapperType = Type.getType(ReflectionUtils.getWrapperType(typeClass));
                    methodVisitor.visitFieldInsn(GETSTATIC, wrapperType.getInternalName(), "TYPE", Type.getDescriptor(Class.class));
                }
            } else {
                methodVisitor.push(getTypeReference(type));
            }
        } else {
            methodVisitor.push(getTypeReference(type));
        }
        // store the type reference
        methodVisitor.arrayStore(TYPE_CLASS);
        // if we are not at the end of the array duplicate array onto the stack
        if (index < (size - 1)) {
            methodVisitor.dup();
        }
    }

    /**
     * @param types The types
     * @return An array with the {@link Type} of the objects
     */
    protected Type[] getTypes(Collection<ClassElement> types) {
        Type[] converted = new Type[types.size()];
        Iterator<ClassElement> iter = types.iterator();
        for (int i = 0; i < converted.length; i++) {
            ClassElement type = iter.next();
            converted[i] = getTypeReference(type);
        }
        return converted;
    }

    /**
     * @param type The type
     * @return The {@link Type} for the object type
     */
    protected static Type getObjectType(Object type) {
        if (type instanceof TypedElement) {
            String name = ((TypedElement) type).getType().getName();
            String internalName = getTypeDescriptor(name);
            return Type.getType(internalName);
        } else if (type instanceof Class) {
            return Type.getType((Class) type);
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
        if (NAME_TO_TYPE_MAP.containsKey(className)) {
            return NAME_TO_TYPE_MAP.get(className);
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
     * Writes the class file to disk in the given directory.
     *
     * @param targetDir   The target directory
     * @param classWriter The current class writer
     * @param className   The class name
     * @throws IOException if there is a problem writing the class to disk
     */
    protected void writeClassToDisk(File targetDir, ClassWriter classWriter, String className) throws IOException {
        if (targetDir != null) {

            String fileName = className.replace('.', '/') + ".class";
            File targetFile = new File(targetDir, fileName);
            targetFile.getParentFile().mkdirs();

            try (OutputStream outputStream = Files.newOutputStream(targetFile.toPath())) {
                writeClassToDisk(outputStream, classWriter);
            }
        }
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
        classWriter.visit(V1_8, ACC_SYNTHETIC, className, null, superType.getInternalName(), null);
        classWriter.visitAnnotation(TYPE_GENERATED.getDescriptor(), false);
    }

    /**
     * @param classWriter The current class writer
     * @param className   The class name
     * @param superType   The super type
     */
    protected void startPublicClass(ClassVisitor classWriter, String className, Type superType) {
        classWriter.visit(V1_8, ACC_PUBLIC | ACC_SYNTHETIC, className, null, superType.getInternalName(), null);
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
        classWriter.visit(V1_8, ACC_PUBLIC | ACC_FINAL | ACC_SYNTHETIC, internalClassName, null, superType.getInternalName(), interfaces);
        AnnotationVisitor annotationVisitor = classWriter.visitAnnotation(TYPE_GENERATED.getDescriptor(), false);
        annotationVisitor.visit("service", serviceName);
        annotationVisitor.visitEnd();
    }

    /**
     * @param classWriter The current class writer
     * @param className   The class name
     * @param superType   The super type
     */
    protected void startFinalClass(ClassVisitor classWriter, String className, Type superType) {
        classWriter.visit(V1_8, ACC_FINAL | ACC_SYNTHETIC, className, null, superType.getInternalName(), null);
        classWriter.visitAnnotation(TYPE_GENERATED.getDescriptor(), false);
    }

    /**
     * Starts a public final class.
     *
     * @param classWriter The current class writer
     * @param className   The class name
     * @param superType   The super type
     */
    protected void startPublicFinalClass(ClassVisitor classWriter, String className, Type superType) {
        classWriter.visit(V1_8, ACC_PUBLIC | ACC_FINAL | ACC_SYNTHETIC, className, null, superType.getInternalName(), null);
        classWriter.visitAnnotation(TYPE_GENERATED.getDescriptor(), false);
    }

    /**
     * @param classWriter      The current class writer
     * @param className        The class name
     * @param superType        The super type
     * @param genericSignature The generic signature
     */
    protected void startClass(ClassWriter classWriter, String className, Type superType, String genericSignature) {
        classWriter.visit(V1_8, ACC_SYNTHETIC, className, genericSignature, superType.getInternalName(), null);
        classWriter.visitAnnotation(TYPE_GENERATED.getDescriptor(), false);
    }

    /**
     * @param cv            The constructor visitor
     * @param superClass    The super class
     * @param argumentTypes The argument types
     */
    protected void invokeConstructor(MethodVisitor cv, Class superClass, Class... argumentTypes) {
        try {
            Type superType = Type.getType(superClass);
            Type superConstructor = Type.getType(superClass.getDeclaredConstructor(argumentTypes));
            cv.visitMethodInsn(INVOKESPECIAL,
                    superType.getInternalName(),
                    CONSTRUCTOR_NAME,
                    superConstructor.getDescriptor(),
                    false);
        } catch (NoSuchMethodException e) {
            throw new ClassGenerationException("Micronaut version on compile classpath doesn't match", e);
        }
    }

    /**
     * @param visitor    The interface visitor
     * @param targetType The target type
     * @param method     The method
     */
    protected static void invokeInterfaceStaticMethod(MethodVisitor visitor, Class targetType, Method method) {
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
    protected GeneratorAdapter startPublicMethodZeroArgs(ClassWriter classWriter, Class returnType, String methodName) {
        Type methodType = Type.getMethodType(Type.getType(returnType));

        return new GeneratorAdapter(classWriter.visitMethod(ACC_PUBLIC, methodName, methodType.getDescriptor(), null, null), ACC_PUBLIC, methodName, methodType.getDescriptor());
    }

    /**
     * @param classWriter The current class writer
     * @param returnType  The return type
     * @param methodName  The method name
     * @return TheThe {@link GeneratorAdapter} for the method
     */
    protected GeneratorAdapter startPublicFinalMethodZeroArgs(ClassWriter classWriter, Class returnType, String methodName) {
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
     * @param type The type
     * @return the internal name for cast
     */
    protected static String getInternalNameForCast(TypedElement type) {
        ClassElement ce = type.getType();
        if (ce.isPrimitive() && !ce.isArray()) {

            final Optional<Class> pt = ClassUtils.getPrimitiveType(ce.getName());
            if (pt.isPresent()) {
                return Type.getInternalName(ReflectionUtils.getWrapperType(pt.get()));
            } else {
                return getTypeReference(ce).getInternalName();
            }
        } else {
            return getTypeReference(ce).getInternalName();
        }
    }

    /**
     * @param typeClass The type
     * @return the internal name for cast
     */
    protected static String getInternalNameForCast(Class<?> typeClass) {
        if (typeClass.isPrimitive()) {
            typeClass = ReflectionUtils.getWrapperType(typeClass);
        }
        return Type.getInternalName(typeClass);
    }

    /**
     * @param type The type
     * @return the internal name for cast
     */
    protected static String getInternalNameForCast(Type type) {
        final Optional<Class> pt = ClassUtils.getPrimitiveType(type.getClassName());
        if (pt.isPresent()) {
            return Type.getInternalName(ReflectionUtils.getWrapperType(pt.get()));
        } else {
            return type.getInternalName();
        }
    }

    /**
     * @param className The class name
     * @return The class file name
     */
    protected String getClassFileName(String className) {
        return className.replace('.', File.separatorChar) + ".class";
    }

    /**
     * @param compilationDir The compilation directory
     * @return The directory class writer output visitor
     */
    protected ClassWriterOutputVisitor newClassWriterOutputVisitor(File compilationDir) {
        return new DirectoryClassWriterOutputVisitor(compilationDir);
    }

    /**
     * @param overriddenMethodGenerator The overridden method generator
     */
    protected void returnVoid(GeneratorAdapter overriddenMethodGenerator) {
        overriddenMethodGenerator.pop();
        overriddenMethodGenerator.visitInsn(RETURN);
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
     * @param asmMethod     The asm method
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
     * Generates a service discovery for the given class name and file.
     *
     * @param className     The class name
     * @param generatedFile The generated file
     * @throws IOException An exception if an error occurs
     */
    protected void generateServiceDescriptor(String className, GeneratedFile generatedFile) throws IOException {
        CharSequence contents = generatedFile.getTextContent();
        if (contents != null) {
            String[] entries = contents.toString().split("\\n");
            if (!Arrays.asList(entries).contains(className)) {
                try (BufferedWriter w = new BufferedWriter(generatedFile.openWriter())) {
                    w.newLine();
                    w.write(className);
                }
            }
        } else {
            try (BufferedWriter w = new BufferedWriter(generatedFile.openWriter())) {
                w.write(className);
            }
        }
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

    /**
     * Invokes the given method.
     * @param generatorAdapter The generator adapter
     * @param method The method to invoke
     * @return The return type
     */
    protected @NonNull ClassElement invokeMethod(@NonNull GeneratorAdapter generatorAdapter, @NonNull MethodElement method) {
        ClassElement returnType = method.getReturnType();
        Method targetMethod = new Method(method.getName(), getMethodDescriptor(returnType, Arrays.asList(method.getParameters())));
        ClassElement declaringElement = method.getDeclaringType();
        Type declaringType = getTypeReference(declaringElement);
        if (method.isStatic()) {
            generatorAdapter.invokeStatic(declaringType, targetMethod);
        } else if (declaringElement.isInterface()) {
            generatorAdapter.invokeInterface(declaringType, targetMethod);
        } else {
            generatorAdapter.invokeVirtual(declaringType, targetMethod);
        }
        return returnType;
    }
}
