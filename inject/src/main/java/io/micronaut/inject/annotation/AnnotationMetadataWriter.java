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
package io.micronaut.inject.annotation;

import io.micronaut.core.annotation.*;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.ImmutableSortedStringsArrayMap;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.writer.AbstractAnnotationMetadataWriter;
import io.micronaut.inject.writer.AbstractClassFileWriter;
import io.micronaut.inject.writer.ClassGenerationException;
import io.micronaut.inject.writer.ClassWriterOutputVisitor;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Responsible for writing class files that are instances of {@link AnnotationMetadata}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class AnnotationMetadataWriter extends AbstractClassFileWriter {

    private static final Type TYPE_DEFAULT_ANNOTATION_METADATA = Type.getType(DefaultAnnotationMetadata.class);
    private static final Type TYPE_DEFAULT_ANNOTATION_METADATA_HIERARCHY = Type.getType(AnnotationMetadataHierarchy.class);
    private static final Type TYPE_ANNOTATION_CLASS_VALUE = Type.getType(AnnotationClassValue.class);
    private static final org.objectweb.asm.commons.Method INTERN_MAP_OF_METHOD = org.objectweb.asm.commons.Method.getMethod(
            ReflectionUtils.getRequiredInternalMethod(
                    AnnotationUtil.class,
                    "internMapOf",
                    String.class,
                    Object.class
            )
    );

    private static final org.objectweb.asm.commons.Method METHOD_LIST_OF = org.objectweb.asm.commons.Method.getMethod(
            ReflectionUtils.getRequiredInternalMethod(
                    AnnotationUtil.class,
                    "internListOf",
                    Object[].class
            )
    );

    private static final org.objectweb.asm.commons.Method METHOD_REGISTER_ANNOTATION_DEFAULTS = org.objectweb.asm.commons.Method.getMethod(
            ReflectionUtils.getRequiredInternalMethod(
                    DefaultAnnotationMetadata.class,
                    "registerAnnotationDefaults",
                    AnnotationClassValue.class,
                    Map.class
            )
    );

    private static final org.objectweb.asm.commons.Method METHOD_REGISTER_ANNOTATION_TYPE = org.objectweb.asm.commons.Method.getMethod(
            ReflectionUtils.getRequiredInternalMethod(
                    DefaultAnnotationMetadata.class,
                    "registerAnnotationType",
                    AnnotationClassValue.class
            )
    );

    private static final org.objectweb.asm.commons.Method METHOD_GET_DEFAULT_VALUES = org.objectweb.asm.commons.Method.getMethod(
            ReflectionUtils.getRequiredInternalMethod(
                    AnnotationMetadataSupport.class,
                    "getDefaultValues",
                    String.class
            )
    );

    private static final org.objectweb.asm.commons.Method CONSTRUCTOR_ANNOTATION_METADATA = org.objectweb.asm.commons.Method.getMethod(
            ReflectionUtils.getRequiredInternalConstructor(
                    DefaultAnnotationMetadata.class,
                    Map.class,
                    Map.class,
                    Map.class,
                    Map.class,
                    Map.class,
                    boolean.class
            )
    );

    private static final org.objectweb.asm.commons.Method CONSTRUCTOR_ANNOTATION_METADATA_HIERARCHY = org.objectweb.asm.commons.Method.getMethod(
            ReflectionUtils.getRequiredInternalConstructor(
                    AnnotationMetadataHierarchy.class,
                    AnnotationMetadata[].class
            )
    );

    private static final org.objectweb.asm.commons.Method CONSTRUCTOR_ANNOTATION_VALUE_AND_MAP = org.objectweb.asm.commons.Method.getMethod(
            ReflectionUtils.getRequiredInternalConstructor(
                    io.micronaut.core.annotation.AnnotationValue.class,
                    String.class,
                    Map.class,
                    Map.class
            )
    );

    private static final org.objectweb.asm.commons.Method CONSTRUCTOR_CLASS_VALUE = org.objectweb.asm.commons.Method.getMethod(
            ReflectionUtils.getRequiredInternalConstructor(
                    AnnotationClassValue.class,
                    String.class
            )
    );

    private static final org.objectweb.asm.commons.Method CONSTRUCTOR_CLASS_VALUE_WITH_CLASS = org.objectweb.asm.commons.Method.getMethod(
            ReflectionUtils.getRequiredInternalConstructor(
                    AnnotationClassValue.class,
                    Class.class
            )
    );

    private static final org.objectweb.asm.commons.Method CONSTRUCTOR_CLASS_VALUE_WITH_INSTANCE = org.objectweb.asm.commons.Method.getMethod(
            ReflectionUtils.getRequiredInternalConstructor(
                    AnnotationClassValue.class,
                    Object.class
            )
    );

    private static final org.objectweb.asm.commons.Method[] MAP_OF;
    private static final org.objectweb.asm.commons.Method MAP_BY_ARRAY;

    static {
        MAP_OF = new Method[11];
        for (int i = 2; i < MAP_OF.length; i++) {
            Class[] mapArgs = new Class[i * 2];
            for (int k = 0; k < i * 2; k += 2) {
                mapArgs[k] = String.class;
                mapArgs[k + 1] = Object.class;
            }
            MAP_OF[i] = org.objectweb.asm.commons.Method.getMethod(ReflectionUtils.getRequiredMethod(ImmutableSortedStringsArrayMap.class, "of", mapArgs));
        }
        MAP_BY_ARRAY = org.objectweb.asm.commons.Method.getMethod(ReflectionUtils.getRequiredMethod(ImmutableSortedStringsArrayMap.class, "of", Object[].class));
    }

    private static final Type ANNOTATION_UTIL_TYPE = Type.getType(AnnotationUtil.class);
    private static final Type SORTED_IMMUTABLE_MAP_TYPE = Type.getType(ImmutableSortedStringsArrayMap.class);
    private static final Type MAP_TYPE = Type.getType(Map.class);
    private static final Type LIST_TYPE = Type.getType(List.class);
    private static final String EMPTY_MAP = "EMPTY_MAP";
    private static final String EMPTY_LIST = "EMPTY_LIST";

    private static final String LOAD_CLASS_PREFIX = "$micronaut_load_class_value_";

    private final String className;
    private final AnnotationMetadata annotationMetadata;
    private final boolean writeAnnotationDefaults;

    /**
     * Constructs a new writer for the given class name and metadata.
     *
     * @param className               The class name for which the metadata relates
     * @param originatingElement      The originating element
     * @param annotationMetadata      The annotation metadata
     * @param writeAnnotationDefaults Whether annotations defaults should be written
     * @deprecated No longer needs to be instantiated directly, just use the static methods
     */
    @Deprecated
    public AnnotationMetadataWriter(
            String className,
            ClassElement originatingElement,
            AnnotationMetadata annotationMetadata,
            boolean writeAnnotationDefaults) {
        super(originatingElement);
        this.className = className + AnnotationMetadata.CLASS_NAME_SUFFIX;
        if (annotationMetadata instanceof DefaultAnnotationMetadata) {
            this.annotationMetadata = annotationMetadata;
        } else if (annotationMetadata instanceof AnnotationMetadataHierarchy) {
            final AnnotationMetadataHierarchy hierarchy = (AnnotationMetadataHierarchy) annotationMetadata;
            this.annotationMetadata = hierarchy.getDeclaredMetadata();
        } else {
            throw new ClassGenerationException("Compile time metadata required to generate class: " + className);
        }
        this.writeAnnotationDefaults = writeAnnotationDefaults;
    }

    /**
     * Constructs a new writer for the given class name and metadata.
     *
     * @param className          The class name for which the metadata relates
     * @param originatingElement The originating element
     * @param annotationMetadata The annotation metadata
     * @deprecated No longer needs to be instantiated directly, just use the static methods
     */
    @Deprecated
    public AnnotationMetadataWriter(
            String className,
            ClassElement originatingElement,
            AnnotationMetadata annotationMetadata) {
        this(className, originatingElement, annotationMetadata, false);
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
    @Override
    public void accept(ClassWriterOutputVisitor outputVisitor) throws IOException {
        ClassWriter classWriter = generateClassBytes();
        if (classWriter != null) {

            try (OutputStream outputStream = outputVisitor.visitClass(className, getOriginatingElements())) {
                outputStream.write(classWriter.toByteArray());
            }
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
     * @param owningType           The owning type
     * @param declaringClassWriter The declaring class writer
     * @param generatorAdapter     The generator adapter
     * @param annotationMetadata   The annotation metadata
     * @param defaultsStorage      The annotation defaults
     * @param loadTypeMethods      The generated load type methods
     */
    @Internal
    @UsedByGeneratedCode
    public static void instantiateNewMetadata(Type owningType, ClassWriter declaringClassWriter, GeneratorAdapter generatorAdapter, DefaultAnnotationMetadata annotationMetadata, Map<String, Integer> defaultsStorage, Map<String, GeneratorAdapter> loadTypeMethods) {
        instantiateInternal(owningType, declaringClassWriter, generatorAdapter, annotationMetadata, true, defaultsStorage, loadTypeMethods);
    }

    /**
     * Writes out the byte code necessary to instantiate the given {@link AnnotationMetadataHierarchy}.
     *
     * @param owningType       The owning type
     * @param classWriter      The declaring class writer
     * @param generatorAdapter The generator adapter
     * @param hierarchy        The annotation metadata
     * @param defaultsStorage  The annotation defaults
     * @param loadTypeMethods  The generated load type methods
     */
    @Internal
    @UsedByGeneratedCode
    public static void instantiateNewMetadataHierarchy(
            Type owningType,
            ClassWriter classWriter,
            GeneratorAdapter generatorAdapter,
            AnnotationMetadataHierarchy hierarchy,
            Map<String, Integer> defaultsStorage,
            Map<String, GeneratorAdapter> loadTypeMethods) {

        if (hierarchy.isEmpty()) {
            generatorAdapter.getStatic(Type.getType(AnnotationMetadata.class), "EMPTY_METADATA", Type.getType(AnnotationMetadata.class));
            return;
        }
        List<AnnotationMetadata> notEmpty = CollectionUtils.iterableToList(hierarchy)
                .stream().filter(h -> !h.isEmpty()).collect(Collectors.toList());
        if (notEmpty.size() == 1) {
            pushNewAnnotationMetadataOrReference(owningType, classWriter, generatorAdapter, defaultsStorage, loadTypeMethods, notEmpty.get(0));
            return;
        }

        generatorAdapter.visitTypeInsn(NEW, TYPE_DEFAULT_ANNOTATION_METADATA_HIERARCHY.getInternalName());
        generatorAdapter.visitInsn(DUP);

        pushNewArray(generatorAdapter, AnnotationMetadata.class, 2);
        pushStoreInArray(generatorAdapter, 0, 2, () -> {
            final AnnotationMetadata rootMetadata = hierarchy.getRootMetadata();
            pushNewAnnotationMetadataOrReference(owningType, classWriter, generatorAdapter, defaultsStorage, loadTypeMethods, rootMetadata);
        });
        pushStoreInArray(generatorAdapter, 1, 2, () -> {
            final AnnotationMetadata declaredMetadata = hierarchy.getDeclaredMetadata();
            pushNewAnnotationMetadataOrReference(owningType, classWriter, generatorAdapter, defaultsStorage, loadTypeMethods, declaredMetadata);
        });

        // invoke the constructor
        generatorAdapter.invokeConstructor(TYPE_DEFAULT_ANNOTATION_METADATA_HIERARCHY, CONSTRUCTOR_ANNOTATION_METADATA_HIERARCHY);
    }

    /**
     * Pushes an annotation metadata reference.
     *
     * @param generatorAdapter   The generator adapter
     * @param annotationMetadata The metadata
     */
    @Internal
    public static void pushAnnotationMetadataReference(GeneratorAdapter generatorAdapter, AnnotationMetadataReference annotationMetadata) {
        final String className = annotationMetadata.getClassName();
        final Type type = getTypeReferenceForName(className);
        generatorAdapter.getStatic(type, AbstractAnnotationMetadataWriter.FIELD_ANNOTATION_METADATA, Type.getType(AnnotationMetadata.class));
    }

    @Internal
    private static void pushNewAnnotationMetadataOrReference(
            Type owningType,
            ClassWriter classWriter,
            GeneratorAdapter generatorAdapter,
            Map<String, Integer> defaultsStorage,
            Map<String, GeneratorAdapter> loadTypeMethods,
            AnnotationMetadata annotationMetadata) {
        if (annotationMetadata instanceof DefaultAnnotationMetadata) {
            instantiateNewMetadata(
                    owningType,
                    classWriter,
                    generatorAdapter,
                    (DefaultAnnotationMetadata) annotationMetadata,
                    defaultsStorage,
                    loadTypeMethods
            );
        } else if (annotationMetadata instanceof AnnotationMetadataReference) {
            pushAnnotationMetadataReference(generatorAdapter, (AnnotationMetadataReference) annotationMetadata);
        } else {
            generatorAdapter.getStatic(Type.getType(AnnotationMetadata.class), "EMPTY_METADATA", Type.getType(AnnotationMetadata.class));
        }
    }

    /**
     * Writes out the byte code necessary to instantiate the given {@link DefaultAnnotationMetadata}.
     *
     * @param annotationMetadata The annotation metadata
     * @param classWriter        The class writer
     * @param owningType         The owning type
     * @param defaultsStorage    The annotation defaults
     * @param loadTypeMethods    The generated load type methods
     */
    @Internal
    public static void writeAnnotationDefaults(DefaultAnnotationMetadata annotationMetadata, ClassWriter classWriter, Type owningType, Map<String, Integer> defaultsStorage, Map<String, GeneratorAdapter> loadTypeMethods) {
        final Map<String, Map<CharSequence, Object>> annotationDefaultValues = annotationMetadata.annotationDefaultValues;
        if (CollectionUtils.isNotEmpty(annotationDefaultValues)) {

            MethodVisitor si = classWriter.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
            GeneratorAdapter staticInit = new GeneratorAdapter(si, ACC_STATIC, "<clinit>", "()V");

            writeAnnotationDefaults(owningType, classWriter, staticInit, annotationMetadata, defaultsStorage, loadTypeMethods);
            staticInit.visitInsn(RETURN);

            staticInit.visitMaxs(1, 1);
            staticInit.visitEnd();
        }
    }

    /**
     * Write annotation defaults into the given static init block.
     *
     * @param owningType         The owning type
     * @param classWriter        The class writer
     * @param staticInit         The staitc init
     * @param annotationMetadata The annotation metadata
     * @param defaultsStorage    The annotation defaults
     * @param loadTypeMethods    The load type methods
     */
    @Internal
    public static void writeAnnotationDefaults(
            Type owningType,
            ClassWriter classWriter,
            GeneratorAdapter staticInit,
            DefaultAnnotationMetadata annotationMetadata,
            Map<String, Integer> defaultsStorage,
            Map<String, GeneratorAdapter> loadTypeMethods) {
        final Map<String, Map<CharSequence, Object>> annotationDefaultValues = annotationMetadata.annotationDefaultValues;
        if (CollectionUtils.isNotEmpty(annotationDefaultValues)) {
            for (Map.Entry<String, Map<CharSequence, Object>> entry : annotationDefaultValues.entrySet()) {
                final Map<CharSequence, Object> annotationValues = entry.getValue();
                final boolean typeOnly = CollectionUtils.isEmpty(annotationValues);
                String annotationName = entry.getKey();

                // skip already registered
                if (typeOnly && AnnotationMetadataSupport.getRegisteredAnnotationType(annotationName).isPresent()) {
                    continue;
                }

//                Label falseCondition = new Label();
//
//                staticInit.push(annotationName);
//                staticInit.invokeStatic(TYPE_DEFAULT_ANNOTATION_METADATA, METHOD_ARE_DEFAULTS_REGISTERED);
//                staticInit.push(true);
//                staticInit.ifCmp(Type.BOOLEAN_TYPE, GeneratorAdapter.EQ, falseCondition);
//                staticInit.visitLabel(new Label());

                invokeLoadClassValueMethod(owningType, classWriter, staticInit, loadTypeMethods, new AnnotationClassValue(annotationName));

                if (!typeOnly) {
                    pushMapOf(staticInit, annotationValues, true, null, v -> pushValue(owningType, classWriter, staticInit, v, defaultsStorage, loadTypeMethods));
                    staticInit.invokeStatic(TYPE_DEFAULT_ANNOTATION_METADATA, METHOD_REGISTER_ANNOTATION_DEFAULTS);
                } else {
                    staticInit.invokeStatic(TYPE_DEFAULT_ANNOTATION_METADATA, METHOD_REGISTER_ANNOTATION_TYPE);
                }
//                staticInit.visitLabel(falseCondition);
            }
        }
    }

    private static <T> void pushMapOf(GeneratorAdapter generatorAdapter, Map<? extends CharSequence, T> annotationData,
                                      boolean skipEmpty,
                                      T empty,
                                      Consumer<T> pushValue) {
        Set<? extends Map.Entry<String, T>> entrySet = annotationData != null ? annotationData.entrySet()
                .stream()
                .filter(e -> !skipEmpty || (e.getKey() != null && e.getValue() != null))
                .map(e -> e.getValue() == null && empty != null ? new AbstractMap.SimpleEntry<>(e.getKey().toString(), empty) : new AbstractMap.SimpleEntry<>(e.getKey().toString(), e.getValue()))
                .collect(Collectors.toCollection(() -> new TreeSet<>(Map.Entry.comparingByKey()))) : null;
        if (entrySet == null || entrySet.isEmpty()) {
            generatorAdapter.getStatic(Type.getType(Collections.class), EMPTY_MAP, MAP_TYPE);
            return;
        }
        if (entrySet.size() == 1) {
            for (Map.Entry<String, T> entry : entrySet) {
                generatorAdapter.push(entry.getKey());
                pushValue.accept(entry.getValue());
            }
            generatorAdapter.invokeStatic(ANNOTATION_UTIL_TYPE, INTERN_MAP_OF_METHOD);
        } else if (entrySet.size() < MAP_OF.length) {
            for (Map.Entry<String, T> entry : entrySet) {
                generatorAdapter.push(entry.getKey());
                pushValue.accept(entry.getValue());
            }
            generatorAdapter.invokeStatic(SORTED_IMMUTABLE_MAP_TYPE, MAP_OF[entrySet.size()]);
        } else {
            int totalSize = entrySet.size() * 2;
            // start a new array
            pushNewArray(generatorAdapter, Object.class, totalSize);
            int i = 0;
            for (Map.Entry<? extends CharSequence, T> entry : entrySet) {
                // use the property name as the key
                String memberName = entry.getKey().toString();
                pushStoreStringInArray(generatorAdapter, i++, totalSize, memberName);
                // use the property type as the value
                pushStoreInArray(generatorAdapter, i++, totalSize, () -> pushValue.accept(entry.getValue()));
            }
            generatorAdapter.invokeStatic(SORTED_IMMUTABLE_MAP_TYPE, MAP_BY_ARRAY);
        }
    }

    private static void pushListOfString(GeneratorAdapter methodVisitor, List<String> names) {
        if (names != null) {
            names = names.stream().filter(Objects::nonNull).collect(Collectors.toList());
        }
        if (names == null || names.isEmpty()) {
            methodVisitor.getStatic(Type.getType(Collections.class), EMPTY_LIST, LIST_TYPE);
            return;
        }
        int totalSize = names.size();
        // start a new array
        pushNewArray(methodVisitor, Object.class, totalSize);
        int i = 0;
        for (String name : names) {
            // use the property name as the key
            pushStoreStringInArray(methodVisitor, i++, totalSize, name);
            // use the property type as the value
        }
        // invoke the AbstractBeanDefinition.createMap method
        methodVisitor.invokeStatic(ANNOTATION_UTIL_TYPE, METHOD_LIST_OF);
    }

    private static void instantiateInternal(
            Type owningType,
            ClassWriter declaringClassWriter,
            GeneratorAdapter generatorAdapter,
            DefaultAnnotationMetadata annotationMetadata,
            boolean isNew,
            Map<String, Integer> defaultsStorage,
            Map<String, GeneratorAdapter> loadTypeMethods) {
        if (isNew) {
            generatorAdapter.visitTypeInsn(NEW, TYPE_DEFAULT_ANNOTATION_METADATA.getInternalName());
            generatorAdapter.visitInsn(DUP);
        } else {
            generatorAdapter.loadThis();
        }
        // 1st argument: the declared annotations
        pushCreateAnnotationData(owningType, declaringClassWriter, generatorAdapter, annotationMetadata.declaredAnnotations, defaultsStorage, loadTypeMethods, annotationMetadata.getSourceRetentionAnnotations());
        // 2nd argument: the declared stereotypes
        pushCreateAnnotationData(owningType, declaringClassWriter, generatorAdapter, annotationMetadata.declaredStereotypes, defaultsStorage, loadTypeMethods, annotationMetadata.getSourceRetentionAnnotations());
        // 3rd argument: all stereotypes
        pushCreateAnnotationData(owningType, declaringClassWriter, generatorAdapter, annotationMetadata.allStereotypes, defaultsStorage, loadTypeMethods, annotationMetadata.getSourceRetentionAnnotations());
        // 4th argument: all annotations
        pushCreateAnnotationData(owningType, declaringClassWriter, generatorAdapter, annotationMetadata.allAnnotations, defaultsStorage, loadTypeMethods, annotationMetadata.getSourceRetentionAnnotations());
        // 5th argument: annotations by stereotype
        pushMapOf(generatorAdapter, annotationMetadata.annotationsByStereotype, false, Collections.emptyList(), list -> pushListOfString(generatorAdapter, list));
        // 6th argument: has property expressions
        generatorAdapter.push(annotationMetadata.hasPropertyExpressions());

        // invoke the constructor
        generatorAdapter.invokeConstructor(TYPE_DEFAULT_ANNOTATION_METADATA, CONSTRUCTOR_ANNOTATION_METADATA);

    }

    private ClassWriter generateClassBytes() {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        final Type owningType = getTypeReferenceForName(className);
        startClass(classWriter, getInternalName(className), TYPE_DEFAULT_ANNOTATION_METADATA);

        GeneratorAdapter constructor = startConstructor(classWriter);
        DefaultAnnotationMetadata annotationMetadata = (DefaultAnnotationMetadata) this.annotationMetadata;

        Map<String, Integer> defaultsStorage = new HashMap<>(3);
        final HashMap<String, GeneratorAdapter> loadTypeMethods = new HashMap<>(5);
        instantiateInternal(
                owningType,
                classWriter,
                constructor,
                annotationMetadata,
                false,
                defaultsStorage,
                loadTypeMethods);
        constructor.visitInsn(RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();
        if (writeAnnotationDefaults) {
            writeAnnotationDefaults(annotationMetadata, classWriter, owningType, defaultsStorage, loadTypeMethods);
        }
        for (GeneratorAdapter adapter : loadTypeMethods.values()) {
            adapter.visitMaxs(3, 1);
            adapter.visitEnd();
        }
        classWriter.visitEnd();
        return classWriter;
    }

    private static void pushCreateAnnotationData(
            Type declaringType,
            ClassWriter declaringClassWriter,
            GeneratorAdapter methodVisitor,
            Map<String, Map<CharSequence, Object>> annotationData,
            Map<String, Integer> defaultsStorage,
            Map<String, GeneratorAdapter> loadTypeMethods,
            Set<String> sourceRetentionAnnotations) {
        if (annotationData != null) {
            annotationData = new LinkedHashMap<>(annotationData);
            for (String sourceRetentionAnnotation : sourceRetentionAnnotations) {
                annotationData.remove(sourceRetentionAnnotation);
            }
        }

        pushMapOf(methodVisitor, annotationData, false, Collections.emptyMap(), attributes -> {
            pushMapOf(methodVisitor, attributes, true, null, v -> pushValue(declaringType, declaringClassWriter, methodVisitor, v, defaultsStorage, loadTypeMethods));
        });
    }

    private static void pushValue(Type declaringType, ClassVisitor declaringClassWriter,
                                  GeneratorAdapter methodVisitor, Object value,
                                  Map<String, Integer> defaultsStorage,
                                  Map<String, GeneratorAdapter> loadTypeMethods) {
        if (value == null) {
            throw new IllegalStateException("Cannot map null value in: " + declaringType.getClassName());
        } else if (value instanceof Boolean) {
            methodVisitor.push((Boolean) value);
            pushBoxPrimitiveIfNecessary(boolean.class, methodVisitor);
        } else if (value instanceof String) {
            methodVisitor.push(value.toString());
        } else if (value instanceof AnnotationClassValue) {
            AnnotationClassValue acv = (AnnotationClassValue) value;
            if (acv.isInstantiated()) {
                methodVisitor.visitTypeInsn(NEW, TYPE_ANNOTATION_CLASS_VALUE.getInternalName());
                methodVisitor.visitInsn(DUP);
                methodVisitor.visitTypeInsn(NEW, getInternalName(acv.getName()));
                methodVisitor.visitInsn(DUP);
                methodVisitor.invokeConstructor(getTypeReferenceForName(acv.getName()), new Method(CONSTRUCTOR_NAME, getConstructorDescriptor()));
                methodVisitor.invokeConstructor(TYPE_ANNOTATION_CLASS_VALUE, CONSTRUCTOR_CLASS_VALUE_WITH_INSTANCE);
            } else {
                invokeLoadClassValueMethod(declaringType, declaringClassWriter, methodVisitor, loadTypeMethods, acv);
            }
        } else if (value instanceof Enum) {
            Enum enumObject = (Enum) value;
            Class declaringClass = enumObject.getDeclaringClass();
            Type t = Type.getType(declaringClass);
            methodVisitor.getStatic(t, enumObject.name(), t);
        } else if (value.getClass().isArray()) {
            final Class<?> componentType = ReflectionUtils.getWrapperType(value.getClass().getComponentType());
            int len = Array.getLength(value);
            if (Object.class == componentType && len == 0) {
                pushEmptyObjectsArray(methodVisitor);
            } else {
                pushNewArray(methodVisitor, componentType, len);
                for (int i = 0; i < len; i++) {
                    final Object v = Array.get(value, i);
                    pushStoreInArray(methodVisitor, i, len, () ->
                            pushValue(declaringType, declaringClassWriter, methodVisitor, v, defaultsStorage, loadTypeMethods)
                    );
                }
            }
        } else if (value instanceof Collection) {
            if (((Collection<?>) value).isEmpty()) {
                pushEmptyObjectsArray(methodVisitor);
            } else {
                List array = Arrays.asList(((Collection) value).toArray());
                int len = array.size();
                boolean first = true;
                for (int i = 0; i < len; i++) {
                    Object v = array.get(i);
                    if (first) {
                        Class type = v == null ? Object.class : v.getClass();
                        pushNewArray(methodVisitor, type, len);
                        first = false;
                    }
                    pushStoreInArray(methodVisitor, i, len, () -> pushValue(declaringType, declaringClassWriter, methodVisitor, v, defaultsStorage, loadTypeMethods));
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

            pushMapOf(methodVisitor, values, true, null, v -> pushValue(declaringType, declaringClassWriter, methodVisitor, v, defaultsStorage, loadTypeMethods));

            Integer defaultIndex = defaultsStorage.get(annotationName);
            if (defaultIndex == null) {
                methodVisitor.push(annotationName);
                methodVisitor.invokeStatic(Type.getType(AnnotationMetadataSupport.class), METHOD_GET_DEFAULT_VALUES);
                methodVisitor.dup();
                int localIndex = methodVisitor.newLocal(Type.getType(Map.class));
                methodVisitor.storeLocal(localIndex);
                defaultsStorage.put(annotationName, localIndex);
            } else {
                methodVisitor.loadLocal(defaultIndex);
            }
            methodVisitor.invokeConstructor(annotationValueType, CONSTRUCTOR_ANNOTATION_VALUE_AND_MAP);
        } else {
            throw new IllegalStateException("Cannot map unknown value: " + value + " in: " + declaringType.getClassName());
        }
    }

    private static void pushEmptyObjectsArray(GeneratorAdapter methodVisitor) {
        methodVisitor.getStatic(Type.getType(ArrayUtils.class), "EMPTY_OBJECT_ARRAY", Type.getType(Object[].class));
    }

    private static void invokeLoadClassValueMethod(
            Type declaringType,
            ClassVisitor declaringClassWriter,
            GeneratorAdapter methodVisitor,
            Map<String, GeneratorAdapter> loadTypeMethods,
            AnnotationClassValue acv) {
        final String typeName = acv.getName();
        final String desc = getMethodDescriptor(AnnotationClassValue.class, Collections.emptyList());
        final GeneratorAdapter loadTypeGeneratorMethod = loadTypeMethods.computeIfAbsent(typeName, type -> {
            final String methodName = LOAD_CLASS_PREFIX + loadTypeMethods.size();
            final GeneratorAdapter loadTypeGenerator = new GeneratorAdapter(declaringClassWriter.visitMethod(
                    ACC_STATIC | ACC_SYNTHETIC,
                    methodName,
                    desc,
                    null,
                    null

            ), ACC_STATIC | ACC_SYNTHETIC, methodName, desc);

            loadTypeGenerator.visitCode();
            Label tryStart = new Label();
            Label tryEnd = new Label();
            Label exceptionHandler = new Label();

            // This logic will generate a method such as the following, allowing non dynamic classloading:
            //
            // AnnotationClassValue $micronaut_load_class_value_0() {
            //     try {
            //          return new AnnotationClassValue(test.MyClass.class);
            //     } catch(Throwable e) {
            //          return new AnnotationClassValue("test.MyClass");
            //     }
            // }

            loadTypeGenerator.visitTryCatchBlock(tryStart, tryEnd, exceptionHandler, Type.getInternalName(Throwable.class));
            loadTypeGenerator.visitLabel(tryStart);
            loadTypeGenerator.visitTypeInsn(NEW, TYPE_ANNOTATION_CLASS_VALUE.getInternalName());
            loadTypeGenerator.visitInsn(DUP);
            loadTypeGenerator.push(getTypeReferenceForName(typeName));
            loadTypeGenerator.invokeConstructor(TYPE_ANNOTATION_CLASS_VALUE, CONSTRUCTOR_CLASS_VALUE_WITH_CLASS);
            loadTypeGenerator.visitLabel(tryEnd);
            loadTypeGenerator.returnValue();
            loadTypeGenerator.visitLabel(exceptionHandler);
            loadTypeGenerator.visitFrame(Opcodes.F_NEW, 0, new Object[]{}, 1, new Object[]{"java/lang/Throwable"});
            // Try load the class

            // fallback to return a class value that is just a string
            loadTypeGenerator.visitVarInsn(ASTORE, 0);
            loadTypeGenerator.visitTypeInsn(NEW, TYPE_ANNOTATION_CLASS_VALUE.getInternalName());
            loadTypeGenerator.visitInsn(DUP);
            loadTypeGenerator.push(typeName);
            loadTypeGenerator.invokeConstructor(TYPE_ANNOTATION_CLASS_VALUE, CONSTRUCTOR_CLASS_VALUE);
            loadTypeGenerator.returnValue();
            return loadTypeGenerator;
        });

        methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, declaringType.getInternalName(), loadTypeGeneratorMethod.getName(), desc, false);
    }
}
