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

package io.micronaut.inject.annotation;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.inject.writer.AbstractClassFileWriter;
import io.micronaut.inject.writer.ClassGenerationException;
import io.micronaut.inject.writer.ClassWriterOutputVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Responsible for writing class files that are instances of {@link AnnotationMetadata}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class AnnotationMetadataWriter extends AbstractClassFileWriter {

    private static final Type TYPE_DEFAULT_ANNOTATION_METADATA = Type.getType(DefaultAnnotationMetadata.class);
    private static final org.objectweb.asm.commons.Method METHOD_MAP_OF = org.objectweb.asm.commons.Method.getMethod(
        ReflectionUtils.getRequiredInternalMethod(
            AnnotationUtil.class,
            "internMapOf",
            Object[].class
        )
    );

    private static final org.objectweb.asm.commons.Method METHOD_LIST_OF = org.objectweb.asm.commons.Method.getMethod(
        ReflectionUtils.getRequiredInternalMethod(
            AnnotationUtil.class,
            "internListOf",
            Object[].class
        )
    );

    private static final org.objectweb.asm.commons.Method METHOD_ARE_DEFAULTS_REGISTERED = org.objectweb.asm.commons.Method.getMethod(
            ReflectionUtils.getRequiredInternalMethod(
                    DefaultAnnotationMetadata.class,
                    "areAnnotationDefaultsRegistered",
                    String.class
            )
    );

    private static final org.objectweb.asm.commons.Method METHOD_REGISTER_ANNOTATION_DEFAULTS = org.objectweb.asm.commons.Method.getMethod(
            ReflectionUtils.getRequiredInternalMethod(
                    DefaultAnnotationMetadata.class,
                    "registerAnnotationDefaults",
                    String.class,
                    Map.class
            )
    );

    private static final org.objectweb.asm.commons.Method CONSTRUCTOR_ANNOTATION_METADATA = org.objectweb.asm.commons.Method.getMethod(
        ReflectionUtils.getRequiredInternalConstructor(
            DefaultAnnotationMetadata.class,
            Map.class,
            Map.class,
            Map.class,
            Map.class,
            Map.class
        )
    );

    private static final org.objectweb.asm.commons.Method CONSTRUCTOR_ANNOTATION_VALUE = org.objectweb.asm.commons.Method.getMethod(
        ReflectionUtils.getRequiredInternalConstructor(
            io.micronaut.core.annotation.AnnotationValue.class,
            String.class
        )
    );

    private static final org.objectweb.asm.commons.Method CONSTRUCTOR_ANNOTATION_VALUE_AND_MAP = org.objectweb.asm.commons.Method.getMethod(
        ReflectionUtils.getRequiredInternalConstructor(
            io.micronaut.core.annotation.AnnotationValue.class,
            String.class,
            Map.class
        )
    );

    private static final Type EMPTY_MAP_TYPE = Type.getType(Map.class);
    private static final String EMPTY_MAP = "EMPTY_MAP";

    private final String className;
    private final DefaultAnnotationMetadata annotationMetadata;

    /**
     * Constructs a new writer for the given class name and metadata.
     *
     * @param className          The class name for which the metadata relates
     * @param annotationMetadata The annotation metadata
     */
    public AnnotationMetadataWriter(String className, AnnotationMetadata annotationMetadata) {
        this.className = className + AnnotationMetadata.CLASS_NAME_SUFFIX;
        if (annotationMetadata instanceof DefaultAnnotationMetadata) {
            this.annotationMetadata = (DefaultAnnotationMetadata) annotationMetadata;
        } else {
            throw new ClassGenerationException("Compile time metadata required to generate class: " + className);
        }
    }

    /**
     * @return The class name that this metadata will generate
     */
    public String getClassName() {
        return className;
    }

    /**
     * Accept an {@link ClassWriterOutputVisitor} to write all generated classes.
     *
     * @param outputVisitor The {@link ClassWriterOutputVisitor}
     * @throws IOException If an error occurs
     */
    public void accept(ClassWriterOutputVisitor outputVisitor) throws IOException {
        try (OutputStream outputStream = outputVisitor.visitClass(className)) {
            ClassWriter classWriter = generateClassBytes();
            outputStream.write(classWriter.toByteArray());
        }
    }

    /**
     * Write the class to the output stream, such a JavaFileObject created from a java annotation processor Filer object.
     *
     * @param outputStream the output stream pointing to the target class file
     */
    public void writeTo(OutputStream outputStream) {
        try {
            ClassWriter classWriter = generateClassBytes();

            writeClassToDisk(outputStream, classWriter);
        } catch (Throwable e) {
            throw new ClassGenerationException("Error generating annotation metadata: " + e.getMessage(), e);
        }
    }

    /**
     * Writes out the byte code necessary to instantiate the given {@link DefaultAnnotationMetadata}.
     *
     * @param generatorAdapter   The generator adapter
     * @param annotationMetadata The annotation metadata
     */
    public static void instantiateNewMetadata(GeneratorAdapter generatorAdapter, DefaultAnnotationMetadata annotationMetadata) {
        instantiateInternal(generatorAdapter, annotationMetadata, true);
    }

    /**
     * Writes annotation attributes to the given generator.
     *
     * @param generatorAdapter The generator adapter
     * @param annotationData   The annotation data
     */
    @Internal
    public static void pushAnnotationAttributes(GeneratorAdapter generatorAdapter, Map<CharSequence, Object> annotationData) {
        int totalSize = annotationData.size() * 2;
        // start a new array
        pushNewArray(generatorAdapter, Object.class, totalSize);
        int i = 0;
        for (Map.Entry<CharSequence, Object> entry : annotationData.entrySet()) {
            // use the property name as the key
            String memberName = entry.getKey().toString();
            pushStoreStringInArray(generatorAdapter, i++, totalSize, memberName);
            // use the property type as the value
            Object value = entry.getValue();
            pushStoreInArray(generatorAdapter, i++, totalSize, () ->
                pushValue(generatorAdapter, value)
            );
        }
        // invoke the AbstractBeanDefinition.createMap method
        generatorAdapter.invokeStatic(Type.getType(AnnotationUtil.class), METHOD_MAP_OF);
    }

    private static void instantiateInternal(GeneratorAdapter generatorAdapter, DefaultAnnotationMetadata annotationMetadata, boolean isNew) {
        if (isNew) {
            generatorAdapter.visitTypeInsn(NEW, TYPE_DEFAULT_ANNOTATION_METADATA.getInternalName());
            generatorAdapter.visitInsn(DUP);
        } else {
            generatorAdapter.loadThis();
        }
        // 1st argument: the declared annotations
        pushCreateAnnotationData(generatorAdapter, annotationMetadata.declaredAnnotations);
        // 2nd argument: the declared stereotypes
        pushCreateAnnotationData(generatorAdapter, annotationMetadata.declaredStereotypes);
        // 3rd argument: all stereotypes
        pushCreateAnnotationData(generatorAdapter, annotationMetadata.allStereotypes);
        // 4th argument: all annotations
        pushCreateAnnotationData(generatorAdapter, annotationMetadata.allAnnotations);
        // 5th argument: annotations by stereotype
        pushCreateAnnotationsByStereotypeData(generatorAdapter, annotationMetadata.annotationsByStereotype);

        // invoke the constructor
        generatorAdapter.invokeConstructor(TYPE_DEFAULT_ANNOTATION_METADATA, CONSTRUCTOR_ANNOTATION_METADATA);

    }

    private ClassWriter generateClassBytes() {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        startClass(classWriter, getInternalName(className), TYPE_DEFAULT_ANNOTATION_METADATA);

        GeneratorAdapter constructor = startConstructor(classWriter);
        DefaultAnnotationMetadata annotationMetadata = this.annotationMetadata;

        instantiateInternal(constructor, annotationMetadata, false);
        constructor.visitInsn(RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();

        Map<String, Map<CharSequence, Object>> annotationDefaultValues = annotationMetadata.annotationDefaultValues;
        if (annotationDefaultValues != null) {

            MethodVisitor si = classWriter.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
            GeneratorAdapter staticInit = new GeneratorAdapter(si, ACC_STATIC, "<clinit>", "()V");

            for (Map.Entry<String, Map<CharSequence, Object>> entry : annotationDefaultValues.entrySet()) {
                String annotationName = entry.getKey();

                Label falseCondition = new Label();

                staticInit.push(annotationName);
                staticInit.invokeStatic(TYPE_DEFAULT_ANNOTATION_METADATA, METHOD_ARE_DEFAULTS_REGISTERED);
                staticInit.push(true);
                staticInit.ifCmp(Type.BOOLEAN_TYPE, GeneratorAdapter.EQ, falseCondition);
                staticInit.visitLabel(new Label());

                staticInit.push(annotationName);
                pushAnnotationAttributes(staticInit, entry.getValue());
                staticInit.invokeStatic(TYPE_DEFAULT_ANNOTATION_METADATA, METHOD_REGISTER_ANNOTATION_DEFAULTS);
                staticInit.visitLabel(falseCondition);
            }
            staticInit.visitInsn(RETURN);

            staticInit.visitMaxs(1, 1);
            staticInit.visitEnd();
        }
        return classWriter;
    }

    private static void pushCreateListCall(GeneratorAdapter methodVisitor, List<String> names) {
        int totalSize = names == null ? 0 : names.size();
        if (totalSize > 0) {

            // start a new array
            pushNewArray(methodVisitor, Object.class, totalSize);
            int i = 0;
            for (String name : names) {
                // use the property name as the key
                pushStoreStringInArray(methodVisitor, i++, totalSize, name);
                // use the property type as the value
            }
            // invoke the AbstractBeanDefinition.createMap method
            methodVisitor.invokeStatic(Type.getType(AnnotationUtil.class), METHOD_LIST_OF);
        } else {
            methodVisitor.visitInsn(ACONST_NULL);
        }
    }

    private static void pushCreateAnnotationsByStereotypeData(GeneratorAdapter methodVisitor, Map<String, List<String>> annotationData) {
        int totalSize = annotationData == null ? 0 : annotationData.size() * 2;
        if (totalSize > 0) {

            // start a new array
            pushNewArray(methodVisitor, Object.class, totalSize);
            int i = 0;
            for (Map.Entry<String, List<String>> entry : annotationData.entrySet()) {
                // use the property name as the key
                String annotationName = entry.getKey();
                pushStoreStringInArray(methodVisitor, i++, totalSize, annotationName);
                // use the property type as the value
                pushStoreInArray(methodVisitor, i++, totalSize, () ->
                    pushCreateListCall(methodVisitor, entry.getValue())
                );
            }
            // invoke the AbstractBeanDefinition.createMap method
            methodVisitor.invokeStatic(Type.getType(AnnotationUtil.class), METHOD_MAP_OF);
        } else {
            methodVisitor.visitInsn(ACONST_NULL);
        }
    }

    private static void pushCreateAnnotationData(GeneratorAdapter methodVisitor, Map<String, Map<CharSequence, Object>> annotationData) {
        int totalSize = annotationData == null ? 0 : annotationData.size() * 2;
        if (totalSize > 0) {

            // start a new array
            pushNewArray(methodVisitor, Object.class, totalSize);
            int i = 0;
            for (Map.Entry<String, Map<CharSequence, Object>> entry : annotationData.entrySet()) {
                // use the property name as the key
                String annotationName = entry.getKey();
                pushStoreStringInArray(methodVisitor, i++, totalSize, annotationName);
                // use the property type as the value
                Map<CharSequence, Object> attributes = entry.getValue();
                if (attributes.isEmpty()) {
                    pushStoreInArray(methodVisitor, i++, totalSize, () ->
                        methodVisitor.getStatic(Type.getType(Collections.class), EMPTY_MAP, EMPTY_MAP_TYPE)
                    );
                } else {
                    pushStoreInArray(methodVisitor, i++, totalSize, () ->
                        pushAnnotationAttributes(methodVisitor, attributes)
                    );
                }
            }
            // invoke the StringUtils.mapOf method
            methodVisitor.invokeStatic(Type.getType(AnnotationUtil.class), METHOD_MAP_OF);
        } else {
            methodVisitor.visitInsn(ACONST_NULL);
        }
    }

    private static void pushValue(GeneratorAdapter methodVisitor, Object value) {
        if (value == null) {
            methodVisitor.visitInsn(ACONST_NULL);
        } else if (value instanceof Boolean) {
            methodVisitor.push((Boolean) value);
            pushBoxPrimitiveIfNecessary(boolean.class, methodVisitor);
        } else if (value instanceof String) {
            methodVisitor.push((String) value);
        } else if (value instanceof Enum) {
            Enum enumObject = (Enum) value;
            Class declaringClass = enumObject.getDeclaringClass();
            Type t = Type.getType(declaringClass);
            methodVisitor.getStatic(t, value.toString(), t);
        } else if (value.getClass().isArray()) {
            Object[] array = (Object[]) value;
            int len = array.length;
            pushNewArray(methodVisitor, ((Object[]) value).getClass().getComponentType(), len);
            for (int i = 0; i < array.length; i++) {
                int index = i;
                pushStoreInArray(methodVisitor, i, len, () ->
                    pushValue(methodVisitor, array[index])
                );
            }
        } else if (value instanceof List) {
            List array = (List) value;
            int len = array.size();
            if (len == 0) {
                pushNewArray(methodVisitor, Object.class, len);
            } else {
                boolean first = true;
                for (int i = 0; i < len; i++) {
                    Object v = array.get(i);
                    if (first) {
                        Class type = v == null ? Object.class : v.getClass();
                        pushNewArray(methodVisitor, type, len);
                        first = false;
                    }
                    pushStoreInArray(methodVisitor, i, len, () -> pushValue(methodVisitor, v));
                }
            }
        } else if (value instanceof Long) {
            methodVisitor.push(((Long) value));
            pushBoxPrimitiveIfNecessary(long.class, methodVisitor);
        } else if (value instanceof Double) {
            methodVisitor.push(((Double) value));
            pushBoxPrimitiveIfNecessary(double.class, methodVisitor);
        } else if (value instanceof Float) {
            methodVisitor.push(((Float) value));
            pushBoxPrimitiveIfNecessary(float.class, methodVisitor);
        } else if (value instanceof Number) {
            methodVisitor.push(((Number) value).intValue());
            pushBoxPrimitiveIfNecessary(ReflectionUtils.getPrimitiveType(value.getClass()), methodVisitor);
        } else if (value instanceof io.micronaut.core.annotation.AnnotationValue) {
            io.micronaut.core.annotation.AnnotationValue data = (io.micronaut.core.annotation.AnnotationValue) value;
            String annotationName = data.getAnnotationName();
            Map<CharSequence, Object> values = data.getValues();
            Type annotationValueType = Type.getType(io.micronaut.core.annotation.AnnotationValue.class);
            methodVisitor.newInstance(annotationValueType);
            methodVisitor.dup();
            methodVisitor.push(annotationName);

            if (values != null) {
                pushAnnotationAttributes(methodVisitor, values);
                methodVisitor.invokeConstructor(annotationValueType, CONSTRUCTOR_ANNOTATION_VALUE_AND_MAP);
            } else {
                methodVisitor.invokeConstructor(annotationValueType, CONSTRUCTOR_ANNOTATION_VALUE);
            }
        } else {
            methodVisitor.visitInsn(ACONST_NULL);
        }
    }
}
