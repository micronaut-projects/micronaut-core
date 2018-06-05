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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.annotation.AnnotationMetadataReference;
import io.micronaut.inject.annotation.AnnotationMetadataWriter;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import javax.annotation.Nonnull;

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

    protected final AnnotationMetadataWriter annotationMetadataWriter;
    protected final Type targetClassType;
    protected final AnnotationMetadata annotationMetadata;

    /**
     * @param className          The class name
     * @param annotationMetadata The annotation metadata
     */
    protected AbstractAnnotationMetadataWriter(String className, AnnotationMetadata annotationMetadata) {
        this.targetClassType = getTypeReference(className);
        if (annotationMetadata == AnnotationMetadata.EMPTY_METADATA) {
            this.annotationMetadataWriter = null;
        } else {
            this.annotationMetadataWriter = annotationMetadata instanceof AnnotationMetadataReference ? null : new AnnotationMetadataWriter(className, annotationMetadata);
        }
        this.annotationMetadata = annotationMetadata;
    }

    /**
     * @return The annotation metadata writer
     */
    protected AnnotationMetadataWriter getAnnotationMetadataWriter() {
        return annotationMetadataWriter;
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
     * @param classWriter  The class writer
     * @return The generator adapter
     */
    protected @Nonnull GeneratorAdapter beginAnnotationMetadataMethod(ClassWriter classWriter) {
        return startPublicMethod(classWriter, "getAnnotationMetadata", AnnotationMetadata.class.getName());
    }

    /**
     * @param classWriter The {@link ClassWriter}
     */
    protected void writeAnnotationMetadataStaticInitializer(ClassWriter classWriter) {
        if (!(annotationMetadata instanceof AnnotationMetadataReference)) {

            // write the static initializers for the annotation metadata
            GeneratorAdapter staticInit = visitStaticInitializer(classWriter);
            initializeAnnotationMetadata(staticInit, classWriter);
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

        if (annotationMetadata == AnnotationMetadata.EMPTY_METADATA) {
            staticInit.getStatic(Type.getType(AnnotationMetadata.class), "EMPTY_METADATA", Type.getType(AnnotationMetadata.class));
        } else {
            Type concreteMetadataType = getTypeReference(annotationMetadataWriter.getClassName());
            staticInit.newInstance(concreteMetadataType);
            staticInit.dup();
            staticInit.invokeConstructor(concreteMetadataType, METHOD_DEFAULT_CONSTRUCTOR);
        }

        staticInit.putStatic(targetClassType, FIELD_ANNOTATION_METADATA, annotationMetadataType);
    }

}
