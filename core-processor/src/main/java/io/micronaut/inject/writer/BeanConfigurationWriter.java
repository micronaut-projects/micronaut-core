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

import io.micronaut.context.AbstractBeanConfiguration;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Generated;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.BeanConfiguration;
import io.micronaut.inject.annotation.AnnotationMetadataGenUtils;
import io.micronaut.inject.ast.Element;
import io.micronaut.sourcegen.bytecode.ByteCodeWriter;
import io.micronaut.sourcegen.model.AnnotationDef;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.FieldDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.StatementDef;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.micronaut.inject.annotation.AnnotationMetadataGenUtils.getAnnotationMetadataMethodDef;

/**
 * Writes configuration classes for configuration packages using ASM.
 *
 * @author Graeme Rocher
 * @see BeanConfiguration
 * @see io.micronaut.context.annotation.Configuration
 * @since 1.0
 */
@Internal
public class BeanConfigurationWriter implements ClassOutputWriter {

    /**
     * Suffix for generated configuration classes.
     */
    public static final String CLASS_SUFFIX = "$BeanConfiguration";
    private final String packageName;
    private final String configurationClassName;
    private final Element originatingElement;
    private final AnnotationMetadata annotationMetadata;

    /**
     * @param packageName        The package name
     * @param originatingElement The originating element
     * @param annotationMetadata The annotation metadata
     */
    public BeanConfigurationWriter(
        String packageName,
        Element originatingElement,
        AnnotationMetadata annotationMetadata) {
        this.packageName = packageName;
        this.configurationClassName = packageName + '.' + CLASS_SUFFIX;
        this.originatingElement = originatingElement;
        this.annotationMetadata = annotationMetadata;
    }

    @Override
    public void accept(ClassWriterOutputVisitor classWriterOutputVisitor) throws IOException {
        try (OutputStream outputStream = classWriterOutputVisitor.visitClass(configurationClassName, originatingElement)) {
            outputStream.write(generateClassBytes());
        }
        classWriterOutputVisitor.visitServiceDescriptor(
            BeanConfiguration.class,
            configurationClassName,
            originatingElement
        );
    }

    private byte[] generateClassBytes() {
        ClassTypeDef targetType = ClassTypeDef.of(configurationClassName);

        ClassDef.ClassDefBuilder configurationClassBuilder = ClassDef.builder(configurationClassName)
            .superclass(ClassTypeDef.of(AbstractBeanConfiguration.class))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(AnnotationDef.builder(Generated.class).addMember("service", BeanConfiguration.class.getName()).build());

        ClassDef configurationClass = configurationClassBuilder
            .addMethod(MethodDef.constructor().addModifiers(Modifier.PUBLIC).build((aThis, methodParameters)
                -> aThis.superRef().invokeConstructor(ExpressionDef.constant(packageName))))
            .addMethod(getAnnotationMetadataMethodDef(targetType, annotationMetadata))
            .build();


        Map<String, MethodDef> loadTypeMethods = new LinkedHashMap<>();
        // write the static initializers for the annotation metadata
        List<StatementDef> staticInit = new ArrayList<>();
        AnnotationMetadataGenUtils.writeAnnotationDefault(staticInit, targetType, annotationMetadata, loadTypeMethods);

        FieldDef annotationMetadataField = AnnotationMetadataGenUtils.createAnnotationMetadataField(
            targetType,
            annotationMetadata,
            loadTypeMethods
        );

        loadTypeMethods.values().forEach(configurationClassBuilder::addMethod);

        if (annotationMetadataField != null) {
            configurationClassBuilder.addField(annotationMetadataField);
            if (!staticInit.isEmpty()) {
                configurationClassBuilder.addStaticInitializer(StatementDef.multi(staticInit));
            }
        }

        return new ByteCodeWriter().write(configurationClass);
    }

}
