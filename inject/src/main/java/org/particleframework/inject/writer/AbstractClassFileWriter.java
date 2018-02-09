package org.particleframework.inject.writer;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.particleframework.core.annotation.AnnotationSource;
import org.particleframework.core.io.service.SoftServiceLoader;
import org.particleframework.core.reflect.ReflectionUtils;
import org.particleframework.core.util.ArrayUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.util.*;

/**
 * Abstract class that writes generated classes to disk and provides convenience methods for building classes
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class AbstractClassFileWriter implements Opcodes {

    protected static final String CONSTRUCTOR_NAME = "<init>";
    protected static final String DESCRIPTOR_DEFAULT_CONSTRUCTOR = "()V";
    protected static final Method METHOD_DEFAULT_CONSTRUCTOR = new Method(CONSTRUCTOR_NAME, DESCRIPTOR_DEFAULT_CONSTRUCTOR);
    protected static final int MODIFIERS_PRIVATE_STATIC_FINAL = ACC_PRIVATE | ACC_FINAL | ACC_STATIC;
    protected static final Type TYPE_OBJECT = Type.getType(Object.class);
    protected static final Type TYPE_METHOD = Type.getType(java.lang.reflect.Method.class);
    protected static final int ACC_PRIVATE_STATIC_FINAL = ACC_PRIVATE | ACC_FINAL | ACC_STATIC;
    protected static final Type TYPE_CONSTRUCTOR = Type.getType(Constructor.class);
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

    protected static Type getTypeReference(String className, String... genericTypes) {
        String referenceString = getTypeDescriptor(className, genericTypes);
        return Type.getType(referenceString);
    }

    protected static String getTypeDescriptor(Object type) {
        if(type instanceof Class) {
            return Type.getDescriptor((Class)type);
        }
        else {
            String className = type.toString();
            return getTypeDescriptor(className, new String[0]);
        }
    }

    protected static Type getTypeReference(Object type) {
        if(type instanceof Class) {
            return Type.getType((Class)type);
        }
        else if(type instanceof String) {
            String className = type.toString();

            String internalName = getInternalName(className);
            return Type.getObjectType(internalName);
        }
        else {
            throw new IllegalArgumentException("Type reference ["+type+"] should be a Class or a String representing the class name");
        }
    }

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
                }

                if (valueMethod != null) {
                    methodVisitor.visitMethodInsn(INVOKEVIRTUAL, internalName, valueMethod.getName(), valueMethod.getDescriptor(), false);
                }
            }
        }
    }


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
                }

            }
            else {
                methodVisitor.visitInsn(ARETURN);
            }
        }
        else {
            methodVisitor.visitInsn(ARETURN);
        }
    }

    protected static Class getWrapperType(Object type) {
        if (isPrimitive(type)) {
            return ReflectionUtils.getWrapperType((Class)type);
        }
        return null;
    }

    protected static boolean isPrimitive(Object type) {
        if (type instanceof Class) {
            Class typeClass = (Class) type;
            return typeClass.isPrimitive();
        }
        return false;
    }

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

    protected Type[] getObjectTypes(Collection types) {
        Type[] converted = new Type[types.size()];
        Iterator iter = types.iterator();
        for (int i = 0; i < converted.length; i++) {
            Object type = iter.next();
            converted[i] = getObjectType(type);
        }
        return converted;
    }

    protected Type[] getObjectTypes(Object... types) {
        Type[] converted = new Type[types.length];
        for (int i = 0; i < types.length; i++) {
            Object type = types[i];
            converted[i] = getObjectType(type);
        }
        return converted;
    }

    protected static Type getObjectType(Object type) {
        if(type instanceof Class) {
            return Type.getType((Class)type);
        }
        else if(type instanceof String) {
            String className = type.toString();

            String internalName = getTypeDescriptor(className);
            return Type.getType(internalName);
        }
        else {
            throw new IllegalArgumentException("Type reference ["+type+"] should be a Class or a String representing the class name");
        }
    }
    protected static String getTypeDescriptor(String className, String... genericTypes) {
        if(NAME_TO_TYPE_MAP.containsKey(className)) {
            return NAME_TO_TYPE_MAP.get(className);
        }
        if("void".equals(className)) {
            return "V";
        }
        String internalName = getInternalName(className);
        StringBuilder start;
        if(className.endsWith("[]")) {
            start = new StringBuilder("[L" + internalName);
        }
        else {
            start = new StringBuilder('L' + internalName);
        }
        if(genericTypes != null && genericTypes.length >0) {
            start.append('<');
            for (String genericType : genericTypes) {
                start.append(getTypeDescriptor(genericType));
            }
            start.append('>');
        }
        return start.append(';').toString();
    }

    protected static String getMethodDescriptor(String returnType, String...argumentTypes) {
        StringBuilder builder = new StringBuilder();
        builder.append('(');

        for (String argumentType : argumentTypes) {
            builder.append(getTypeDescriptor(argumentType));
        }

        builder.append(")");

        builder.append(getTypeDescriptor(returnType));
        return builder.toString();
    }

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


    protected static String getConstructorDescriptor(Object...argumentTypes) {
        return getConstructorDescriptor(Arrays.asList(argumentTypes));
    }


    protected static String getConstructorDescriptor(Collection<Object> argList) {
        StringBuilder builder = new StringBuilder();
        builder.append('(');

        for (Object argumentType : argList) {
            builder.append(getTypeDescriptor(argumentType));
        }

        return builder.append(")V").toString();
    }

    /**
     * Writes the class file to disk in the given directory
     *
     * @param targetDir The target directory
     * @param classWriter The current class writer
     * @param className The class name
     */
    protected void writeClassToDisk(File targetDir, ClassWriter classWriter, String className) throws IOException {
        if(targetDir != null) {

            String fileName = className.replace('.','/') + ".class";
            File targetFile = new File(targetDir, fileName);
            targetFile.getParentFile().mkdirs();

            try (FileOutputStream outputStream = new FileOutputStream(targetFile)) {
                writeClassToDisk(outputStream, classWriter);
            }
        }
    }

    protected void writeClassToDisk(OutputStream out, ClassWriter classWriter) throws IOException {
        byte[] bytes = classWriter.toByteArray();
        out.write(bytes);
    }

    protected GeneratorAdapter startConstructor(ClassVisitor classWriter) {
        MethodVisitor defaultConstructor = classWriter.visitMethod(ACC_PUBLIC, CONSTRUCTOR_NAME, DESCRIPTOR_DEFAULT_CONSTRUCTOR, null, null);
        return new GeneratorAdapter(defaultConstructor, ACC_PUBLIC, CONSTRUCTOR_NAME, DESCRIPTOR_DEFAULT_CONSTRUCTOR);
    }

    protected GeneratorAdapter startConstructor(ClassVisitor classWriter, Object...argumentTypes
    ) {
        String descriptor = getConstructorDescriptor(argumentTypes);
        return new GeneratorAdapter(classWriter.visitMethod(ACC_PUBLIC, CONSTRUCTOR_NAME, descriptor, null, null), ACC_PUBLIC, CONSTRUCTOR_NAME, descriptor);
    }

    protected void startClass(ClassVisitor classWriter, String className, Type superType) {
        classWriter.visit(V1_8, ACC_PUBLIC, className, null, superType.getInternalName(), null);
    }

    protected void startClass(ClassWriter classWriter, String className, Type superType, String genericSignature) {
        classWriter.visit(V1_8, ACC_PUBLIC, className, genericSignature, superType.getInternalName(), null);
    }

    protected void invokeConstructor(MethodVisitor cv, Class superClass, Class... argumentTypes)  {
        try {
            Type superType = Type.getType(superClass);
            Type superConstructor = Type.getType(superClass.getDeclaredConstructor(argumentTypes));
            cv.visitMethodInsn(INVOKESPECIAL,
                    superType.getInternalName(),
                    CONSTRUCTOR_NAME,
                    superConstructor.getDescriptor(),
                    false);
        } catch (NoSuchMethodException e) {
            throw new ClassGenerationException("Particle version on compile classpath doesn't match", e);
        }
    }

    protected void invokeStaticMethod(MethodVisitor cv, Class targetType, String methodName, Class... argumentTypes) throws NoSuchMethodException {
        Type packageType = Type.getType(targetType);
        Type getPackageMethod = Type.getType(targetType.getMethod(methodName, argumentTypes));
        cv.visitMethodInsn(INVOKESTATIC,
                packageType.getInternalName(),
                methodName,
                getPackageMethod.getDescriptor(),
                false);
    }

    protected GeneratorAdapter startPublicMethodZeroArgs(ClassWriter classWriter, Class returnType, String methodName) {
        Type methodType = Type.getMethodType(Type.getType(returnType));

        return new GeneratorAdapter(classWriter.visitMethod(ACC_PUBLIC, methodName, methodType.getDescriptor(), null, null), ACC_PUBLIC, methodName, methodType.getDescriptor());
    }

    protected static String getInternalName(String className) {
        String newClassName = className.replace('.', '/');
        if(newClassName.endsWith("[]")) {
            return newClassName.substring(0, newClassName.length()-2);
        }
        return newClassName;
    }

    protected static String getInternalNameForCast(Object type) {
        if(type instanceof Class) {
            Class typeClass = (Class) type;
            if(typeClass.isPrimitive()) {
                typeClass = ReflectionUtils.getWrapperType(typeClass);
            }
            return Type.getInternalName(typeClass);
        }
        else {
            String className = type.toString();
            if(className.endsWith("[]")) {
                return getTypeDescriptor(type);
            }
            else {
                return getInternalName(className);
            }
        }
    }


    protected String getClassFileName(String className) {
        return className.replace('.', File.separatorChar) + ".class";
    }

    protected ClassWriterOutputVisitor newClassWriterOutputVisitor(File compilationDir) {
        return new ClassWriterOutputVisitor() {
            @Override
            public OutputStream visitClass(String className) throws IOException {
                File targetFile = new File(compilationDir, getClassFileName(className)).getCanonicalFile();
                File parentDir = targetFile.getParentFile();
                if (!parentDir.exists() && !parentDir.mkdirs()) {
                    throw new IOException("Cannot create parent directory: " + targetFile.getParentFile());
                }
                return new FileOutputStream(targetFile);
            }

            @Override
            public Optional<File> visitServiceDescriptor(String classname) {
                return Optional.ofNullable(compilationDir).map(root ->
                        new File(root, SoftServiceLoader.META_INF_SERVICES + File.separator + classname)
                );
            }

            @Override
            public Optional<File> visitMetaInfFile(String path) throws IOException {
                return Optional.ofNullable(compilationDir).map(root ->
                        new File(root, "META-INF" + File.separator + path)
                );
            }
        };
    }

    protected void returnVoid(GeneratorAdapter overriddenMethodGenerator) {
        overriddenMethodGenerator.pop();
        overriddenMethodGenerator.visitInsn(RETURN);
    }

    protected GeneratorAdapter visitStaticInitializer(ClassVisitor classWriter) {
        MethodVisitor mv = classWriter.visitMethod(ACC_STATIC, "<clinit>", DESCRIPTOR_DEFAULT_CONSTRUCTOR, null, null);
        return new GeneratorAdapter(mv, ACC_STATIC, "<clinit>", DESCRIPTOR_DEFAULT_CONSTRUCTOR);
    }

    protected GeneratorAdapter startPublicMethod(ClassWriter writer, String methodName, String returnType, String...argumentTypes) {
        return new GeneratorAdapter( writer.visitMethod(
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
     * Write the class to the target directory
     *
     * @param targetDir The target directory
     */
    public void writeTo(File targetDir) throws IOException {
        accept(newClassWriterOutputVisitor(targetDir));
    }

    /**
     * Accept a ClassWriterOutputVisitor to write this writer to disk
     *
     * @param classWriterOutputVisitor The {@link ClassWriterOutputVisitor}
     */
    public abstract void accept(ClassWriterOutputVisitor classWriterOutputVisitor) throws IOException;

    /**
     * Represents a method {@link AnnotationSource} reference
     */
    protected class MethodAnnotationSource extends TypeAnnotationSource{
        final String methodName;
        final Map<String, Object> parameters;

        public MethodAnnotationSource(Object declaringType, String methodName, Map<String, Object> parameters) {
            super(declaringType);
            this.methodName = methodName;
            this.parameters = parameters;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;

            MethodAnnotationSource that = (MethodAnnotationSource) o;

            if (!methodName.equals(that.methodName)) return false;
            return parameters.equals(that.parameters);
        }

        @Override
        public int hashCode() {
            int result = super.hashCode();
            result = 31 * result + methodName.hashCode();
            result = 31 * result + parameters.hashCode();
            return result;
        }
    }
}
