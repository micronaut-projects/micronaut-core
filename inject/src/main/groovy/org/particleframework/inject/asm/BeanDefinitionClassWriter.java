package org.particleframework.inject.asm;

import groovyjarjarasm.asm.ClassWriter;
import groovyjarjarasm.asm.MethodVisitor;
import groovyjarjarasm.asm.Type;
import org.particleframework.context.AbstractBeanDefinitionClass;
import org.particleframework.core.annotation.Internal;
import org.particleframework.inject.BeanDefinition;

import java.io.File;

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
     * @return Obtains the class internal name of the bean definition to be written
     */
    public String getBeanDefinitionClassInternalName() {
        return beanDefinitionClassInternalName;
    }

    /**
     * @return Obtains the class name of the bean definition class to be written
     */
    public String getBeanDefinitionClassName() {
        return beanDefinitionClassName;
    }

    /**
     * Writer the class to the target directory
     *
     * @param targetDir The target directory
     */
    public void writeTo(File targetDir) {
        try {
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

            // start method: getReplacesBeanTypeName()
            if(replaceBeanName != null) {
                MethodVisitor getReplacesBeanTypeNameMethod = startPublicMethodZeroArgs(classWriter, String.class, "getReplacesBeanTypeName");
                getReplacesBeanTypeNameMethod.visitLdcInsn(replaceBeanName);
                getReplacesBeanTypeNameMethod.visitInsn(ARETURN);
                getReplacesBeanTypeNameMethod.visitMaxs(1, 1);
            }

            writeClassToDisk(targetDir, classWriter, beanDefinitionClassInternalName);

        } catch (Throwable e) {
            throw new ClassGenerationException("Error generating bean definition class for bean definition ["+beanDefinitionName+"]: " + e.getMessage(), e);
        }
    }

}
