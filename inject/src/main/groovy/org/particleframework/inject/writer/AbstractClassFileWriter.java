package org.particleframework.inject.writer;

import groovyjarjarasm.asm.*;
import org.particleframework.core.reflect.ReflectionUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

/**
 * Abstract class that writes generated classes to disk and provides convenience methods for building classes
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class AbstractClassFileWriter implements Opcodes {

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
//            if(className.endsWith("[]")) {
//                internalName = '[' + internalName;
//            }
            return Type.getObjectType(internalName);
        }
        else {
            throw new IllegalArgumentException("Type reference should be a Class or a String representing the class name");
        }
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
            throw new IllegalArgumentException("Type reference should be a Class or a String representing the class name");
        }
    }
    protected static String getTypeDescriptor(String className, String... genericTypes) {
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
    protected void writeClassToDisk(File targetDir, ClassWriter classWriter, String className) {
        if(targetDir != null) {

            byte[] bytes = classWriter.toByteArray();
            String fileName = className.replace('.','/') + ".class";
            File targetFile = new File(targetDir, fileName);
            targetFile.getParentFile().mkdirs();

            try (FileOutputStream outputStream = new FileOutputStream(targetFile)) {
                outputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    protected MethodVisitor startConstructor(ClassVisitor classWriter) {
        return classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
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
                    "<init>",
                    superConstructor.getDescriptor(),
                    false);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Particle version on compile classpath doesn't match");
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

    protected MethodVisitor startPublicMethodZeroArgs(ClassWriter classWriter, Class returnType, String methodName) {
        return classWriter.visitMethod(ACC_PUBLIC, methodName, Type.getMethodType(Type.getType(returnType), new Type[0]).getDescriptor(), null, null);
    }

    protected static String getInternalName(String className) {
        String newClassName = className.replace('.', '/');
        if(newClassName.endsWith("[]")) {
            return newClassName.substring(0, newClassName.length()-2);
        }
        return newClassName;
    }

    protected String getInternalNameForCast(Object type) {
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
}
