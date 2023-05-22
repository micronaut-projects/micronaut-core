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
import io.micronaut.core.graal.GraalReflectionConfigurer;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.AbstractAnnotationMetadataWriter;
import io.micronaut.inject.writer.ClassWriterOutputVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Generates Runtime executed Graal configuration.
 *
 * @author graemerocher
 * @since 3.5.0
 */
final class GraalReflectionMetadataWriter extends AbstractAnnotationMetadataWriter {

    private final String className;
    private final String classInternalName;

    public GraalReflectionMetadataWriter(ClassElement originatingElement,
                                         AnnotationMetadata annotationMetadata,
                                         VisitorContext visitorContext) {
        super(resolveName(originatingElement), originatingElement, annotationMetadata, true, visitorContext);
        this.className = targetClassType.getClassName();
        this.classInternalName = targetClassType.getInternalName();
    }

    private static String resolveName(ClassElement originatingElement) {
        return originatingElement.getPackageName() + ".$" + originatingElement.getSimpleName() + GraalReflectionConfigurer.CLASS_SUFFIX;
    }

    @Override
    public void accept(ClassWriterOutputVisitor classWriterOutputVisitor) throws IOException {
        try (OutputStream outputStream = classWriterOutputVisitor.visitClass(className, getOriginatingElements())) {
            ClassWriter classWriter = generateClassBytes();
            outputStream.write(classWriter.toByteArray());
        }
        classWriterOutputVisitor.visitServiceDescriptor(
                GraalReflectionConfigurer.class,
                className,
                getOriginatingElement()
        );
    }

    private ClassWriter generateClassBytes() {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        startService(
                classWriter,
                getInternalName(GraalReflectionConfigurer.class.getName()),
                classInternalName,
                Type.getType(Object.class),
                getInternalName(GraalReflectionConfigurer.class.getName())
        );
        writeAnnotationMetadataStaticInitializer(classWriter);
        writeConstructor(classWriter);
        writeGetAnnotationMetadataMethod(classWriter);

        for (GeneratorAdapter method : loadTypeMethods.values()) {
            method.visitMaxs(3, 1);
            method.visitEnd();
        }

        return classWriter;
    }

    private void writeConstructor(ClassWriter classWriter) {
        GeneratorAdapter cv = startConstructor(classWriter);

        // ALOAD 0
        cv.loadThis();
        invokeConstructor(cv, Object.class);

        // RETURN
        cv.visitInsn(RETURN);
        // MAXSTACK = 2
        // MAXLOCALS = 1
        cv.visitMaxs(2, 1);
    }
}
