/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.graal.reflect;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Generated;
import io.micronaut.core.graal.GraalReflectionConfigurer;
import io.micronaut.inject.annotation.AnnotationMetadataGenUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.writer.ClassOutputWriter;
import io.micronaut.inject.writer.ClassWriterOutputVisitor;
import io.micronaut.sourcegen.bytecode.ByteCodeWriter;
import io.micronaut.sourcegen.model.AnnotationDef;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.FieldDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.StatementDef;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.micronaut.inject.annotation.AnnotationMetadataGenUtils.getAnnotationMetadataMethodDef;

/**
 * Generates Runtime executed Graal configuration.
 *
 * @author graemerocher
 * @since 3.5.0
 */
final class GraalReflectionMetadataWriter implements ClassOutputWriter {

    private final ClassElement originatingElement;
    private final AnnotationMetadata annotationMetadata;
    private final String className;

    public GraalReflectionMetadataWriter(ClassElement originatingElement,
                                         AnnotationMetadata annotationMetadata) {
        this.annotationMetadata = annotationMetadata;
        this.originatingElement = originatingElement;
        this.className = resolveName(originatingElement);
    }

    private static String resolveName(ClassElement originatingElement) {
        return originatingElement.getPackageName() + ".$" + originatingElement.getSimpleName() + GraalReflectionConfigurer.CLASS_SUFFIX;
    }

    @Override
    public void accept(ClassWriterOutputVisitor classWriterOutputVisitor) throws IOException {
        ClassTypeDef thisType = ClassTypeDef.of(className);
        try (OutputStream outputStream = classWriterOutputVisitor.visitClass(className, originatingElement)) {
            ClassDef.ClassDefBuilder classDefBuilder = ClassDef.builder(className)
                .addAnnotation(AnnotationDef.builder(Generated.class).addMember("service", GraalReflectionConfigurer.class.getName()).build())
                .addSuperinterface(ClassTypeDef.of(GraalReflectionConfigurer.class))
                .addMethod(getAnnotationMetadataMethodDef(thisType, annotationMetadata));

            Map<String, MethodDef> loadTypeMethods = new LinkedHashMap<>();
            // write the static initializers for the annotation metadata
            List<StatementDef> staticInit = new ArrayList<>();
            AnnotationMetadataGenUtils.writeAnnotationDefault(staticInit, thisType, annotationMetadata, loadTypeMethods);

            FieldDef annotationMetadataField = AnnotationMetadataGenUtils.createAnnotationMetadataField(
                thisType,
                annotationMetadata,
                loadTypeMethods
            );

            loadTypeMethods.values().forEach(classDefBuilder::addMethod);

            if (annotationMetadataField != null) {
                classDefBuilder.addField(annotationMetadataField);
                if (!staticInit.isEmpty()) {
                    classDefBuilder.addStaticInitializer(StatementDef.multi(staticInit));
                }
            }
            outputStream.write(new ByteCodeWriter().write(classDefBuilder.build()));
        }
        classWriterOutputVisitor.visitServiceDescriptor(
            GraalReflectionConfigurer.class,
            className,
            originatingElement
        );
    }

}
