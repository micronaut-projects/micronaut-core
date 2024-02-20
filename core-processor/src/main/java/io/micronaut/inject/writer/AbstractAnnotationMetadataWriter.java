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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.annotation.AnnotationMetadataReference;
import io.micronaut.inject.annotation.AnnotationMetadataWriter;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.visitor.VisitorContext;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

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

    /**
     * Field name for empty metadata.
     */
    public static final String FIELD_EMPTY_METADATA = "EMPTY_METADATA";

    protected final Type targetClassType;
    protected final AnnotationMetadata annotationMetadata;
    protected final Map<String, GeneratorAdapter> loadTypeMethods = new HashMap<>();
    protected final Map<String, Integer> defaults = new HashMap<>();
    protected final EvaluatedExpressionProcessor evaluatedExpressionProcessor;
    private final boolean writeAnnotationDefault;

    /**
     * @param className               The class name
     * @param originatingElements     The originating elements
     * @param annotationMetadata      The annotation metadata
     * @param writeAnnotationDefaults Whether to write annotation defaults
     * @param visitorContext          The visitor context
     */
    protected AbstractAnnotationMetadataWriter(
        String className,
        OriginatingElements originatingElements,
        AnnotationMetadata annotationMetadata,
        boolean writeAnnotationDefaults,
        VisitorContext visitorContext) {
        super(originatingElements);
        this.targetClassType = getTypeReferenceForName(className);
        this.annotationMetadata = annotationMetadata.getTargetAnnotationMetadata();
        this.writeAnnotationDefault = writeAnnotationDefaults;
        this.evaluatedExpressionProcessor = new EvaluatedExpressionProcessor(visitorContext, getOriginatingElement());
        this.evaluatedExpressionProcessor.processEvaluatedExpressions(this.annotationMetadata, null);
    }

    /**
     * @param className               The class name
     * @param originatingElement     The originating element
     * @param annotationMetadata      The annotation metadata
     * @param writeAnnotationDefaults Whether to write annotation defaults
     * @param visitorContext          The visitor context
     */
    protected AbstractAnnotationMetadataWriter(
            String className,
            Element originatingElement,
            AnnotationMetadata annotationMetadata,
            boolean writeAnnotationDefaults,
            VisitorContext visitorContext) {
        super(originatingElement);
        this.targetClassType = getTypeReferenceForName(className);
        this.annotationMetadata = annotationMetadata.getTargetAnnotationMetadata();
        this.writeAnnotationDefault = writeAnnotationDefaults;
        this.evaluatedExpressionProcessor = new EvaluatedExpressionProcessor(visitorContext, originatingElement);
        this.evaluatedExpressionProcessor.processEvaluatedExpressions(this.annotationMetadata, null);
    }

    /**
     * @param classWriter The {@link ClassWriter}
     */
    protected void writeGetAnnotationMetadataMethod(ClassWriter classWriter) {
        GeneratorAdapter annotationMetadataMethod = beginAnnotationMetadataMethod(classWriter);
        annotationMetadataMethod.loadThis();

        // in order to save memory of a method doesn't have any annotations of its own but merely references class metadata
        // then we set up an annotation metadata reference from the method to the class (or inherited method) metadata
        AnnotationMetadata annotationMetadata = this.annotationMetadata.getTargetAnnotationMetadata();
        if (annotationMetadata.isEmpty()) {
            annotationMetadataMethod.getStatic(Type.getType(AnnotationMetadata.class), FIELD_EMPTY_METADATA, Type.getType(AnnotationMetadata.class));
        } else if (annotationMetadata instanceof AnnotationMetadataReference reference) {
            String className = reference.getClassName();
            annotationMetadataMethod.getStatic(getTypeReferenceForName(className), AbstractAnnotationMetadataWriter.FIELD_ANNOTATION_METADATA, Type.getType(AnnotationMetadata.class));
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
    protected @NonNull
    GeneratorAdapter beginAnnotationMetadataMethod(ClassWriter classWriter) {
        return startPublicMethod(classWriter, "getAnnotationMetadata", AnnotationMetadata.class.getName());
    }

    /**
     * @param classWriter The {@link ClassWriter}
     */
    protected void writeAnnotationMetadataStaticInitializer(ClassWriter classWriter) {
        writeAnnotationMetadataStaticInitializer(classWriter, defaults);
    }

    /**
     * @param classWriter The {@link ClassWriter}
     * @param defaults    The annotation defaults
     */
    protected void writeAnnotationMetadataStaticInitializer(ClassWriter classWriter, Map<String, Integer> defaults) {
        if (!(annotationMetadata instanceof AnnotationMetadataReference)) {

            // write the static initializers for the annotation metadata
            GeneratorAdapter staticInit = visitStaticInitializer(classWriter);
            staticInit.visitCode();
            if (writeAnnotationDefault) {
                writeAnnotationDefault(classWriter, staticInit, targetClassType, annotationMetadata, defaults, loadTypeMethods);
            }
            staticInit.visitLabel(new Label());
            initializeAnnotationMetadata(staticInit, classWriter, targetClassType, annotationMetadata, defaults, loadTypeMethods);
            staticInit.visitInsn(RETURN);
            staticInit.visitMaxs(1, 1);
            staticInit.visitEnd();
        }
    }

    /**
     * @param classWriter        The class writer
     * @param staticInit         The static init
     * @param targetClassType    The targetClassType
     * @param annotationMetadata The annotation metadata
     * @param defaults           The defaults
     * @param loadTypeMethods    The loadTypeMethods
     */
    public static void writeAnnotationDefault(ClassWriter classWriter,
                                              GeneratorAdapter staticInit,
                                              Type targetClassType,
                                              AnnotationMetadata annotationMetadata,
                                              Map<String, Integer> defaults,
                                              Map<String, GeneratorAdapter> loadTypeMethods) {
        annotationMetadata = annotationMetadata.getTargetAnnotationMetadata();
        if (annotationMetadata.isEmpty()) {
            return;
        }
        if (annotationMetadata instanceof AnnotationMetadataHierarchy annotationMetadataHierarchy) {
            annotationMetadata = annotationMetadataHierarchy.merge();
        }
        if (annotationMetadata instanceof MutableAnnotationMetadata mutableAnnotationMetadata) {
            AnnotationMetadataWriter.writeAnnotationDefaults(
                targetClassType,
                classWriter,
                staticInit,
                mutableAnnotationMetadata,
                defaults,
                loadTypeMethods
            );
        } else {
            throw new IllegalStateException("Unknown annotation metadata: " + annotationMetadata);
        }
    }

    /**
     * @param staticInit         The {@link GeneratorAdapter}
     * @param classWriter        The {@link ClassWriter}
     * @param targetClassType    The targetClassType
     * @param annotationMetadata The annotation metadata
     * @param defaults           The annotation defaults
     * @param loadTypeMethods    The loadTypeMethods
     */
    public static void initializeAnnotationMetadata(GeneratorAdapter staticInit,
                                                    ClassWriter classWriter,
                                                    Type targetClassType,
                                                    AnnotationMetadata annotationMetadata,
                                                    Map<String, Integer> defaults,
                                                    Map<String, GeneratorAdapter> loadTypeMethods) {
        Type annotationMetadataType = Type.getType(AnnotationMetadata.class);
        classWriter.visitField(ACC_PUBLIC | ACC_FINAL | ACC_STATIC, FIELD_ANNOTATION_METADATA, annotationMetadataType.getDescriptor(), null, null);

        annotationMetadata = annotationMetadata.getTargetAnnotationMetadata();
        if (annotationMetadata.isEmpty()) {
            staticInit.getStatic(Type.getType(AnnotationMetadata.class), FIELD_EMPTY_METADATA, Type.getType(AnnotationMetadata.class));
        } else if (annotationMetadata instanceof MutableAnnotationMetadata mutableAnnotationMetadata) {
            AnnotationMetadataWriter.instantiateNewMetadata(
                targetClassType,
                classWriter,
                staticInit,
                mutableAnnotationMetadata,
                defaults,
                loadTypeMethods
            );
        } else if (annotationMetadata instanceof AnnotationMetadataHierarchy annotationMetadataHierarchy) {
            AnnotationMetadataWriter.instantiateNewMetadataHierarchy(
                targetClassType,
                classWriter,
                staticInit,
                annotationMetadataHierarchy,
                defaults,
                loadTypeMethods
            );
        } else {
            throw new IllegalStateException("Unknown annotation metadata: " + annotationMetadata);
        }

        staticInit.putStatic(targetClassType, FIELD_ANNOTATION_METADATA, annotationMetadataType);
    }

}
