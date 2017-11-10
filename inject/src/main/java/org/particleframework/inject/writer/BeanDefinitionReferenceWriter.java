package org.particleframework.inject.writer;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.particleframework.context.AbstractBeanDefinitionReference;
import org.particleframework.core.annotation.AnnotationMetadata;
import org.particleframework.core.annotation.Internal;
import org.particleframework.inject.BeanDefinition;
import org.particleframework.inject.annotation.AnnotationMetadataWriter;

import java.io.File;
import java.io.OutputStream;

/**
 * Writes the bean definition class file to disk
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class BeanDefinitionReferenceWriter extends AbstractAnnotationMetadataWriter {

    public static final String REF_SUFFIX = "Class";

    private final String beanTypeName;
    private final String beanDefinitionName;
    private final String beanDefinitionClassInternalName;
    private final String beanDefinitionReferenceClassName;
    private String replaceBeanName;
    private boolean contextScope = false;
    private String replaceBeanDefinitionName;

    public BeanDefinitionReferenceWriter(String beanTypeName, String beanDefinitionName, AnnotationMetadata annotationMetadata) {
        super(beanDefinitionName  + REF_SUFFIX, annotationMetadata);
        this.beanTypeName = beanTypeName;
        this.beanDefinitionName = beanDefinitionName;
        this.beanDefinitionReferenceClassName = beanDefinitionName + REF_SUFFIX;
        this.beanDefinitionClassInternalName = getInternalName(beanDefinitionName) + REF_SUFFIX;
    }

    /**
     * Set whether the bean should be in context scope
     *
     * @param contextScope The context scope
     */
    public void setContextScope(boolean contextScope) {
        this.contextScope = contextScope;
    }

    /**
     * The name of the bean this bean replaces
     * @param replaceBeanName The replace bean name
     */
    public void setReplaceBeanName(String replaceBeanName) {
        this.replaceBeanName = replaceBeanName;
    }

    /**
     * The name of the bean this bean replaces
     * @param replaceBeanName The replace bean name
     */
    public void setReplaceBeanDefinitionName(String replaceBeanName) {
        this.replaceBeanDefinitionName = replaceBeanName;
    }

    /**
     * @return Obtains the class internal name of the bean definition to be written
     */
    public String getBeanDefinitionClassInternalName() {
        return beanDefinitionClassInternalName;
    }

    /**
     * Obtains the class name of the bean definition to be written. Java Annotation Processors need
     * this information to create a JavaFileObject using a Filer.
     *
     * @return the class name of the bean definition to be written
     */
    public String getBeanDefinitionQualifiedClassName() {
        String newClassName = beanDefinitionName;
        if(newClassName.endsWith("[]")) {
            newClassName = newClassName.substring(0, newClassName.length()-2);
        }
        return newClassName + "Class";
    }

    /**
     * @return Obtains the class name of the bean definition class to be written
     */
    public String getBeanDefinitionReferenceClassName() {
        return beanDefinitionReferenceClassName;
    }

    /**
     * Write the class to the target directory
     *
     * @param targetDir The target directory
     */
    public void writeTo(File targetDir) {
        try {
            ClassWriter classWriter = generateClassBytes();

            AnnotationMetadataWriter annotationMetadataWriter = getAnnotationMetadataWriter();
            if(annotationMetadataWriter != null) {
                annotationMetadataWriter.writeTo(targetDir);
            }
            writeClassToDisk(targetDir, classWriter, beanDefinitionClassInternalName);

        } catch (Throwable e) {
            throw new ClassGenerationException("Error generating bean definition class for bean definition ["+beanDefinitionName+"]: " + e.getMessage(), e);
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
            throw new ClassGenerationException("Error generating bean definition class for bean definition ["+beanDefinitionName+"]: " + e.getMessage(), e);
        }
    }

    private ClassWriter generateClassBytes() {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);


        Type superType = Type.getType(AbstractBeanDefinitionReference.class);
        startClass(classWriter, beanDefinitionClassInternalName, superType);
        Type beanType = getTypeReference(beanDefinitionName);
        writeAnnotationMetadataStaticInitializer(classWriter);


        GeneratorAdapter cv = startConstructor(classWriter);

        // ALOAD 0
        cv.loadThis();
        // LDC "..class name.."
        cv.push(beanTypeName);
        cv.push(beanDefinitionName);

        // INVOKESPECIAL AbstractBeanDefinitionReference.<init> (Ljava/lang/String;)V
        invokeConstructor(cv, AbstractBeanDefinitionReference.class, String.class, String.class);


        // RETURN
        cv.visitInsn(RETURN);
        // MAXSTACK = 2
        // MAXLOCALS = 1
        cv.visitMaxs(2, 1);

        // start method: BeanDefinition load()
        GeneratorAdapter loadMethod = startPublicMethodZeroArgs(classWriter, BeanDefinition.class, "load");

        // return new BeanDefinition()
        loadMethod.newInstance(beanType);
        loadMethod.dup();
        loadMethod.invokeConstructor(beanType, METHOD_DEFAULT_CONSTRUCTOR);

        // RETURN
        loadMethod.returnValue();
        loadMethod.visitMaxs(2, 1);

        // start method: boolean isContextScope()
        if(contextScope) {
            GeneratorAdapter isContextScopeMethod = startPublicMethodZeroArgs(classWriter, boolean.class, "isContextScope");
            isContextScopeMethod.push(true);
            isContextScopeMethod.returnValue();
            isContextScopeMethod.visitMaxs(1, 1);

        }
        writeGetAnnotationMetadataMethod(classWriter);

        // start method: getReplacesBeanTypeName()
        writeReplacementIfNecessary(classWriter, replaceBeanName, "getReplacesBeanTypeName");
        writeReplacementIfNecessary(classWriter, replaceBeanDefinitionName, "getReplacesBeanDefinitionName");
        return classWriter;
    }


    private void writeReplacementIfNecessary(ClassWriter classWriter, String replaceBeanName, String method) {
        if(replaceBeanName != null) {
            MethodVisitor getReplacesBeanTypeNameMethod = startPublicMethodZeroArgs(classWriter, String.class, method);
            getReplacesBeanTypeNameMethod.visitLdcInsn(replaceBeanName);
            getReplacesBeanTypeNameMethod.visitInsn(ARETURN);
            getReplacesBeanTypeNameMethod.visitMaxs(1, 1);
        }
    }

}
