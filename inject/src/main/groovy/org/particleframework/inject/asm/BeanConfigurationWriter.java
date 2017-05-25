package org.particleframework.inject.asm;

import groovyjarjarasm.asm.ClassWriter;
import groovyjarjarasm.asm.MethodVisitor;
import groovyjarjarasm.asm.Type;
import org.particleframework.context.AbstractBeanConfiguration;
import org.particleframework.core.annotation.Internal;

import java.io.File;

/**
 * Writes configuration classes for configuration packages using ASM
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class BeanConfigurationWriter extends AbstractClassFileWriter {


    /**
     * Writes the configuration class for a {@link org.particleframework.context.annotation.Configuration}
     *
     * @param packageName The package name of the configuration
     * @param targetDir   The target directory for compilation
     * @return The generated class name
     */
    public String writeConfiguration(String packageName,
                                     File targetDir) {

        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        String packagePath = getInternalName(packageName);
        String classShortName = "$BeanConfiguration";
        String className = packagePath + '/' + classShortName;

        try {

            Class<AbstractBeanConfiguration> superType = AbstractBeanConfiguration.class;
            Type beanConfigurationType = Type.getType(superType);

            startClass(classWriter, className, beanConfigurationType);
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

            writeClassToDisk(targetDir, classWriter, className);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        return packageName + '.' + classShortName;
    }

}
