/*
 * Copyright 2017-2018 original authors
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

import io.micronaut.core.reflect.ReflectionUtils;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Abstract class that writes generated classes to disk and provides convenience methods for building classes.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class AbstractClassFileWriter implements Opcodes {

    protected static final String CONSTRUCTOR_NAME = "<init>";
    protected static final String DESCRIPTOR_DEFAULT_CONSTRUCTOR = "()V";
    protected static final Method METHOD_DEFAULT_CONSTRUCTOR = new Method(CONSTRUCTOR_NAME, DESCRIPTOR_DEFAULT_CONSTRUCTOR);
    protected static final Type TYPE_OBJECT = Type.getType(Object.class);
    protected static final Type TYPE_CLASS = Type.getType(Class.class);
    protected static final int DEFAULT_MAX_STACK = 13;

    protected static final Map<String, String> NAME_TO_TYPE_MAP = new HashMap<>();

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
        if (type instanceof Class) {
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

    /**
     * @param methodVisitor The {@link MethodVisitor}
     * @param type          The type
     */
    protected static void pushCastToType(MethodVisitor methodVisitor, Object type) {
        String internalName = getInternalNameForCast(type);
        methodVisitor.visitTypeInsn(CHECKCAST, internalName);
        if (type instanceof Class) {
            Class typeClass = (Class) type;
            if (typeClass.isPrimitive()) {
                Type primitiveType = Type.getType(typeClass);
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

            try (FileOutputStream outputStream = new FileOutputStream(targetFile)) {
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
        classWriter.visit(V1_8, ACC_PUBLIC, className, null, superType.getInternalName(), null);
    }

    /**
     * @param classWriter      The current class writer
     * @param className        The class name
     * @param superType        The super type
     * @param genericSignature The generic signature
     */
    protected void startClass(ClassWriter classWriter, String className, Type superType, String genericSignature) {
        classWriter.visit(V1_8, ACC_PUBLIC, className, genericSignature, superType.getInternalName(), null);
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
}
