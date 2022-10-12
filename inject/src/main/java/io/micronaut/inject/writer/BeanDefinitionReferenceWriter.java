/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.inject.writer;

import io.micronaut.context.AbstractInitializableBeanDefinitionReference;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.ConfigurationReader;
import io.micronaut.context.annotation.DefaultScope;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.DefaultArgument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.AdvisedBeanType;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.annotation.AnnotationMetadataReference;
import jakarta.inject.Singleton;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Writes the bean definition class file to disk.
 *
 * @author Graeme Rocher
 * @author Denis Stepanov
 * @see BeanDefinitionReference
 * @since 1.0
 */
@Internal
public class BeanDefinitionReferenceWriter extends AbstractAnnotationMetadataWriter {

    /**
     * Suffix for reference classes.
     */
    public static final String REF_SUFFIX = "$Reference";

    private static final org.objectweb.asm.commons.Method BEAN_DEFINITION_REF_CLASS_CONSTRUCTOR = new org.objectweb.asm.commons.Method(CONSTRUCTOR_NAME, getConstructorDescriptor(
            String.class, // beanTypeName
            String.class, // beanDefinitionTypeName
            AnnotationMetadata.class, // annotationMetadata
            boolean.class, // isPrimary
            boolean.class, // isContextScope
            boolean.class, // isConditional
            boolean.class, // isContainerType
            boolean.class, // isSingleton
            boolean.class, // isConfigurationProperties
            boolean.class,  // hasExposedTypes
            boolean.class  // requiresMethodProcessing
    ));

    private final String beanTypeName;
    private final String beanDefinitionName;
    private final String beanDefinitionClassInternalName;
    private final String beanDefinitionReferenceClassName;
    private final Type interceptedType;
    private final Type providedType;
    private boolean contextScope = false;
    private boolean requiresMethodProcessing;

    /**
     * Default constructor.
     *
     * @param visitor      The visitor
     */
    public BeanDefinitionReferenceWriter(BeanDefinitionVisitor visitor) {
        super(
                visitor.getBeanDefinitionName() + REF_SUFFIX,
                visitor,
                visitor.getAnnotationMetadata(),
                true);
        this.providedType = visitor.getProvidedType();
        this.beanTypeName = visitor.getBeanTypeName();
        this.beanDefinitionName = visitor.getBeanDefinitionName();
        this.beanDefinitionReferenceClassName = beanDefinitionName + REF_SUFFIX;
        this.beanDefinitionClassInternalName = getInternalName(beanDefinitionName) + REF_SUFFIX;
        this.interceptedType = visitor.getInterceptedType().orElse(null);
    }

    /**
     * Accept an {@link ClassWriterOutputVisitor} to write all generated classes.
     *
     * @param outputVisitor The {@link ClassWriterOutputVisitor}
     * @throws IOException If an error occurs
     */
    @Override
    public void accept(ClassWriterOutputVisitor outputVisitor) throws IOException {
        try (OutputStream outputStream = outputVisitor.visitClass(getBeanDefinitionQualifiedClassName(), getOriginatingElements())) {
            ClassWriter classWriter = generateClassBytes();
            outputStream.write(classWriter.toByteArray());
        }
        outputVisitor.visitServiceDescriptor(
                BeanDefinitionReference.class,
                beanDefinitionReferenceClassName,
                getOriginatingElement()
        );
    }

    /**
     * Set whether the bean should be in context scope.
     *
     * @param contextScope The context scope
     */
    public void setContextScope(boolean contextScope) {
        this.contextScope = contextScope;
    }

    /**
     * Sets whether the {@link BeanDefinition#requiresMethodProcessing()} returns true.
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

        Type superType = Type.getType(AbstractInitializableBeanDefinitionReference.class);
        String[] interfaceInternalNames;
        if (interceptedType != null) {
            interfaceInternalNames = new String[] { Type.getType(AdvisedBeanType.class).getInternalName() };
        } else {
            interfaceInternalNames = StringUtils.EMPTY_STRING_ARRAY;
        }
        startService(
                classWriter,
                BeanDefinitionReference.class.getName(),
                beanDefinitionClassInternalName,
                superType,
                interfaceInternalNames
        );
        Type beanDefinitionType = getTypeReferenceForName(beanDefinitionName);
        writeAnnotationMetadataStaticInitializer(classWriter);

        GeneratorAdapter cv = startConstructor(classWriter);

        cv.loadThis();
        // 1: beanTypeName
        cv.push(beanTypeName);
        // 2: beanDefinitionTypeName
        cv.push(beanDefinitionName);
        // 3: annotationMetadata
        if (annotationMetadata == AnnotationMetadata.EMPTY_METADATA || annotationMetadata.isEmpty()) {
            cv.getStatic(Type.getType(AnnotationMetadata.class), "EMPTY_METADATA", Type.getType(AnnotationMetadata.class));
        } else if (annotationMetadata instanceof AnnotationMetadataReference) {
            AnnotationMetadataReference reference = (AnnotationMetadataReference) annotationMetadata;
            String className = reference.getClassName();
            cv.getStatic(getTypeReferenceForName(className), AbstractAnnotationMetadataWriter.FIELD_ANNOTATION_METADATA, Type.getType(AnnotationMetadata.class));
        } else {
            cv.getStatic(targetClassType, AbstractAnnotationMetadataWriter.FIELD_ANNOTATION_METADATA, Type.getType(AnnotationMetadata.class));
        }
        // 4: isPrimary
        cv.push(annotationMetadata.hasDeclaredStereotype(Primary.class));
        // 5: isContextScope
        cv.push(contextScope);
        // 6: isConditional
        cv.push(annotationMetadata.hasStereotype(Requires.class));
        // 7: isContainerType
        cv.push(providedType.getSort() == Type.ARRAY || DefaultArgument.CONTAINER_TYPES.stream().anyMatch(clazz -> clazz.getName().equals(beanTypeName)));
        // 8: isSingleton
        cv.push(
                annotationMetadata.hasDeclaredStereotype(AnnotationUtil.SINGLETON) ||
                        (!annotationMetadata.hasDeclaredStereotype(AnnotationUtil.SCOPE) &&
                                annotationMetadata.hasDeclaredStereotype(DefaultScope.class) &&
                                annotationMetadata.stringValue(DefaultScope.class)
                                        .map(t -> t.equals(Singleton.class.getName()) || t.equals(AnnotationUtil.SINGLETON))
                                        .orElse(false))
        );
        // 9: isConfigurationProperties
        cv.push(annotationMetadata.hasDeclaredStereotype(ConfigurationReader.class));
        // 10: hasExposedTypes
        cv.push(
                annotationMetadata.hasDeclaredAnnotation(Bean.class)
                        && annotationMetadata.stringValues(Bean.class, "typed").length > 0
        );
        // 10: requiresMethodProcessing
        cv.push(requiresMethodProcessing);
        // (...)
        cv.invokeConstructor(
                Type.getType(AbstractInitializableBeanDefinitionReference.class),
                BEAN_DEFINITION_REF_CLASS_CONSTRUCTOR
        );
        // RETURN
        cv.visitInsn(RETURN);
        // MAXSTACK = 2
        // MAXLOCALS = 1
        cv.visitMaxs(2, 1);

        // start method: BeanDefinition load()
        GeneratorAdapter loadMethod = startPublicMethodZeroArgs(classWriter, BeanDefinition.class, "load");
        // return new BeanDefinition()
        pushNewInstance(loadMethod, beanDefinitionType);
        // RETURN
        loadMethod.returnValue();
        loadMethod.visitMaxs(2, 1);

        // start method: Class getBeanDefinitionType()
        GeneratorAdapter getBeanDefinitionType = startPublicMethodZeroArgs(classWriter, Class.class, "getBeanDefinitionType");
        getBeanDefinitionType.push(beanDefinitionType);
        getBeanDefinitionType.returnValue();
        getBeanDefinitionType.visitMaxs(2, 1);

        // start method: Class getBeanType()
        GeneratorAdapter getBeanType = startPublicMethodZeroArgs(classWriter, Class.class, "getBeanType");
        getBeanType.push(providedType);
        getBeanType.returnValue();
        getBeanType.visitMaxs(2, 1);

        if (interceptedType != null) {
            super.implementInterceptedTypeMethod(interceptedType, classWriter);
        }
        for (GeneratorAdapter generatorAdapter : loadTypeMethods.values()) {
            generatorAdapter.visitMaxs(3, 1);
        }

        return classWriter;
    }

}
