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


    public String writeBeanDefinitionClass(File targetDir, String beanDefinitionName, boolean isContextScope) {

        String newBeanDefinitionClassName = getInternalName(beanDefinitionName) + "Class";
        try {
            ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);


            Type superType = Type.getType(AbstractBeanDefinitionClass.class);
            startClass(classWriter, newBeanDefinitionClassName, superType);

            MethodVisitor cv = startConstructor(classWriter);

            // ALOAD 0
            cv.visitVarInsn(ALOAD, 0);
            // LDC "..class name.."
            cv.visitLdcInsn(beanDefinitionName);

            // INVOKESPECIAL AbstractBeanDefinitionClass.<init> (Ljava/lang/String;)V
            invokeConstructor(cv, AbstractBeanDefinitionClass.class, String.class);


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
            if(isContextScope) {
                MethodVisitor isContextScopeMethod = startPublicMethodZeroArgs(classWriter, boolean.class, "isContextScope");
                isContextScopeMethod.visitLdcInsn(1);
                isContextScopeMethod.visitInsn(IRETURN);
                isContextScopeMethod.visitMaxs(1, 1);

            }

            writeClassToDisk(targetDir, classWriter, newBeanDefinitionClassName);

        } catch (Throwable e) {
            throw new ClassGenerationException("Error generating bean definition class for bean definition ["+beanDefinitionName+"]: " + e.getMessage(), e);
        }
        return  beanDefinitionName + "Class";
    }

}
