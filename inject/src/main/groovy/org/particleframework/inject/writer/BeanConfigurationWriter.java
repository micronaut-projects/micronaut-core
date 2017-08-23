package org.particleframework.inject.writer;

import groovyjarjarasm.asm.ClassWriter;
import groovyjarjarasm.asm.MethodVisitor;
import groovyjarjarasm.asm.Type;
import org.particleframework.context.AbstractBeanConfiguration;
import org.particleframework.core.annotation.Internal;

import java.io.File;
import java.io.OutputStream;

/**
 * Writes configuration classes for configuration packages using ASM
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class BeanConfigurationWriter extends AbstractClassFileWriter {

    private final String packageName;
    private final String configurationClassName;
    private final String configurationClassInternalName;

    public BeanConfigurationWriter(String packageName) {
        this.packageName = packageName;
        String classShortName = "$BeanConfiguration";
        this.configurationClassName = packageName + '.' + classShortName;
        this.configurationClassInternalName = getInternalName(configurationClassName);
    }

    /**
     * @return The configuration class name
     */
    public String getConfigurationClassName() {
        return configurationClassName;
    }

    /**
     * Writes the configuration class for a {@link org.particleframework.context.annotation.Configuration}
     *
     * @param targetDir   The target directory for compilation
     */
    public void writeTo(File targetDir) {
        try {
            ClassWriter classWriter = generateClassBytes();
            writeClassToDisk(targetDir, classWriter, configurationClassName);
        } catch (Throwable e) {
            throw new ClassGenerationException("Error generating configuration class. I/O exception occurred: " + e.getMessage(), e);
        }
    }

    /**
     * Write the class to the output stream, such a JavaFileObject created from a java annotation processor Filer object
     *
     * @param outputStream the output stream pointing to the target class file
     */
    public void writeTo(OutputStream outputStream) {
        try {
            ClassWriter classWriter = generateClassBytes();
            writeClassToDisk(outputStream, classWriter);
        } catch (Throwable e) {
            throw new ClassGenerationException("Error generating configuration class. I/O exception occurred: " + e.getMessage(), e);
        }
    }

    private ClassWriter generateClassBytes() {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);

        try {

            Class<AbstractBeanConfiguration> superType = AbstractBeanConfiguration.class;
            Type beanConfigurationType = Type.getType(superType);

            startClass(classWriter, configurationClassInternalName, beanConfigurationType);
            MethodVisitor cv = startConstructor(classWriter);

            // ALOAD 0
            cv.visitVarInsn(ALOAD, 0);
            // LDC "..package name.."
            cv.visitLdcInsn(packageName);

            // INVOKESTATIC java/lang/Package.getPackage (Ljava/lang/String;)Ljava/lang/Package;
            invokeStaticMethod(cv, Package.class, "getPackage", String.class);

            // INVOKESPECIAL AbstractBeanConfiguration.<init> (Ljava/lang/Package;)V
            invokeConstructor(cv, AbstractBeanConfiguration.class, Package.class);

            // RETURN
            cv.visitInsn(RETURN);
            // MAXSTACK = 2
            // MAXLOCALS = 1
            cv.visitMaxs(2, 1);

        } catch (NoSuchMethodException e) {
            throw new ClassGenerationException("Error generating configuration class. Incompatible JVM or Particle version?: " + e.getMessage(), e);
        }

        return classWriter;
    }
}
