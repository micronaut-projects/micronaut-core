/*
 * Copyright 2017-2019 original authors
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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.annotation.AnnotationMetadataReference;
import io.micronaut.inject.annotation.AnnotationMetadataWriter;
import io.micronaut.inject.annotation.DefaultAnnotationMetadata;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

/**
 * Base class for types that also write {@link io.micronaut.core.annotation.AnnotationMetadata}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public abstract class AbstractAnnotationMetadataWriter extends AbstractClassFileWriter {

    /**
     * Field name for annotation metadata.
     */
    public static final String FIELD_ANNOTATION_METADATA = "$ANNOTATION_METADATA";

    protected final Type targetClassType;
    protected final AnnotationMetadata annotationMetadata;
    protected final Map<String, GeneratorAdapter> loadTypeMethods = new HashMap<>();
    private final boolean writeAnnotationDefault;

    /**
     * @param className               The class name
     * @param annotationMetadata      The annotation metadata
     * @param writeAnnotationDefaults Whether to write annotation defaults
     */
    protected AbstractAnnotationMetadataWriter(
            String className,
            AnnotationMetadata annotationMetadata,
            boolean writeAnnotationDefaults) {
        this.targetClassType = getTypeReference(className);
        this.annotationMetadata = annotationMetadata;
        this.writeAnnotationDefault = writeAnnotationDefaults;
    }

    /**
     * @param classWriter The {@link ClassWriter}
     */
    protected void writeGetAnnotationMetadataMethod(ClassWriter classWriter) {
        GeneratorAdapter annotationMetadataMethod = beginAnnotationMetadataMethod(classWriter);
        annotationMetadataMethod.loadThis();

        // in order to save memory of a method doesn't have any annotations of its own but merely references class metadata
        // then we setup an annotation metadata reference from the method to the class (or inherited method) metadata
        if (annotationMetadata == AnnotationMetadata.EMPTY_METADATA) {
            annotationMetadataMethod.getStatic(Type.getType(AnnotationMetadata.class), "EMPTY_METADATA", Type.getType(AnnotationMetadata.class));
        } else if (annotationMetadata instanceof AnnotationMetadataReference) {
            AnnotationMetadataReference reference = (AnnotationMetadataReference) annotationMetadata;
            String className = reference.getClassName();
            annotationMetadataMethod.getStatic(getTypeReference(className), AbstractAnnotationMetadataWriter.FIELD_ANNOTATION_METADATA, Type.getType(AnnotationMetadata.class));
        } else {
            annotationMetadataMethod.getStatic(targetClassType, AbstractAnnotationMetadataWriter.FIELD_ANNOTATION_METADATA, Type.getType(AnnotationMetadata.class));
        }
        annotationMetadataMethod.returnValue();
        annotationMetadataMethod.visitMaxs(1, 1);
        annotationMetadataMethod.visitEnd();
    }

    /**
     * Returns the generator adaptor for the method that resolves the annotation metadata.
     *
     * @param classWriter The class writer
     * @return The generator adapter
     */
    protected @Nonnull
    GeneratorAdapter beginAnnotationMetadataMethod(ClassWriter classWriter) {
        return startPublicMethod(classWriter, "getAnnotationMetadata", AnnotationMetadata.class.getName());
    }

    /**
     * @param classWriter The {@link ClassWriter}
     */
    protected void writeAnnotationMetadataStaticInitializer(ClassWriter classWriter) {
        if (!(annotationMetadata instanceof AnnotationMetadataReference)) {

            // write the static initializers for the annotation metadata
            GeneratorAdapter staticInit = visitStaticInitializer(classWriter);
            staticInit.visitCode();
            staticInit.visitLabel(new Label());
            initializeAnnotationMetadata(staticInit, classWriter);
            if (writeAnnotationDefault && annotationMetadata instanceof DefaultAnnotationMetadata) {
                DefaultAnnotationMetadata dam = (DefaultAnnotationMetadata) annotationMetadata;
                AnnotationMetadataWriter.writeAnnotationDefaults(
                        targetClassType,
                        classWriter,
                        staticInit,
                        dam,
                        loadTypeMethods
                );

            }
            staticInit.visitInsn(RETURN);
            staticInit.visitMaxs(1, 1);
            staticInit.visitEnd();
        }
    }

    /**
     * @param staticInit  The {@link GeneratorAdapter}
     * @param classWriter The {@link ClassWriter}
     */
    protected void initializeAnnotationMetadata(GeneratorAdapter staticInit, ClassWriter classWriter) {
        Type annotationMetadataType = Type.getType(AnnotationMetadata.class);
        classWriter.visitField(ACC_PUBLIC | ACC_FINAL | ACC_STATIC, FIELD_ANNOTATION_METADATA, annotationMetadataType.getDescriptor(), null, null);

        if (annotationMetadata instanceof DefaultAnnotationMetadata) {
            AnnotationMetadataWriter.instantiateNewMetadata(
                    targetClassType,
                    classWriter,
                    staticInit,
                    (DefaultAnnotationMetadata) annotationMetadata,
                    loadTypeMethods
            );
        } else if (annotationMetadata instanceof AnnotationMetadataHierarchy) {
            AnnotationMetadataWriter.instantiateNewMetadataHierarchy(
                    targetClassType,
                    classWriter,
                    staticInit,
                    (AnnotationMetadataHierarchy) annotationMetadata,
                    loadTypeMethods
            );
        } else {
            staticInit.getStatic(Type.getType(AnnotationMetadata.class), "EMPTY_METADATA", Type.getType(AnnotationMetadata.class));
        }

        staticInit.putStatic(targetClassType, FIELD_ANNOTATION_METADATA, annotationMetadataType);
    }

}
