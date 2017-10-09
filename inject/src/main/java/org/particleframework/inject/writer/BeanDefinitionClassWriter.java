package org.particleframework.inject.writer;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.particleframework.context.AbstractBeanDefinitionClass;
import org.particleframework.core.annotation.Internal;
import org.particleframework.inject.BeanDefinition;

import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Writes the bean definition class file to disk
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class BeanDefinitionClassWriter extends AbstractClassFileWriter {


    private final String beanTypeName;
    private final String beanDefinitionName;
    private final String beanDefinitionClassInternalName;
    private final String beanDefinitionClassName;
    private String replaceBeanName;
    private boolean contextScope = false;
    private String replaceBeanDefinitionName;
    private List<TypeAnnotationSource> annotationSourceList = new ArrayList<>();

    public BeanDefinitionClassWriter(String beanTypeName, String beanDefinitionName) {
        this.beanTypeName = beanTypeName;
        this.beanDefinitionName = beanDefinitionName;
        this.beanDefinitionClassName = beanDefinitionName + "Class";
        this.beanDefinitionClassInternalName = getInternalName(beanDefinitionName) + "Class";
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
    public String getBeanDefinitionClassName() {
        return beanDefinitionClassName;
    }

    /**
     * Write the class to the target directory
     *
     * @param targetDir The target directory
     */
    public void writeTo(File targetDir) {
        try {
            ClassWriter classWriter = generateClassBytes();

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

    /**
     * Visit an annotation source for a method
     *
     * @param annotationSource The annotation source
     */
    public BeanDefinitionClassWriter visitAnnotationSource(TypeAnnotationSource annotationSource) {
        if(annotationSource != null && !this.annotationSourceList.contains(annotationSource)) {

            this.annotationSourceList.add(annotationSource);
        }
        return this;
    }

    private ClassWriter generateClassBytes() {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);


        Type superType = Type.getType(AbstractBeanDefinitionClass.class);
        startClass(classWriter, beanDefinitionClassInternalName, superType);

        MethodVisitor cv = startConstructor(classWriter);

        // ALOAD 0
        cv.visitVarInsn(ALOAD, 0);
        // LDC "..class name.."
        cv.visitLdcInsn(beanTypeName);
        cv.visitLdcInsn(beanDefinitionName);

        // INVOKESPECIAL AbstractBeanDefinitionClass.<init> (Ljava/lang/String;)V
        invokeConstructor(cv, AbstractBeanDefinitionClass.class, String.class, String.class);


        // RETURN
        cv.visitInsn(RETURN);
        // MAXSTACK = 2
        // MAXLOCALS = 1
        cv.visitMaxs(2, 1);

        // start method: BeanDefinition load()
        MethodVisitor loadMethod = startPublicMethodZeroArgs(classWriter, BeanDefinition.class, "load");

        // return new BeanDefinition()
        String beanDefinitionTypeName = getInternalName(beanDefinitionName);
        loadMethod.visitTypeInsn(NEW, beanDefinitionTypeName);
        loadMethod.visitInsn(DUP);
        loadMethod.visitMethodInsn(INVOKESPECIAL, beanDefinitionTypeName, "<init>", "()V", false);

        // RETURN
        loadMethod.visitInsn(ARETURN);
        loadMethod.visitMaxs(2, 1);

        // start method: boolean isContextScope()
        if(contextScope) {
            MethodVisitor isContextScopeMethod = startPublicMethodZeroArgs(classWriter, boolean.class, "isContextScope");
            isContextScopeMethod.visitLdcInsn(1);
            isContextScopeMethod.visitInsn(IRETURN);
            isContextScopeMethod.visitMaxs(1, 1);

        }

        if(!annotationSourceList.isEmpty()) {
            GeneratorAdapter annotatedElementMethod = writeGetAnnotatedElementsMethod(classWriter, Type.getType(AbstractBeanDefinitionClass.class), annotationSourceList);
            annotatedElementMethod.visitMaxs(1,1);
        }

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
