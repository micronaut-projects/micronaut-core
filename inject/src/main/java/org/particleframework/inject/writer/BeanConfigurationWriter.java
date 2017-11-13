package org.particleframework.inject.writer;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.particleframework.context.AbstractBeanConfiguration;
import org.particleframework.core.annotation.AnnotationMetadata;
import org.particleframework.core.annotation.Internal;
import org.particleframework.inject.annotation.AnnotationMetadataWriter;

import java.io.File;
import java.io.OutputStream;

/**
 * Writes configuration classes for configuration packages using ASM
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class BeanConfigurationWriter extends AbstractAnnotationMetadataWriter {

    public static final String CLASS_SUFFIX = "$BeanConfiguration";
    private final String packageName;
    private final String configurationClassName;
    private final String configurationClassInternalName;

    public BeanConfigurationWriter(String packageName, AnnotationMetadata annotationMetadata) {
        super(packageName + '.' + CLASS_SUFFIX, annotationMetadata);
        this.packageName = packageName;
        this.configurationClassName = targetClassType.getClassName();
        this.configurationClassInternalName = targetClassType.getInternalName();
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
            AnnotationMetadataWriter annotationMetadataWriter = getAnnotationMetadataWriter();
            if(annotationMetadataWriter != null) {
                annotationMetadataWriter.writeTo(targetDir);
            }
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
            writeAnnotationMetadataStaticInitializer(classWriter);

            writeConstructor(classWriter);
            writeGetAnnotationMetadataMethod(classWriter);

        } catch (NoSuchMethodException e) {
            throw new ClassGenerationException("Error generating configuration class. Incompatible JVM or Particle version?: " + e.getMessage(), e);
        }

        return classWriter;
    }

    private void writeConstructor(ClassWriter classWriter) throws NoSuchMethodException {
        GeneratorAdapter cv = startConstructor(classWriter);

        // ALOAD 0
        cv.loadThis();
        // LDC "..package name.."
        cv.push(packageName);

        // INVOKESPECIAL AbstractBeanConfiguration.<init> (Ljava/lang/Package;)V
        invokeConstructor(cv, AbstractBeanConfiguration.class, String.class);

        // RETURN
        cv.visitInsn(RETURN);
        // MAXSTACK = 2
        // MAXLOCALS = 1
        cv.visitMaxs(2, 1);
    }
}
