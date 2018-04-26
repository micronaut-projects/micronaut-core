/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.inject.writer;

import io.micronaut.context.AbstractBeanDefinitionReference;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.service.ServiceDescriptorGenerator;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Optional;

/**
 * Writes the bean definition class file to disk
 *
 * @author Graeme Rocher
 * @see BeanDefinitionReference
 * @since 1.0
 */
@Internal
public class BeanDefinitionReferenceWriter extends AbstractAnnotationMetadataWriter {

    /**
     * Suffix for reference classes
     */
    public static final String REF_SUFFIX = "Class";

    private final String beanTypeName;
    private final String beanDefinitionName;
    private final String beanDefinitionClassInternalName;
    private final String beanDefinitionReferenceClassName;
    private String replaceBeanName;
    private boolean contextScope = false;
    private String replaceBeanDefinitionName;
    private boolean requiresMethodProcessing;

    public BeanDefinitionReferenceWriter(String beanTypeName, String beanDefinitionName, AnnotationMetadata annotationMetadata) {
        super(beanDefinitionName + REF_SUFFIX, annotationMetadata);
        this.beanTypeName = beanTypeName;
        this.beanDefinitionName = beanDefinitionName;
        this.beanDefinitionReferenceClassName = beanDefinitionName + REF_SUFFIX;
        this.beanDefinitionClassInternalName = getInternalName(beanDefinitionName) + REF_SUFFIX;
    }

    /**
     * Accept an {@link ClassWriterOutputVisitor} to write all generated classes
     *
     * @param outputVisitor The {@link ClassWriterOutputVisitor}
     * @throws IOException If an error occurs
     */
    @Override
    public void accept(ClassWriterOutputVisitor outputVisitor) throws IOException {
        if (annotationMetadataWriter != null) {
            annotationMetadataWriter.accept(outputVisitor);
        }
        try (OutputStream outputStream = outputVisitor.visitClass(getBeanDefinitionQualifiedClassName())) {
            ClassWriter classWriter = generateClassBytes();
            outputStream.write(classWriter.toByteArray());
        }
        outputVisitor.visitServiceDescriptor(
                BeanDefinitionReference.class,
                beanDefinitionReferenceClassName
        );
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
     *
     * @param replaceBeanName The replace bean name
     */
    public void setReplaceBeanName(String replaceBeanName) {
        this.replaceBeanName = replaceBeanName;
    }

    /**
     * The name of the bean this bean replaces
     *
     * @param replaceBeanName The replace bean name
     */
    public void setReplaceBeanDefinitionName(String replaceBeanName) {
        this.replaceBeanDefinitionName = replaceBeanName;
    }

    /**
     * Sets whether the {@link BeanDefinition#requiresMethodProcessing()} returns true
     *
     * @param shouldPreProcess True if they should be pre-processed
     */
    public void setRequiresMethodProcessing(boolean shouldPreProcess) {
        this.requiresMethodProcessing = shouldPreProcess;
    }

    /**
     * Obtains the class name of the bean definition to be written. Java Annotation Processors need
     * this information to create a JavaFileObject using a Filer.
     *
     * @return the class name of the bean definition to be written
     */
    public String getBeanDefinitionQualifiedClassName() {
        String newClassName = beanDefinitionName;
        if (newClassName.endsWith("[]")) {
            newClassName = newClassName.substring(0, newClassName.length() - 2);
        }
        return newClassName + REF_SUFFIX;
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
        if (contextScope) {
            GeneratorAdapter isContextScopeMethod = startPublicMethodZeroArgs(classWriter, boolean.class, "isContextScope");
            isContextScopeMethod.push(true);
            isContextScopeMethod.returnValue();
            isContextScopeMethod.visitMaxs(1, 1);
        }

        //noinspection Duplicates
        if (requiresMethodProcessing) {
            GeneratorAdapter requiresMethodProcessing = startPublicMethod(classWriter, "requiresMethodProcessing", boolean.class.getName());
            requiresMethodProcessing.push(true);
            requiresMethodProcessing.visitInsn(IRETURN);
            requiresMethodProcessing.visitMaxs(1, 1);
            requiresMethodProcessing.visitEnd();
        }

        writeGetAnnotationMetadataMethod(classWriter);

        // start method: getReplacesBeanTypeName()
        writeReplacementIfNecessary(classWriter, replaceBeanName, "getReplacesBeanTypeName");
        writeReplacementIfNecessary(classWriter, replaceBeanDefinitionName, "getReplacesBeanDefinitionName");
        return classWriter;
    }

    private void writeReplacementIfNecessary(ClassWriter classWriter, String replaceBeanName, String method) {
        if (replaceBeanName != null) {
            MethodVisitor getReplacesBeanTypeNameMethod = startPublicMethodZeroArgs(classWriter, String.class, method);
            getReplacesBeanTypeNameMethod.visitLdcInsn(replaceBeanName);
            getReplacesBeanTypeNameMethod.visitInsn(ARETURN);
            getReplacesBeanTypeNameMethod.visitMaxs(1, 1);
        }
    }
}
