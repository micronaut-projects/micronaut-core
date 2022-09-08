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
package io.micronaut.inject.beans.visitor;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.AbstractBeanIntrospectionReference;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospectionReference;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.annotation.AnnotationMetadataReference;
import io.micronaut.inject.annotation.AnnotationMetadataWriter;
import io.micronaut.inject.annotation.DefaultAnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.beans.AbstractInitializableBeanIntrospection;
import io.micronaut.inject.processing.JavaModelUtils;
import io.micronaut.inject.writer.AbstractAnnotationMetadataWriter;
import io.micronaut.inject.writer.ClassWriterOutputVisitor;
import io.micronaut.inject.writer.DispatchWriter;
import io.micronaut.inject.writer.StringSwitchWriter;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * A class file writer that writes a {@link BeanIntrospectionReference} and associated
 * {@link BeanIntrospection} for the given class.
 *
 * @author graemerocher
 * @author Denis Stepanov
 * @since 1.1
 */
@Internal
final class BeanIntrospectionWriter extends AbstractAnnotationMetadataWriter {
    private static final String REFERENCE_SUFFIX = "$IntrospectionRef";
    private static final String INTROSPECTION_SUFFIX = "$Introspection";

    private static final String FIELD_CONSTRUCTOR_ANNOTATION_METADATA = "$FIELD_CONSTRUCTOR_ANNOTATION_METADATA";
    private static final String FIELD_CONSTRUCTOR_ARGUMENTS = "$CONSTRUCTOR_ARGUMENTS";
    private static final String FIELD_BEAN_PROPERTIES_REFERENCES = "$PROPERTIES_REFERENCES";
    private static final String FIELD_BEAN_METHODS_REFERENCES = "$METHODS_REFERENCES";
    private static final Method PROPERTY_INDEX_OF = Method.getMethod(
            ReflectionUtils.findMethod(BeanIntrospection.class, "propertyIndexOf", String.class).get()
    );
    private static final Method FIND_PROPERTY_BY_INDEX_METHOD = Method.getMethod(
            ReflectionUtils.findMethod(AbstractInitializableBeanIntrospection.class, "getPropertyByIndex", int.class).get()
    );
    private static final Method FIND_INDEXED_PROPERTY_METHOD = Method.getMethod(
            ReflectionUtils.findMethod(AbstractInitializableBeanIntrospection.class, "findIndexedProperty", Class.class, String.class).get()
    );
    private static final Method GET_INDEXED_PROPERTIES = Method.getMethod(
            ReflectionUtils.findMethod(AbstractInitializableBeanIntrospection.class, "getIndexedProperties", Class.class).get()
    );
    private static final Method GET_BP_INDEXED_SUBSET_METHOD = Method.getMethod(
            ReflectionUtils.findMethod(AbstractInitializableBeanIntrospection.class, "getBeanPropertiesIndexedSubset", int[].class).get()
    );
    private static final Method COLLECTIONS_EMPTY_LIST = Method.getMethod(
            ReflectionUtils.findMethod(Collections.class, "emptyList").get()
    );

    private final ClassWriter referenceWriter;
    private final String introspectionName;
    private final Type introspectionType;
    private final Type beanType;
    private final Map<AnnotationWithValue, String> indexByAnnotationAndValue = new HashMap<>(2);
    private final Map<String, Set<String>> indexByAnnotations = new HashMap<>(2);
    private final Map<String, String> annotationIndexFields = new HashMap<>(2);
    private final ClassElement classElement;
    private boolean executed = false;
    private MethodElement constructor;
    private MethodElement defaultConstructor;

    private final List<BeanPropertyData> beanProperties = new ArrayList<>();
    private final List<BeanFieldData> beanFields = new ArrayList<>();
    private final List<BeanMethodData> beanMethods = new ArrayList<>();

    private final DispatchWriter dispatchWriter;

    /**
     * Default constructor.
     *
     * @param classElement           The class element
     * @param beanAnnotationMetadata The bean annotation metadata
     */
    BeanIntrospectionWriter(ClassElement classElement, AnnotationMetadata beanAnnotationMetadata) {
        super(computeReferenceName(classElement.getName()), classElement, beanAnnotationMetadata, true);
        final String name = classElement.getName();
        this.classElement = classElement;
        this.referenceWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        this.introspectionName = computeIntrospectionName(name);
        this.introspectionType = getTypeReferenceForName(introspectionName);
        this.beanType = getTypeReferenceForName(name);
        this.dispatchWriter = new DispatchWriter(introspectionType);
    }

    /**
     * Constructor used to generate a reference for already compiled classes.
     *
     * @param generatingType         The originating type
     * @param index                  A unique index
     * @param originatingElement     The originating element
     * @param classElement           The class element
     * @param beanAnnotationMetadata The bean annotation metadata
     */
    BeanIntrospectionWriter(
            String generatingType,
            int index,
            ClassElement originatingElement,
            ClassElement classElement,
            AnnotationMetadata beanAnnotationMetadata) {
        super(computeReferenceName(generatingType) + index, originatingElement, beanAnnotationMetadata, true);
        final String className = classElement.getName();
        this.classElement = classElement;
        this.referenceWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        this.introspectionName = computeIntrospectionName(generatingType, className);
        this.introspectionType = getTypeReferenceForName(introspectionName);
        this.beanType = getTypeReferenceForName(className);
        this.dispatchWriter = new DispatchWriter(introspectionType);
    }

    /**
     * @return The constructor.
     */
    @Nullable
    public MethodElement getConstructor() {
        return constructor;
    }

    /**
     * The bean type.
     *
     * @return The bean type
     */
    public Type getBeanType() {
        return beanType;
    }

    /**
     * Visit a property.
     *
     * @param type               The property type
     * @param genericType        The generic type
     * @param name               The property name
     * @param readMethod         The read method
     * @param writeMethod        The write methodname
     * @param isReadOnly         Is the property read only
     * @param annotationMetadata The property annotation metadata
     * @param typeArguments      The type arguments
     */
    void visitProperty(
            @NonNull TypedElement type,
            @NonNull TypedElement genericType,
            @NonNull String name,
            @Nullable MethodElement readMethod,
            @Nullable MethodElement writeMethod,
            boolean isReadOnly,
            @Nullable AnnotationMetadata annotationMetadata,
            @Nullable Map<String, ClassElement> typeArguments) {

        DefaultAnnotationMetadata.contributeDefaults(
                this.annotationMetadata,
                annotationMetadata
        );
        if (typeArguments != null) {
            for (ClassElement element : typeArguments.values()) {
                DefaultAnnotationMetadata.contributeRepeatable(
                        this.annotationMetadata,
                        element
                );
            }
        }

        int readMethodIndex = -1;
        if (readMethod != null) {
            readMethodIndex = dispatchWriter.addMethod(classElement, readMethod, true);
        }
        int writeMethodIndex = -1;
        int withMethodIndex = -1;
        if (writeMethod != null) {
            writeMethodIndex = dispatchWriter.addMethod(classElement, writeMethod, true);
        }
        boolean isMutable = !isReadOnly || hasAssociatedConstructorArgument(name, genericType);
        if (isMutable) {
            if (writeMethod == null) {
                final String prefix = this.annotationMetadata.stringValue(Introspected.class, "withPrefix").orElse("with");
                ElementQuery<MethodElement> elementQuery = ElementQuery.of(MethodElement.class)
                        .onlyAccessible()
                        .onlyDeclared()
                        .onlyInstance()
                        .named((n) -> n.startsWith(prefix) && n.equals(prefix + NameUtils.capitalize(name)))
                        .filter((methodElement -> {
                            ParameterElement[] parameters = methodElement.getParameters();
                            return parameters.length == 1 &&
                                    methodElement.getGenericReturnType().getName().equals(classElement.getName()) &&
                                    type.getType().isAssignable(parameters[0].getType());
                        }));
                MethodElement withMethod = classElement.getEnclosedElement(elementQuery).orElse(null);
                if (withMethod != null) {
                    withMethodIndex = dispatchWriter.addMethod(classElement, withMethod, true);
                } else {
                    MethodElement constructor = this.constructor == null ? defaultConstructor : this.constructor;
                    if (constructor != null) {
                        withMethodIndex = dispatchWriter.addDispatchTarget(new CopyConstructorDispatchTarget(constructor, name));
                    }
                }
            }
            // Otherwise, set method would be used in BeanProperty
        } else {
            withMethodIndex = dispatchWriter.addDispatchTarget(new ExceptionDispatchTarget(
                    UnsupportedOperationException.class,
                    "Cannot mutate property [" + name + "] that is not mutable via a setter method or constructor argument for type: " + beanType.getClassName()
            ));
        }

        beanProperties.add(new BeanPropertyData(
                genericType,
                name,
                annotationMetadata,
                typeArguments,
                readMethodIndex,
                writeMethodIndex,
                withMethodIndex,
                isReadOnly
        ));
    }

    /**
     * Visits a bean method.
     *
     * @param element The method
     */
    public void visitBeanMethod(MethodElement element) {
        if (element != null && !element.isPrivate()) {
            int dispatchIndex = dispatchWriter.addMethod(classElement, element);
            beanMethods.add(new BeanMethodData(element, dispatchIndex));
        }
    }

    /**
     * Visits a bean field.
     *
     * @param beanField The field
     */
    public void visitBeanField(FieldElement beanField) {
        int getDispatchIndex = dispatchWriter.addGetField(beanField);
        int setDispatchIndex = dispatchWriter.addSetField(beanField);
        beanFields.add(new BeanFieldData(beanField, getDispatchIndex, setDispatchIndex));
    }

    /**
     * Builds an index for the given property and annotation.
     *
     * @param annotationName The annotation
     * @param property       The property
     * @param value          the value of the annotation
     */
    void indexProperty(String annotationName, String property, @Nullable String value) {
        indexByAnnotationAndValue.put(new AnnotationWithValue(annotationName, value), property);
        indexByAnnotations.computeIfAbsent(annotationName, (a) -> new LinkedHashSet<>()).add(property);
    }

    @Override
    public void accept(ClassWriterOutputVisitor classWriterOutputVisitor) throws IOException {
        if (!executed) {

            // Run only once
            executed = true;

            // write the reference
            writeIntrospectionReference(classWriterOutputVisitor);
            loadTypeMethods.clear();
            // write the introspection
            writeIntrospectionClass(classWriterOutputVisitor);

        }
    }

    private void buildStaticInit(ClassWriter classWriter) {
        GeneratorAdapter staticInit = visitStaticInitializer(classWriter);
        if (constructor != null) {
            if (!constructor.getAnnotationMetadata().isEmpty()) {
                Type am = Type.getType(AnnotationMetadata.class);
                classWriter.visitField(ACC_PRIVATE | ACC_FINAL | ACC_STATIC, FIELD_CONSTRUCTOR_ANNOTATION_METADATA, am.getDescriptor(), null, null);
                pushAnnotationMetadata(classWriter, staticInit, constructor.getAnnotationMetadata());
                staticInit.putStatic(introspectionType, FIELD_CONSTRUCTOR_ANNOTATION_METADATA, am);
            }
            if (ArrayUtils.isNotEmpty(constructor.getParameters())) {
                Type args = Type.getType(Argument[].class);
                classWriter.visitField(ACC_PRIVATE | ACC_FINAL | ACC_STATIC, FIELD_CONSTRUCTOR_ARGUMENTS, args.getDescriptor(), null, null);
                pushBuildArgumentsForMethod(
                        introspectionType.getClassName(),
                        introspectionType,
                        classWriter,
                        staticInit,
                        Arrays.asList(constructor.getParameters()),
                        defaults,
                        loadTypeMethods
                );
                staticInit.putStatic(introspectionType, FIELD_CONSTRUCTOR_ARGUMENTS, args);
            }
        }
        if (!beanProperties.isEmpty() || !beanFields.isEmpty()) {
            Type beanPropertiesRefs = Type.getType(AbstractInitializableBeanIntrospection.BeanPropertyRef[].class);

            classWriter.visitField(ACC_PRIVATE | ACC_FINAL | ACC_STATIC, FIELD_BEAN_PROPERTIES_REFERENCES, beanPropertiesRefs.getDescriptor(), null, null);

            int size = beanProperties.size() + beanFields.size();

            pushNewArray(staticInit, AbstractInitializableBeanIntrospection.BeanPropertyRef.class, size);
            int i = 0;
            for (BeanPropertyData beanPropertyData : beanProperties) {
                pushStoreInArray(staticInit, i++, size, () ->
                        pushBeanPropertyReference(
                                classWriter,
                                staticInit,
                                beanPropertyData
                        )
                );
            }
            for (BeanFieldData beanFieldData : beanFields) {
                pushStoreInArray(staticInit, i++, size, () ->
                        pushBeanPropertyReference(
                                classWriter,
                                staticInit,
                                beanFieldData
                        )
                );
            }
            staticInit.putStatic(introspectionType, FIELD_BEAN_PROPERTIES_REFERENCES, beanPropertiesRefs);
        }
        if (!beanMethods.isEmpty()) {
            Type beanMethodsRefs = Type.getType(AbstractInitializableBeanIntrospection.BeanMethodRef[].class);

            classWriter.visitField(ACC_PRIVATE | ACC_FINAL | ACC_STATIC, FIELD_BEAN_METHODS_REFERENCES, beanMethodsRefs.getDescriptor(), null, null);
            pushNewArray(staticInit, AbstractInitializableBeanIntrospection.BeanMethodRef.class, beanMethods.size());
            int i = 0;
            for (BeanMethodData beanMethodData : beanMethods) {
                pushStoreInArray(staticInit, i++, beanMethods.size(), () ->
                        pushBeanMethodReference(
                                classWriter,
                                staticInit,
                                beanMethodData
                        )
                );
            }
            staticInit.putStatic(introspectionType, FIELD_BEAN_METHODS_REFERENCES, beanMethodsRefs);
        }

        int indexesIndex = 0;
        for (String annotationName : indexByAnnotations.keySet()) {
            int[] indexes = indexByAnnotations.get(annotationName)
                    .stream()
                    .mapToInt(prop -> getPropertyIndex(prop))
                    .toArray();

            String newIndexField = "INDEX_" + (++indexesIndex);
            Type type = Type.getType(int[].class);
            classWriter.visitField(ACC_PRIVATE | ACC_FINAL | ACC_STATIC, newIndexField, type.getDescriptor(), null, null);
            pushNewArray(staticInit, int.class, indexes.length);
            int i = 0;
            for (int index : indexes) {
                pushStoreInArray(staticInit, Type.INT_TYPE, i++, indexes.length, () -> staticInit.push(index));
            }
            staticInit.putStatic(introspectionType, newIndexField, type);
            annotationIndexFields.put(annotationName, newIndexField);
        }

        staticInit.returnValue();
        staticInit.visitMaxs(DEFAULT_MAX_STACK, 1);
        staticInit.visitEnd();
    }

    private void pushBeanPropertyReference(ClassWriter classWriter,
                                           GeneratorAdapter staticInit,
                                           BeanPropertyData beanPropertyData) {
        staticInit.newInstance(Type.getType(AbstractInitializableBeanIntrospection.BeanPropertyRef.class));
        staticInit.dup();

        pushCreateArgument(
                beanType.getClassName(),
                introspectionType,
                classWriter,
                staticInit,
                beanPropertyData.name,
                beanPropertyData.typedElement,
                beanPropertyData.annotationMetadata,
                beanPropertyData.typeArguments,
                defaults,
                loadTypeMethods
        );
        staticInit.push(beanPropertyData.getMethodDispatchIndex);
        staticInit.push(beanPropertyData.setMethodDispatchIndex);
        staticInit.push(beanPropertyData.withMethodDispatchIndex);
        staticInit.push(beanPropertyData.isReadOnly);
        staticInit.push(!beanPropertyData.isReadOnly || hasAssociatedConstructorArgument(beanPropertyData.name, beanPropertyData.typedElement));

        invokeConstructor(
                staticInit,
                AbstractInitializableBeanIntrospection.BeanPropertyRef.class,
                Argument.class,
                int.class,
                int.class,
                int.class,
                boolean.class,
                boolean.class);
    }

    private void pushBeanPropertyReference(ClassWriter classWriter,
                                           GeneratorAdapter staticInit,
                                           BeanFieldData beanFieldData) {
        staticInit.newInstance(Type.getType(AbstractInitializableBeanIntrospection.BeanPropertyRef.class));
        staticInit.dup();

        pushCreateArgument(
                beanType.getClassName(),
                introspectionType,
                classWriter,
                staticInit,
                beanFieldData.beanField.getName(),
                beanFieldData.beanField.getGenericType(),
                beanFieldData.beanField.getAnnotationMetadata(),
                beanFieldData.beanField.getGenericType().getTypeArguments(),
                defaults,
                loadTypeMethods
        );
        staticInit.push(beanFieldData.getDispatchIndex);
        staticInit.push(beanFieldData.setDispatchIndex);
        staticInit.push(-1);
        staticInit.push(beanFieldData.beanField.isFinal());
        staticInit.push(!beanFieldData.beanField.isFinal() || hasAssociatedConstructorArgument(beanFieldData.beanField.getName(), beanFieldData.beanField));

        invokeConstructor(
                staticInit,
                AbstractInitializableBeanIntrospection.BeanPropertyRef.class,
                Argument.class,
                int.class,
                int.class,
                int.class,
                boolean.class,
                boolean.class);
    }

    private void pushBeanMethodReference(ClassWriter classWriter,
                                         GeneratorAdapter staticInit,
                                         BeanMethodData beanMethodData) {
        staticInit.newInstance(Type.getType(AbstractInitializableBeanIntrospection.BeanMethodRef.class));
        staticInit.dup();
        // 1: return argument
        ClassElement genericReturnType = beanMethodData.methodElement.getGenericReturnType();
        pushReturnTypeArgument(introspectionType, classWriter, staticInit, classElement.getName(), genericReturnType, defaults, loadTypeMethods);
        // 2: name
        staticInit.push(beanMethodData.methodElement.getName());
        // 3: annotation metadata
        pushAnnotationMetadata(classWriter, staticInit, beanMethodData.methodElement.getAnnotationMetadata());
        // 4: arguments
        if (beanMethodData.methodElement.getParameters().length == 0) {
            staticInit.push((String) null);
        } else {
            pushBuildArgumentsForMethod(
                    beanType.getClassName(),
                    introspectionType,
                    classWriter,
                    staticInit,
                    Arrays.asList(beanMethodData.methodElement.getParameters()),
                    new HashMap<>(),
                    loadTypeMethods
            );
        }
        // 5: method index
        staticInit.push(beanMethodData.dispatchIndex);

        invokeConstructor(
                staticInit,
                AbstractInitializableBeanIntrospection.BeanMethodRef.class,
                Argument.class,
                String.class,
                AnnotationMetadata.class,
                Argument[].class,
                int.class);
    }

    private boolean hasAssociatedConstructorArgument(String name, TypedElement typedElement) {
        if (constructor != null) {
            ParameterElement[] parameters = constructor.getParameters();
            for (ParameterElement parameter : parameters) {
                if (name.equals(parameter.getName())) {
                    return typedElement.getType().isAssignable(parameter.getGenericType());
                }
            }
        }
        return false;
    }

    private void writeIntrospectionClass(ClassWriterOutputVisitor classWriterOutputVisitor) throws IOException {
        final Type superType = Type.getType(AbstractInitializableBeanIntrospection.class);

        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classWriter.visit(V1_8, ACC_SYNTHETIC | ACC_FINAL,
                introspectionType.getInternalName(),
                null,
                superType.getInternalName(),
                null);

        classWriter.visitAnnotation(TYPE_GENERATED.getDescriptor(), false);

        buildStaticInit(classWriter);

        final GeneratorAdapter constructorWriter = startConstructor(classWriter);

        // writer the constructor
        constructorWriter.loadThis();
        // 1st argument: The bean type
        constructorWriter.push(beanType);

        // 2nd argument: The annotation metadata
        if (annotationMetadata == null || annotationMetadata == AnnotationMetadata.EMPTY_METADATA) {
            constructorWriter.visitInsn(ACONST_NULL);
        } else {
            // retrieved from BeanIntrospectionReference.$ANNOTATION_METADATA
            constructorWriter.getStatic(
                    targetClassType,
                    AbstractAnnotationMetadataWriter.FIELD_ANNOTATION_METADATA, Type.getType(AnnotationMetadata.class));
        }

        if (constructor != null) {
            // 3rd argument: constructor metadata
            if (!constructor.getAnnotationMetadata().isEmpty()) {
                constructorWriter.getStatic(introspectionType, FIELD_CONSTRUCTOR_ANNOTATION_METADATA, Type.getType(AnnotationMetadata.class));
            } else {
                constructorWriter.push((String) null);
            }
            // 4th argument: constructor arguments
            if (ArrayUtils.isNotEmpty(constructor.getParameters())) {
                constructorWriter.getStatic(introspectionType, FIELD_CONSTRUCTOR_ARGUMENTS, Type.getType(Argument[].class));
            } else {
                constructorWriter.push((String) null);
            }
        } else {
            constructorWriter.push((String) null);
            constructorWriter.push((String) null);
        }

        if (beanProperties.isEmpty() && beanFields.isEmpty()) {
            constructorWriter.push((String) null);
        } else {
            constructorWriter.getStatic(introspectionType,
                    FIELD_BEAN_PROPERTIES_REFERENCES,
                    Type.getType(AbstractInitializableBeanIntrospection.BeanPropertyRef[].class));
        }
        if (beanMethods.isEmpty()) {
            constructorWriter.push((String) null);
        } else {
            constructorWriter.getStatic(introspectionType,
                    FIELD_BEAN_METHODS_REFERENCES,
                    Type.getType(AbstractInitializableBeanIntrospection.BeanMethodRef[].class));
        }

        invokeConstructor(
                constructorWriter,
                AbstractInitializableBeanIntrospection.class,
                Class.class,
                AnnotationMetadata.class,
                AnnotationMetadata.class,
                Argument[].class,
                AbstractInitializableBeanIntrospection.BeanPropertyRef[].class,
                AbstractInitializableBeanIntrospection.BeanMethodRef[].class
        );

        constructorWriter.returnValue();
        constructorWriter.visitMaxs(2, 1);
        constructorWriter.visitEnd();

        dispatchWriter.buildDispatchOneMethod(classWriter);
        dispatchWriter.buildDispatchMethod(classWriter);
        buildPropertyIndexOfMethod(classWriter);
        buildFindIndexedProperty(classWriter);
        buildGetIndexedProperties(classWriter);

        if (defaultConstructor != null) {
            writeInstantiateMethod(classWriter, defaultConstructor, "instantiate");
        }
        if (constructor != null && ArrayUtils.isNotEmpty(constructor.getParameters())) {
            writeInstantiateMethod(classWriter, constructor, "instantiateInternal", Object[].class);
        }

        for (GeneratorAdapter method : loadTypeMethods.values()) {
            method.visitMaxs(3, 1);
            method.visitEnd();
        }

        classWriter.visitEnd();

        try (OutputStream outputStream = classWriterOutputVisitor.visitClass(introspectionName, getOriginatingElements())) {
            outputStream.write(classWriter.toByteArray());
        }
    }

    private void buildPropertyIndexOfMethod(ClassWriter classWriter) {
        GeneratorAdapter findMethod = new GeneratorAdapter(classWriter.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                PROPERTY_INDEX_OF.getName(),
                PROPERTY_INDEX_OF.getDescriptor(),
                null,
                null),
                ACC_PUBLIC | Opcodes.ACC_FINAL,
                PROPERTY_INDEX_OF.getName(),
                PROPERTY_INDEX_OF.getDescriptor()
        );
        new StringSwitchWriter() {

            @Override
            protected Set<String> getKeys() {
                Set<String> keys = new HashSet<>();
                for (BeanPropertyData prop : beanProperties) {
                    keys.add(prop.name);
                }
                for (BeanFieldData field : beanFields) {
                    keys.add(field.beanField.getName());
                }
                return keys;
            }

            @Override
            protected void pushStringValue() {
                findMethod.loadArg(0);
            }

            @Override
            protected void onMatch(String value, Label end) {
                findMethod.loadThis();
                findMethod.push(getPropertyIndex(value));
                findMethod.returnValue();
            }

        }.write(findMethod);
        findMethod.push(-1);
        findMethod.returnValue();
        findMethod.visitMaxs(DEFAULT_MAX_STACK, 1);
        findMethod.visitEnd();
    }

    private void buildFindIndexedProperty(ClassWriter classWriter) {
        if (indexByAnnotationAndValue.isEmpty()) {
            return;
        }
        GeneratorAdapter writer = new GeneratorAdapter(classWriter.visitMethod(
                Opcodes.ACC_PROTECTED | Opcodes.ACC_FINAL,
                FIND_INDEXED_PROPERTY_METHOD.getName(),
                FIND_INDEXED_PROPERTY_METHOD.getDescriptor(),
                null,
                null),
                ACC_PROTECTED | Opcodes.ACC_FINAL,
                FIND_INDEXED_PROPERTY_METHOD.getName(),
                FIND_INDEXED_PROPERTY_METHOD.getDescriptor()
        );
        writer.loadThis();
        writer.loadArg(0);
        writer.invokeVirtual(Type.getType(Class.class), new Method("getName", Type.getType(String.class), new Type[]{}));
        int classNameLocal = writer.newLocal(Type.getType(String.class));
        writer.storeLocal(classNameLocal);
        writer.loadLocal(classNameLocal);

        new StringSwitchWriter() {

            @Override
            protected Set<String> getKeys() {
                return indexByAnnotationAndValue.keySet()
                        .stream()
                        .map(s -> s.annotationName)
                        .collect(Collectors.toSet());
            }

            @Override
            protected void pushStringValue() {
                writer.loadLocal(classNameLocal);
            }

            @Override
            protected void onMatch(String annotationName, Label end) {
                if (indexByAnnotationAndValue.keySet().stream().anyMatch(s -> s.annotationName.equals(annotationName) && s.value == null)) {
                    Label falseLabel = new Label();
                    writer.loadArg(1);
                    writer.ifNonNull(falseLabel);

                    String propertyName = indexByAnnotationAndValue.get(new AnnotationWithValue(annotationName, null));
                    int propertyIndex = getPropertyIndex(propertyName);
                    writer.loadThis();
                    writer.push(propertyIndex);
                    writer.invokeVirtual(introspectionType, FIND_PROPERTY_BY_INDEX_METHOD);
                    writer.returnValue();

                    writer.visitLabel(falseLabel);
                } else {
                    Label falseLabel = new Label();
                    writer.loadArg(1);
                    writer.ifNonNull(falseLabel);
                    writer.goTo(end);
                    writer.visitLabel(falseLabel);
                }
                Set<String> valueMatches = indexByAnnotationAndValue.keySet()
                        .stream()
                        .filter(s -> s.annotationName.equals(annotationName) && s.value != null)
                        .map(s -> s.value)
                        .collect(Collectors.toSet());
                if (!valueMatches.isEmpty()) {
                    new StringSwitchWriter() {

                        @Override
                        protected Set<String> getKeys() {
                            return valueMatches;
                        }

                        @Override
                        protected void pushStringValue() {
                            writer.loadArg(1);
                        }

                        @Override
                        protected void onMatch(String value, Label end) {
                            String propertyName = indexByAnnotationAndValue.get(new AnnotationWithValue(annotationName, value));
                            int propertyIndex = getPropertyIndex(propertyName);
                            writer.loadThis();
                            writer.push(propertyIndex);
                            writer.invokeVirtual(introspectionType, FIND_PROPERTY_BY_INDEX_METHOD);
                            writer.returnValue();
                        }

                    }.write(writer);
                }
                writer.goTo(end);
            }

        }.write(writer);

        writer.push((String) null);
        writer.returnValue();
        writer.visitMaxs(DEFAULT_MAX_STACK, 1);
        writer.visitEnd();
    }

    private void buildGetIndexedProperties(ClassWriter classWriter) {
        if (indexByAnnotations.isEmpty()) {
            return;
        }
        GeneratorAdapter writer = new GeneratorAdapter(classWriter.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                GET_INDEXED_PROPERTIES.getName(),
                GET_INDEXED_PROPERTIES.getDescriptor(),
                null,
                null),
                ACC_PUBLIC | Opcodes.ACC_FINAL,
                GET_INDEXED_PROPERTIES.getName(),
                GET_INDEXED_PROPERTIES.getDescriptor()
        );
        writer.loadThis();
        writer.loadArg(0);
        writer.invokeVirtual(Type.getType(Class.class), new Method("getName", Type.getType(String.class), new Type[]{}));
        int classNameLocal = writer.newLocal(Type.getType(String.class));
        writer.storeLocal(classNameLocal);
        writer.loadLocal(classNameLocal);

        new StringSwitchWriter() {

            @Override
            protected Set<String> getKeys() {
                return indexByAnnotations.keySet();
            }

            @Override
            protected void pushStringValue() {
                writer.loadLocal(classNameLocal);
            }

            @Override
            protected void onMatch(String annotationName, Label end) {
                writer.loadThis();
                writer.getStatic(introspectionType, annotationIndexFields.get(annotationName), Type.getType(int[].class));
                writer.invokeVirtual(introspectionType, GET_BP_INDEXED_SUBSET_METHOD);
                writer.returnValue();
            }

        }.write(writer);

        writer.invokeStatic(Type.getType(Collections.class), COLLECTIONS_EMPTY_LIST);
        writer.returnValue();
        writer.visitMaxs(DEFAULT_MAX_STACK, 1);
        writer.visitEnd();
    }

    private int getPropertyIndex(String propertyName) {
        BeanPropertyData beanPropertyData = beanProperties.stream().filter(bp -> bp.name.equals(propertyName)).findFirst().orElse(null);
        if (beanPropertyData != null) {
            return beanProperties.indexOf(beanPropertyData);
        }
        BeanFieldData beanFieldData = beanFields.stream().filter(f -> f.beanField.getName().equals(propertyName)).findFirst().orElse(null);
        if (beanFieldData != null) {
            return beanProperties.size() + beanFields.indexOf(beanFieldData);
        }
        throw new IllegalStateException("Property not found: " + propertyName + " " + classElement.getName());
    }

    private void writeInstantiateMethod(ClassWriter classWriter, MethodElement constructor, String methodName, Class... args) {
        final String desc = getMethodDescriptor(Object.class, Arrays.asList(args));
        final GeneratorAdapter instantiateInternal = new GeneratorAdapter(classWriter.visitMethod(
                ACC_PUBLIC,
                methodName,
                desc,
                null,
                null
        ), ACC_PUBLIC,
                methodName,
                desc);

        invokeBeanConstructor(instantiateInternal, constructor, (writer, con) -> {
            List<ParameterElement> constructorArguments = Arrays.asList(con.getParameters());
            Collection<Type> argumentTypes = constructorArguments.stream().map(pe ->
                    JavaModelUtils.getTypeReference(pe.getType())
            ).collect(Collectors.toList());

            int i = 0;
            for (Type argumentType : argumentTypes) {
                writer.loadArg(0);
                writer.push(i++);
                writer.arrayLoad(TYPE_OBJECT);
                pushCastToType(writer, argumentType);
            }

        });

        instantiateInternal.returnValue();
        instantiateInternal.visitMaxs(3, 1);
        instantiateInternal.visitEnd();
    }

    private void invokeBeanConstructor(GeneratorAdapter writer, MethodElement constructor, BiConsumer<GeneratorAdapter, MethodElement> argumentsPusher) {
        boolean isConstructor = constructor instanceof ConstructorElement;
        boolean isCompanion = constructor != defaultConstructor && constructor.getDeclaringType().getSimpleName().endsWith("$Companion");

        List<ParameterElement> constructorArguments = Arrays.asList(constructor.getParameters());
        Collection<Type> argumentTypes = constructorArguments.stream().map(pe ->
                JavaModelUtils.getTypeReference(pe.getType())
        ).collect(Collectors.toList());

        if (isConstructor) {
            writer.newInstance(beanType);
            writer.dup();
        } else if (isCompanion) {
            writer.getStatic(beanType, "Companion", JavaModelUtils.getTypeReference(constructor.getDeclaringType()));
        }

        argumentsPusher.accept(writer, constructor);

        if (isConstructor) {
            final String constructorDescriptor = getConstructorDescriptor(constructorArguments);
            writer.invokeConstructor(beanType, new Method("<init>", constructorDescriptor));
        } else if (constructor.isStatic()) {
            final String methodDescriptor = getMethodDescriptor(beanType, argumentTypes);
            Method method = new Method(constructor.getName(), methodDescriptor);
            if (classElement.isInterface()) {
                writer.visitMethodInsn(Opcodes.INVOKESTATIC, beanType.getInternalName(), method.getName(),
                        method.getDescriptor(), true);
            } else {
                writer.invokeStatic(beanType, method);
            }
        } else if (isCompanion) {
            writer.invokeVirtual(JavaModelUtils.getTypeReference(constructor.getDeclaringType()), new Method(constructor.getName(), getMethodDescriptor(beanType, argumentTypes)));
        }
    }

    private void writeIntrospectionReference(ClassWriterOutputVisitor classWriterOutputVisitor) throws IOException {
        Type superType = Type.getType(AbstractBeanIntrospectionReference.class);
        final String referenceName = targetClassType.getClassName();
        classWriterOutputVisitor.visitServiceDescriptor(BeanIntrospectionReference.class, referenceName, getOriginatingElement());

        try (OutputStream referenceStream = classWriterOutputVisitor.visitClass(referenceName, getOriginatingElements())) {
            startService(referenceWriter, BeanIntrospectionReference.class, targetClassType.getInternalName(), superType);
            final ClassWriter classWriter = generateClassBytes(referenceWriter);
            for (GeneratorAdapter generatorAdapter : loadTypeMethods.values()) {
                generatorAdapter.visitMaxs(1, 1);
                generatorAdapter.visitEnd();
            }
            referenceStream.write(classWriter.toByteArray());
        }
    }

    private ClassWriter generateClassBytes(ClassWriter classWriter) {
        writeAnnotationMetadataStaticInitializer(classWriter, new HashMap<>());

        GeneratorAdapter cv = startConstructor(classWriter);
        cv.loadThis();
        invokeConstructor(cv, AbstractBeanIntrospectionReference.class);
        cv.returnValue();
        cv.visitMaxs(2, 1);

        // start method: BeanIntrospection load()
        GeneratorAdapter loadMethod = startPublicMethodZeroArgs(classWriter, BeanIntrospection.class, "load");

        // return new BeanIntrospection()
        pushNewInstance(loadMethod, this.introspectionType);

        loadMethod.returnValue();
        loadMethod.visitMaxs(2, 1);
        loadMethod.endMethod();

        // start method: String getName()
        final GeneratorAdapter nameMethod = startPublicMethodZeroArgs(classWriter, String.class, "getName");
        nameMethod.push(beanType.getClassName());
        nameMethod.returnValue();
        nameMethod.visitMaxs(1, 1);
        nameMethod.endMethod();

        // start method: Class getBeanType()
        GeneratorAdapter getBeanType = startPublicMethodZeroArgs(classWriter, Class.class, "getBeanType");
        getBeanType.push(beanType);
        getBeanType.returnValue();
        getBeanType.visitMaxs(2, 1);
        getBeanType.endMethod();

        writeGetAnnotationMetadataMethod(classWriter);
        return classWriter;
    }

    private void pushAnnotationMetadata(ClassWriter classWriter, GeneratorAdapter staticInit, AnnotationMetadata annotationMetadata) {
        annotationMetadata = annotationMetadata.unwrap();
        if (annotationMetadata.isEmpty()) {
            staticInit.push((String) null);
        } else if (annotationMetadata instanceof AnnotationMetadataReference) {
            AnnotationMetadataReference reference = (AnnotationMetadataReference) annotationMetadata;
            String className = reference.getClassName();
            staticInit.getStatic(getTypeReferenceForName(className), AbstractAnnotationMetadataWriter.FIELD_ANNOTATION_METADATA, Type.getType(AnnotationMetadata.class));
        } else if (annotationMetadata instanceof AnnotationMetadataHierarchy) {
            AnnotationMetadataWriter.instantiateNewMetadataHierarchy(
                    introspectionType,
                    classWriter,
                    staticInit,
                    (AnnotationMetadataHierarchy) annotationMetadata,
                    defaults,
                    loadTypeMethods);
        } else if (annotationMetadata instanceof DefaultAnnotationMetadata) {
            AnnotationMetadataWriter.instantiateNewMetadata(
                    introspectionType,
                    classWriter,
                    staticInit,
                    (DefaultAnnotationMetadata) annotationMetadata,
                    defaults,
                    loadTypeMethods);
        } else {
            throw new IllegalStateException("Unknown annotation metadata:  " + annotationMetadata);
        }
    }

    @NonNull
    private static String computeReferenceName(String className) {
        String packageName = NameUtils.getPackageName(className);
        final String shortName = NameUtils.getSimpleName(className);
        return packageName + ".$" + shortName + REFERENCE_SUFFIX;
    }

    @NonNull
    private static String computeIntrospectionName(String className) {
        String packageName = NameUtils.getPackageName(className);
        final String shortName = NameUtils.getSimpleName(className);
        return packageName + ".$" + shortName + INTROSPECTION_SUFFIX;
    }

    @NonNull
    private static String computeIntrospectionName(String generatingName, String className) {
        final String packageName = NameUtils.getPackageName(generatingName);
        return packageName + ".$" + className.replace('.', '_') + INTROSPECTION_SUFFIX;
    }

    /**
     * Visit the constructor. If any.
     *
     * @param constructor The constructor method
     */
    void visitConstructor(MethodElement constructor) {
        this.constructor = constructor;
    }

    /**
     * Visit the default constructor. If any.
     *
     * @param constructor The constructor method
     */
    void visitDefaultConstructor(MethodElement constructor) {
        this.defaultConstructor = constructor;
    }

    private static final class ExceptionDispatchTarget implements DispatchWriter.DispatchTarget {

        private final Class<?> exceptionType;
        private final String message;

        private ExceptionDispatchTarget(Class<?> exceptionType, String message) {
            this.exceptionType = exceptionType;
            this.message = message;
        }

        @Override
        public boolean supportsDispatchOne() {
            return true;
        }

        @Override
        public void writeDispatchOne(GeneratorAdapter writer) {
            writer.throwException(Type.getType(exceptionType), message);
        }
    }

    /**
     * Copy constructor "with" method writer.
     */
    private final class CopyConstructorDispatchTarget implements DispatchWriter.DispatchTarget {

        private final MethodElement constructor;
        private final String parameterName;

        private CopyConstructorDispatchTarget(MethodElement constructor, String name) {
            this.constructor = constructor;
            this.parameterName = name;
        }

        @Override
        public boolean supportsDispatchOne() {
            return true;
        }

        @Override
        public void writeDispatchOne(GeneratorAdapter writer) {
            // In this case we have to do the copy constructor approach
            Set<BeanPropertyData> constructorProps = new HashSet<>();

            boolean isMutable = true;
            String nonMutableMessage = null;
            ParameterElement[] parameters = constructor.getParameters();
            Object[] constructorArguments = new Object[parameters.length];

            for (int i = 0; i < parameters.length; i++) {
                ParameterElement parameter = parameters[i];
                String parameterName = parameter.getName();

                if (this.parameterName.equals(parameterName)) {
                    constructorArguments[i] = this;
                    continue;
                }

                BeanPropertyData prop = beanProperties.stream()
                        .filter(bp -> bp.name.equals(parameterName))
                        .findAny().orElse(null);

                int getMethodDispatchIndex = prop == null ? -1 : prop.getMethodDispatchIndex;
                if (getMethodDispatchIndex != -1) {
                    DispatchWriter.MethodDispatchTarget methodDispatchTarget = (DispatchWriter.MethodDispatchTarget) dispatchWriter.getDispatchTargets().get(getMethodDispatchIndex);
                    MethodElement readMethod = methodDispatchTarget.getMethodElement();
                    if (readMethod.getGenericReturnType().isAssignable(parameter.getGenericType())) {
                        constructorArguments[i] = readMethod;
                        constructorProps.add(prop);
                    } else {
                        isMutable = false;
                        nonMutableMessage = "Cannot create copy of type [" + beanType.getClassName() + "]. Property of type [" + readMethod.getGenericReturnType().getName() + "] is not assignable to constructor argument [" + parameterName + "]";
                    }
                } else {
                    isMutable = false;
                    nonMutableMessage = "Cannot create copy of type [" + beanType.getClassName() + "]. Constructor contains argument [" + parameterName + "] that is not a readable property";
                    break;
                }
            }

            if (isMutable) {

                writer.loadArg(1);
                pushCastToType(writer, beanType);
                int prevBeanTypeLocal = writer.newLocal(beanType);
                writer.storeLocal(prevBeanTypeLocal, beanType);

                invokeBeanConstructor(writer, constructor, (constructorWriter, constructor) -> {
                    for (int i = 0; i < parameters.length; i++) {
                        ParameterElement parameter = parameters[i];
                        Object constructorArgument = constructorArguments[i];
                        if (constructorArgument == this) {
                            constructorWriter.loadArg(2);
                            pushCastToType(constructorWriter, parameter);
                            if (!parameter.isPrimitive()) {
                                pushBoxPrimitiveIfNecessary(parameter, constructorWriter);
                            }
                        } else {
                            MethodElement readMethod = (MethodElement) constructorArgument;
                            constructorWriter.loadLocal(prevBeanTypeLocal, beanType);
                            invokeMethod(constructorWriter, readMethod);
                        }
                    }
                });

                List<BeanPropertyData> readWriteProps = beanProperties.stream()
                        .filter(bp -> bp.setMethodDispatchIndex != -1 && bp.getMethodDispatchIndex != -1 && !constructorProps.contains(bp))
                        .collect(Collectors.toList());

                if (!readWriteProps.isEmpty()) {
                    int beanTypeLocal = writer.newLocal(beanType);
                    writer.storeLocal(beanTypeLocal, beanType);

                    for (BeanPropertyData readWriteProp : readWriteProps) {

                        MethodElement writeMethod = ((DispatchWriter.MethodDispatchTarget) dispatchWriter
                                .getDispatchTargets().get(readWriteProp.setMethodDispatchIndex)).getMethodElement();
                        MethodElement readMethod = ((DispatchWriter.MethodDispatchTarget) dispatchWriter
                                .getDispatchTargets().get(readWriteProp.getMethodDispatchIndex)).getMethodElement();

                        writer.loadLocal(beanTypeLocal, beanType);
                        writer.loadLocal(prevBeanTypeLocal, beanType);
                        invokeMethod(writer, readMethod);
                        ClassElement writeReturnType = invokeMethod(writer, writeMethod);
                        if (!writeReturnType.getName().equals("void")) {
                            writer.pop();
                        }
                    }
                    writer.loadLocal(beanTypeLocal, beanType);
                }
            } else {
                // In this case the bean cannot be mutated via either copy constructor or setter so simply throw an exception
                writer.throwException(Type.getType(UnsupportedOperationException.class), nonMutableMessage);
            }
        }

        @NonNull
        private ClassElement invokeMethod(GeneratorAdapter mutateMethod, MethodElement method) {
            ClassElement returnType = method.getReturnType();
            if (classElement.isInterface()) {
                mutateMethod.invokeInterface(beanType, new Method(method.getName(), getMethodDescriptor(returnType, Arrays.asList(method.getParameters()))));
            } else {
                mutateMethod.invokeVirtual(beanType, new Method(method.getName(), getMethodDescriptor(returnType, Arrays.asList(method.getParameters()))));
            }
            return returnType;
        }
    }

    private static final class BeanFieldData {
        @NotNull
        final FieldElement beanField;

        final int getDispatchIndex;
        final int setDispatchIndex;

        private BeanFieldData(FieldElement beanField,
                              int getDispatchIndex,
                              int setDispatchIndex) {
            this.beanField = beanField;
            this.getDispatchIndex = getDispatchIndex;
            this.setDispatchIndex = setDispatchIndex;
        }
    }

    private static final class BeanMethodData {
        @NotNull
        final MethodElement methodElement;

        final int dispatchIndex;

        private BeanMethodData(MethodElement methodElement,
                               int dispatchIndex) {
            this.methodElement = methodElement;
            this.dispatchIndex = dispatchIndex;
        }
    }

    private static final class BeanPropertyData {
        @NonNull
        final TypedElement typedElement;
        @NonNull
        final String name;
        final AnnotationMetadata annotationMetadata;
        @Nullable
        final Map<String, ClassElement> typeArguments;

        final int getMethodDispatchIndex;
        final int setMethodDispatchIndex;
        final int withMethodDispatchIndex;
        final boolean isReadOnly;

        private BeanPropertyData(@NonNull TypedElement typedElement,
                                 @NonNull String name,
                                 @Nullable AnnotationMetadata annotationMetadata,
                                 @Nullable Map<String, ClassElement> typeArguments,
                                 int getMethodDispatchIndex,
                                 int setMethodDispatchIndex,
                                 int withMethodDispatchIndex,
                                 boolean isReadOnly) {
            this.typedElement = typedElement;
            this.name = name;
            this.annotationMetadata = annotationMetadata == null ? AnnotationMetadata.EMPTY_METADATA : annotationMetadata;
            this.typeArguments = typeArguments;
            this.getMethodDispatchIndex = getMethodDispatchIndex;
            this.setMethodDispatchIndex = setMethodDispatchIndex;
            this.withMethodDispatchIndex = withMethodDispatchIndex;
            this.isReadOnly = isReadOnly;
        }
    }

    /**
     * index to be created.
     */
    private static final class AnnotationWithValue {
        @NonNull
        final String annotationName;
        @Nullable
        final String value;

        private AnnotationWithValue(@NonNull String annotationName, @Nullable String value) {
            this.annotationName = annotationName;
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            AnnotationWithValue that = (AnnotationWithValue) o;
            return annotationName.equals(that.annotationName) && Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(annotationName, value);
        }
    }
}
