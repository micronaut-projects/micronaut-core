package org.particleframework.inject.asm;

import groovyjarjarasm.asm.ClassWriter;
import groovyjarjarasm.asm.MethodVisitor;
import groovyjarjarasm.asm.Opcodes;
import groovyjarjarasm.asm.Type;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Abstract class that writes generated classes to disk and provides convenience methods for building classes
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class AbstractClassFileWriter implements Opcodes {

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
            String fileName = className + ".class";
            File targetFile = new File(targetDir, fileName);
            targetFile.getParentFile().mkdirs();

            try (FileOutputStream outputStream = new FileOutputStream(targetFile)) {
                outputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    protected MethodVisitor startConstructor(ClassWriter classWriter) {
        return classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
    }

    protected void startClass(ClassWriter classWriter, String className, Type superType) {
        classWriter.visit(V1_8, ACC_PUBLIC, className, null, superType.getInternalName(), null);
    }

    protected void invokeConstructor(MethodVisitor cv, Class superClass, Class... argumentTypes) throws NoSuchMethodException {
        Type superType = Type.getType(superClass);
        Type superConstructor = Type.getType(superClass.getDeclaredConstructor(argumentTypes));
        cv.visitMethodInsn(INVOKESPECIAL,
                superType.getInternalName(),
                "<init>",
                superConstructor.getDescriptor(),
                false);
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
}
