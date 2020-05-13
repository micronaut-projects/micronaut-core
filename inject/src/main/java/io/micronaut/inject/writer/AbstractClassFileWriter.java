/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.inject.writer;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Generated;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.annotation.AnnotationMetadataWriter;
import io.micronaut.inject.annotation.DefaultAnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.TypedElement;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Supplier;

/**
 * Abstract class that writes generated classes to disk and provides convenience methods for building classes.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public abstract class AbstractClassFileWriter implements Opcodes {

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
    }

    protected final Element originatingElement;

    /**
     * @param originatingElement The originating element
     */
    protected AbstractClassFileWriter(Element originatingElement) {
        this.originatingElement = originatingElement;
    }

    /**
     * Pushes type arguments onto the stack.
     *
     * @param generatorAdapter The generator adapter
     * @param declaringElement     The declaring class element of the generics
     * @param types            The type references
     */
    protected static void pushTypeArgumentElements(
            GeneratorAdapter generatorAdapter,
            TypedElement declaringElement,
            Map<String, ClassElement> types) {
        if (types == null || types.isEmpty()) {
            generatorAdapter.visitInsn(ACONST_NULL);
            return;
        }
        Set<String> visitedTypes = new HashSet<>(5);
        pushTypeArgumentElements(generatorAdapter, declaringElement, types, visitedTypes);
    }

    private static void pushTypeArgumentElements(
            GeneratorAdapter generatorAdapter,
            TypedElement declaringElement,
            Map<String, ClassElement> types,
            Set<String> visitedTypes) {
        if (visitedTypes.contains(declaringElement.getName())) {
            generatorAdapter.getStatic(
                    TYPE_ARGUMENT,
                    ZERO_ARGUMENTS_CONSTANT,
                    TYPE_ARGUMENT_ARRAY
            );
        } else {
            visitedTypes.add(declaringElement.getName());

            int len = types.size();
            // Build calls to Argument.create(...)
            pushNewArray(generatorAdapter, Argument.class, len);
            int i = 0;
            for (Map.Entry<String, ClassElement> entry : types.entrySet()) {
                // the array index
                generatorAdapter.push(i);
                String argumentName = entry.getKey();
                ClassElement classElement = entry.getValue();
                Object classReference = toClassReference(classElement);
                Map<String, ClassElement> typeArguments = null;

                if (!classElement.getName().equals(declaringElement.getName())) {
                    typeArguments = classElement.getTypeArguments();
                }
                if (CollectionUtils.isNotEmpty(typeArguments)) {
                    buildArgumentWithGenerics(generatorAdapter, argumentName, classReference, classElement, typeArguments, visitedTypes);
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

    private static Object toClassReference(ClassElement classElement) {
        String n = classElement.getName();
        Object classReference;
        if (classElement.isPrimitive()) {
            if (classElement.isArray()) {
                classReference = ClassUtils.arrayTypeForPrimitive(n).map(t -> (Object) t).orElse(n);
            } else {
                classReference = ClassUtils.getPrimitiveType(n).map(t -> (Object) t).orElse(n);
            }
        } else {
            if (classElement.isArray()) {
                classReference = n + "[]";
            } else {
                classReference = n;
            }
        }
        return classReference;
    }


    /**
     * Pushes type arguments onto the stack.
     *
     * @param generatorAdapter The generator adapter
     * @param types            The type references
     */
    protected static void pushTypeArguments(GeneratorAdapter generatorAdapter, Map<String, Object> types) {
        if (types == null || types.isEmpty()) {
            generatorAdapter.visitInsn(ACONST_NULL);
            return;
        }
        int len = types.size();
        // Build calls to Argument.create(...)
        pushNewArray(generatorAdapter, Argument.class, len);
        int i = 0;
        for (Map.Entry<String, Object> entry : types.entrySet()) {
            // the array index
            generatorAdapter.push(i);
            String typeParameterName = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map) {
                buildArgumentWithGenerics(generatorAdapter, typeParameterName, (Map) value);
            } else {
                buildArgument(generatorAdapter, typeParameterName, value);
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

    /**
     * Builds an argument instance.
     *
     * @param generatorAdapter The generator adapter.
     * @param argumentName     The argument name
     * @param objectType       The object type
     */
    protected static void buildArgument(GeneratorAdapter generatorAdapter, String argumentName, Object objectType) {
        // 1st argument: the type
        generatorAdapter.push(getTypeReference(objectType));
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
     *  @param generatorAdapter The generator adapter to use
     * @param argumentName     The argument name
     * @param typeReference    The type name
     * @param classElement     The class element that declares the generics
     * @param typeArguments    The nested type arguments
     * @param visitedTypes
     */
    private static void buildArgumentWithGenerics(
            GeneratorAdapter generatorAdapter, String argumentName,
            Object typeReference,
            ClassElement classElement,
            Map<String, ClassElement> typeArguments, Set<String> visitedTypes) {
        // 1st argument: the type
        generatorAdapter.push(getTypeReference(typeReference));
        // 2nd argument: the name
        generatorAdapter.push(argumentName);
        // 3rd argument, more generics
        pushTypeArgumentElements(generatorAdapter, classElement, typeArguments, visitedTypes);

        // Argument.create( .. )
        invokeInterfaceStaticMethod(
                generatorAdapter,
                Argument.class,
                METHOD_CREATE_ARGUMENT_WITH_GENERICS
        );
    }

    /**
     * This method should be replaced by the above method.
     *
     * @param generatorAdapter The {@link GeneratorAdapter}
     * @param argumentName     The argument name
     * @param nestedTypeObject The nested type object
     */
    static void buildArgumentWithGenerics(GeneratorAdapter generatorAdapter, String argumentName, Map nestedTypeObject) {
        Map nestedTypes = null;
        @SuppressWarnings("unchecked") Optional<Map.Entry> nestedEntry = nestedTypeObject.entrySet().stream().findFirst();
        Object objectType;
        if (nestedEntry.isPresent()) {
            Map.Entry data = nestedEntry.get();
            Object key = data.getKey();
            Object map = data.getValue();
            objectType = key;
            if (map instanceof Map) {
                nestedTypes = (Map) map;
            }
        } else {
            throw new IllegalArgumentException("Must be a map with a single key containing the argument type and a map of generics as the value");
        }

        // 1st argument: the type
        generatorAdapter.push(getTypeReference(objectType));
        // 2nd argument: the name
        generatorAdapter.push(argumentName);

        // 3rd argument: generic types
        boolean hasGenerics = nestedTypes != null && !nestedTypes.isEmpty();
        if (hasGenerics) {
            pushTypeArguments(generatorAdapter, nestedTypes);
        }

        // Argument.create( .. )
        invokeInterfaceStaticMethod(
                generatorAdapter,
                Argument.class,
                hasGenerics ? METHOD_CREATE_ARGUMENT_WITH_GENERICS : METHOD_CREATE_ARGUMENT_SIMPLE
        );
    }

    /**
     * @param owningType                 The owning type
     * @param declaringClassWriter       The declaring class writer
     * @param generatorAdapter           The {@link GeneratorAdapter}
     * @param argumentTypes              The argument types
     * @param argumentAnnotationMetadata The argument annotation metadata
     * @param genericTypes               The generic types
     * @param loadTypeMethods            The load type methods
     */
    protected static void pushBuildArgumentsForMethod(
            Type owningType,
            ClassWriter declaringClassWriter,
            GeneratorAdapter generatorAdapter,
            Map<String, Object> argumentTypes,
            Map<String, AnnotationMetadata> argumentAnnotationMetadata,
            Map<String, Map<String, Object>> genericTypes,
            Map<String, GeneratorAdapter> loadTypeMethods) {
        int len = argumentTypes.size();
        pushNewArray(generatorAdapter, Argument.class, len);
        int i = 0;
        for (Map.Entry<String, Object> entry : argumentTypes.entrySet()) {
            // the array index position
            generatorAdapter.push(i);

            String argumentName = entry.getKey();
            final Object value = entry.getValue();
            Type argumentType;
            if (value instanceof Map) {
                // for generic types the info as passed with the class name being the key of the map
                // bit of a hack and we need better data structures for this.
                argumentType = getTypeReference(((Map) value).keySet().iterator().next());
            } else {
                argumentType = getTypeReference(value);
            }


            // 1st argument: The type
            generatorAdapter.push(argumentType);

            // 2nd argument: The argument name
            generatorAdapter.push(argumentName);

            // 3rd argument: The annotation metadata
            AnnotationMetadata annotationMetadata = argumentAnnotationMetadata.get(argumentName);
            if (annotationMetadata == null || annotationMetadata == AnnotationMetadata.EMPTY_METADATA) {
                generatorAdapter.visitInsn(ACONST_NULL);
            } else {
                AnnotationMetadataWriter.instantiateNewMetadata(
                        owningType,
                        declaringClassWriter,
                        generatorAdapter,
                        (DefaultAnnotationMetadata) annotationMetadata,
                        loadTypeMethods
                );
            }

            // 4th argument: The generic types
            if (genericTypes != null && genericTypes.containsKey(argumentName)) {
                Map<String, Object> types = genericTypes.get(argumentName);
                pushTypeArguments(generatorAdapter, types);
            } else {
                generatorAdapter.visitInsn(ACONST_NULL);
            }

            // Argument.create( .. )
            invokeInterfaceStaticMethod(
                    generatorAdapter,
                    Argument.class,
                    METHOD_CREATE_ARGUMENT_WITH_ANNOTATION_METADATA_GENERICS
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
     * Write the class to the target directory.
     *
     * @param targetDir The target directory
     * @throws IOException if there is an error writing the file
     */
    public void writeTo(File targetDir) throws IOException {
        accept(newClassWriterOutputVisitor(targetDir));
    }

    /**
     * Obtain the type for a given element.
     *
     * @param type The element type
     * @return The type
     */
    protected Type getTypeForElement(@NonNull TypedElement type) {
        Type propertyType;
        final Optional<Class> pt;
        final String typeName = type.getName();
        if (type.isPrimitive()) {
            if (type.isArray()) {
                pt = ClassUtils.arrayTypeForPrimitive(typeName);
            } else {
                pt = ClassUtils.getPrimitiveType(typeName);
            }
        } else {
            pt = Optional.empty();
        }
        if (pt.isPresent()) {
            propertyType = getTypeReference(pt.get());
        } else {
            if (type.isArray()) {
                propertyType = getTypeReference(typeName + "[]");
            } else {
                propertyType = getTypeReference(typeName);
            }
        }
        return propertyType;
    }

    /**
     * Converts a map of class elements to type arguments.
     *
     * @param typeArguments The type arguments
     * @return The type arguments
     */
    @NotNull
    protected Map<String, Object> toTypeArguments(@NotNull Map<String, ClassElement> typeArguments) {
        Set<String> visitedTypes = new HashSet<>(5);

        return toTypeArguments(typeArguments, visitedTypes);
    }

    @NotNull
    private Map<String, Object> toTypeArguments(@NotNull Map<String, ClassElement> typeArguments, Set<String> visitedTypes) {
        final LinkedHashMap<String, Object> map = new LinkedHashMap<>(typeArguments.size());
        for (Map.Entry<String, ClassElement> entry : typeArguments.entrySet()) {
            final ClassElement ce = entry.getValue();
            String className = ce.getName();
            if (!visitedTypes.contains(entry.getKey())) {
                visitedTypes.add(entry.getKey());
                final Map<String, ClassElement> subArgs = ce.getTypeArguments();
                if (CollectionUtils.isNotEmpty(subArgs)) {
                    Map<String, Object> m = toTypeArguments(subArgs, visitedTypes);
                    if (CollectionUtils.isNotEmpty(m)) {
                        map.put(entry.getKey(), m);
                    } else {
                        map.put(entry.getKey(), Collections.singletonMap(entry.getKey(), className));
                    }
                } else {
                    final Type typeReference = getTypeForElement(ce);
                    map.put(entry.getKey(), typeReference);
                }
            }
        }

        return map;
    }

    /**
     * Converts a map of class elements to type arguments.
     *
     * @param parameters The parametesr
     * @return The type arguments
     */
    @NotNull
    protected Map<String, Map<String, Object>> toTypeArguments(ParameterElement... parameters) {
        final LinkedHashMap<String, Map<String, Object>> map = new LinkedHashMap<>(parameters.length);
        for (ParameterElement ce : parameters) {
            final ClassElement type = ce.getType();
            final Map<String, ClassElement> subArgs = type.getTypeArguments();
            if (CollectionUtils.isNotEmpty(subArgs)) {
                map.put(ce.getName(), toTypeArguments(subArgs));
            }
        }
        return map;
    }

    /**
     * Converts a parameters to type arguments.
     *
     * @param parameters The parameters
     * @return The type arguments
     */
    @NotNull
    protected Map<String, Object> toParameterTypes(ParameterElement... parameters) {
        final LinkedHashMap<String, Object> map = new LinkedHashMap<>(parameters.length);
        for (ParameterElement ce : parameters) {
            final ClassElement type = ce.getType();
            if (type == null) {
                continue;
            }
            final Type typeReference = getTypeForElement(type);
            map.put(ce.getName(), typeReference);
        }

        return map;
    }

    /**
     * Writes a method that returns a boolean value with the value supplied by the given supplier.
     * @param classWriter The class writer
     * @param methodName The method name
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
    public @Nullable Element getOriginatingElement() {
        return this.originatingElement;
    }

    /**
     * Accept a ClassWriterOutputVisitor to write this writer to disk.
     *
     * @param classWriterOutputVisitor The {@link ClassWriterOutputVisitor}
     * @throws IOException if there is an error writing to disk
     */
    public abstract void accept(ClassWriterOutputVisitor classWriterOutputVisitor) throws IOException;

    /**
     * Returns the descriptor corresponding to the given class.
     *
     * @param type The type
     * @return The descriptor for the class
     */
    protected static String getTypeDescriptor(Object type) {
        if (type instanceof Class) {
            return Type.getDescriptor((Class) type);
        } else if (type instanceof Type) {
            return ((Type) type).getDescriptor();
        } else {
            String className = type.toString();
            return getTypeDescriptor(className, new String[0]);
        }
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
    protected static Type getTypeReference(Object type) {
        if (type instanceof Type) {
            return (Type) type;
        } else if (type instanceof Class) {
            return Type.getType((Class) type);
        } else if (type instanceof String) {
            String className = type.toString();

            String internalName = getInternalName(className);
            if (className.endsWith("[]")) {
                internalName = "[L" + internalName + ";";
            }
            return Type.getObjectType(internalName);
        } else {
            throw new IllegalArgumentException("Type reference [" + type + "] should be a Class or a String representing the class name");
        }
    }

    /**
     * @param fieldType           The field type
     * @param injectMethodVisitor The {@link MethodVisitor}
     */
    protected static void pushBoxPrimitiveIfNecessary(Object fieldType, MethodVisitor injectMethodVisitor) {
        if (fieldType instanceof Type) {
            final Type t = (Type) fieldType;
            final Optional<Class> pt = ClassUtils.getPrimitiveType(t.getClassName());
            Class wrapperType = pt.map(ReflectionUtils::getWrapperType).orElse(null);
            if (wrapperType != null) {
                Type wrapper = Type.getType(wrapperType);
                String primitiveName = t.getClassName();
                String sig = wrapperType.getName() + " valueOf(" + primitiveName + ")";
                org.objectweb.asm.commons.Method valueOfMethod = org.objectweb.asm.commons.Method.getMethod(sig);
                injectMethodVisitor.visitMethodInsn(INVOKESTATIC, wrapper.getInternalName(), "valueOf", valueOfMethod.getDescriptor(), false);
            }
        } else {
            Class wrapperType = AbstractClassFileWriter.getWrapperType(fieldType);
            if (wrapperType != null) {
                Class primitiveType = (Class) fieldType;
                Type wrapper = Type.getType(wrapperType);
                String primitiveName = primitiveType.getName();
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
    protected static void pushCastToType(MethodVisitor methodVisitor, Object type) {
        String internalName = getInternalNameForCast(type);
        methodVisitor.visitTypeInsn(CHECKCAST, internalName);
        Type primitiveType = null;
        if (type instanceof Class) {
            Class typeClass = (Class) type;
            if (typeClass.isPrimitive()) {
                primitiveType = Type.getType(typeClass);
            }
        } else if (type instanceof Type) {
            final Optional<Class> pt = ClassUtils.getPrimitiveType(((Type) type).getClassName());
            if (pt.isPresent()) {
                primitiveType = Type.getType(pt.get());
            }
        }

        if (primitiveType != null) {
            org.objectweb.asm.commons.Method valueMethod = null;
            switch (primitiveType.getSort()) {
                case Type.BOOLEAN:
                    valueMethod = org.objectweb.asm.commons.Method.getMethod("boolean booleanValue()");
                    break;
                case Type.CHAR:
                    valueMethod = org.objectweb.asm.commons.Method.getMethod("char charValue()");
                    break;
                case Type.BYTE:
                    valueMethod = org.objectweb.asm.commons.Method.getMethod("byte byteValue()");
                    break;
                case Type.SHORT:
                    valueMethod = org.objectweb.asm.commons.Method.getMethod("short shortValue()");
                    break;
                case Type.INT:
                    valueMethod = org.objectweb.asm.commons.Method.getMethod("int intValue()");
                    break;
                case Type.LONG:
                    valueMethod = org.objectweb.asm.commons.Method.getMethod("long longValue()");
                    break;
                case Type.DOUBLE:
                    valueMethod = org.objectweb.asm.commons.Method.getMethod("double doubleValue()");
                    break;
                case Type.FLOAT:
                    valueMethod = org.objectweb.asm.commons.Method.getMethod("float floatValue()");
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
    protected static void pushReturnValue(MethodVisitor methodVisitor, Object type) {
        if (type instanceof Class) {
            Class typeClass = (Class) type;
            if (typeClass.isPrimitive()) {
                Type primitiveType = Type.getType(typeClass);
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
            } else {
                methodVisitor.visitInsn(ARETURN);
            }
        } else {
            methodVisitor.visitInsn(ARETURN);
        }
    }

    /**
     * @param type The type
     * @return The class
     */
    protected static Class getWrapperType(Object type) {
        if (isPrimitive(type)) {
            return ReflectionUtils.getWrapperType((Class) type);
        }
        return null;
    }

    /**
     * @param type The type
     * @return Whether a type is primitive
     */
    protected static boolean isPrimitive(Object type) {
        if (type instanceof Class) {
            Class typeClass = (Class) type;
            return typeClass.isPrimitive();
        }
        return false;
    }

    /**
     * @param methodVisitor The method visitor as {@link GeneratorAdapter}
     * @param methodName    The method name
     * @param argumentTypes The argument types
     */
    protected static void pushMethodNameAndTypesArguments(GeneratorAdapter methodVisitor, String methodName, Collection<Object> argumentTypes) {
        // and the method name
        methodVisitor.visitLdcInsn(methodName);

        int argTypeCount = argumentTypes.size();
        if (!argumentTypes.isEmpty()) {
            pushNewArray(methodVisitor, Class.class, argTypeCount);
            Iterator<Object> argIterator = argumentTypes.iterator();
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
    protected static void pushNewArray(GeneratorAdapter methodVisitor, Class arrayType, int size) {
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
    protected static void pushStoreTypeInArray(GeneratorAdapter methodVisitor, int index, int size, Object type) {
        // the array index position
        methodVisitor.push(index);
        // the type reference
        if (type instanceof Class) {
            Class typeClass = (Class) type;
            if (typeClass.isPrimitive()) {
                Type wrapperType = Type.getType(ReflectionUtils.getWrapperType(typeClass));

                methodVisitor.visitFieldInsn(GETSTATIC, wrapperType.getInternalName(), "TYPE", Type.getDescriptor(Class.class));
            } else {
                methodVisitor.push(Type.getType(typeClass));
            }
        } else {
            methodVisitor.push(getObjectType(type.toString()));
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
    protected Type[] getObjectTypes(Collection types) {
        Type[] converted = new Type[types.size()];
        Iterator iter = types.iterator();
        for (int i = 0; i < converted.length; i++) {
            Object type = iter.next();
            converted[i] = getObjectType(type);
        }
        return converted;
    }

    /**
     * @param type The type
     * @return The {@link Type} for the object type
     */
    protected static Type getObjectType(Object type) {
        if (type instanceof Class) {
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
            StringBuilder start;
            if (className.endsWith("[]")) {
                start = new StringBuilder("[L" + internalName);
            } else {
                start = new StringBuilder('L' + internalName);
            }
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

        builder.append(")");

        builder.append(getTypeDescriptor(returnType));
        return builder.toString();
    }

    /**
     * @param returnType    The return type
     * @param argumentTypes The argument types
     * @return The method descriptor
     */
    protected static String getMethodDescriptor(Object returnType, Collection<Object> argumentTypes) {
        StringBuilder builder = new StringBuilder();
        builder.append('(');

        for (Object argumentType : argumentTypes) {
            builder.append(getTypeDescriptor(argumentType));
        }

        builder.append(")");

        builder.append(getTypeDescriptor(returnType));
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

        builder.append(")");

        builder.append(returnTypeReference);
        return builder.toString();
    }

    /**
     * @param argumentTypes The argument types
     * @return The constructor descriptor
     */
    protected static String getConstructorDescriptor(Object... argumentTypes) {
        return getConstructorDescriptor(Arrays.asList(argumentTypes));
    }

    /**
     * @param argList The argument list
     * @return The constructor descriptor
     */
    protected static String getConstructorDescriptor(Collection<Object> argList) {
        StringBuilder builder = new StringBuilder();
        builder.append('(');

        for (Object argumentType : argList) {
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
    protected GeneratorAdapter startConstructor(ClassVisitor classWriter, Object... argumentTypes) {
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
        if (newClassName.endsWith("[]")) {
            return newClassName.substring(0, newClassName.length() - 2);
        }
        return newClassName;
    }

    /**
     * @param type The type
     * @return the internal name for cast
     */
    protected static String getInternalNameForCast(Object type) {
        if (type instanceof Class) {
            Class typeClass = (Class) type;
            if (typeClass.isPrimitive()) {
                typeClass = ReflectionUtils.getWrapperType(typeClass);
            }
            return Type.getInternalName(typeClass);
        } else if (type instanceof Type) {
            final Type t = (Type) type;
            final Optional<Class> pt = ClassUtils.getPrimitiveType(t.getClassName());
            if (pt.isPresent()) {
                return Type.getInternalName(ReflectionUtils.getWrapperType(pt.get()));
            } else {
                return t.getInternalName();
            }
        } else {
            String className = type.toString();
            if (className.endsWith("[]")) {
                return getTypeDescriptor(type);
            } else {
                return getInternalName(className);
            }
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
     * @param overriddenMethodGenerator The overriden method generator
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

}
