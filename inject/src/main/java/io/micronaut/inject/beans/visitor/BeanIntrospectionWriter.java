/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.inject.beans.visitor;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospectionReference;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.reflect.exception.InstantiationException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.inject.annotation.DefaultAnnotationMetadata;
import io.micronaut.inject.ast.*;
import io.micronaut.core.beans.AbstractBeanIntrospection;
import io.micronaut.core.beans.AbstractBeanIntrospectionReference;
import io.micronaut.inject.writer.AbstractAnnotationMetadataWriter;
import io.micronaut.inject.writer.ClassWriterOutputVisitor;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A class file writer that writes a {@link io.micronaut.core.beans.BeanIntrospectionReference} and associated
 * {@link io.micronaut.core.beans.BeanIntrospection} for the given class.
 *
 * @author graemerocher
 * @since 1.1
 */
@Internal
class BeanIntrospectionWriter extends AbstractAnnotationMetadataWriter {
    private static final Method METHOD_ADD_PROPERTY = Method.getMethod(ReflectionUtils.getRequiredInternalMethod(AbstractBeanIntrospection.class, "addProperty", BeanProperty.class));
    private static final Method METHOD_INDEX_PROPERTY = Method.getMethod(ReflectionUtils.getRequiredInternalMethod(AbstractBeanIntrospection.class, "indexProperty", Class.class, String.class, String.class));
    private static final String REFERENCE_SUFFIX = "$IntrospectionRef";
    private static final String INTROSPECTION_SUFFIX = "$Introspection";

    private final ClassWriter referenceWriter;
    private final String introspectionName;
    private final Type introspectionType;
    private final Type beanType;
    private final ClassWriter introspectionWriter;
    private final List<BeanPropertyWriter> propertyDefinitions = new ArrayList<>();
    private final Map<String, Collection<AnnotationValueIndex>> indexes = new HashMap<>(2);
    private final Map<String, GeneratorAdapter> localLoadTypeMethods = new HashMap<>();
    private final ClassElement classElement;
    private int propertyIndex = 0;
    private MethodElement constructor;
    private MethodElement defaultConstructor;

    /**
     * Default constructor.
     * @param classElement The class element
     * @param beanAnnotationMetadata The bean annotation metadata
     */
    BeanIntrospectionWriter(ClassElement classElement, AnnotationMetadata beanAnnotationMetadata) {
        super(computeReferenceName(classElement.getName()), beanAnnotationMetadata, true);
        final String name = classElement.getName();
        this.classElement = classElement;
        this.referenceWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        this.introspectionWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        this.introspectionName = computeIntrospectionName(name);
        this.introspectionType = getTypeReference(introspectionName);
        this.beanType = getTypeReference(name);
    }

    /**
     * Constructor used to generate a reference for already compiled classes.
     * @param generatingType The originating type
     * @param index A unique index
     * @param classElement The class element
     * @param beanAnnotationMetadata The bean annotation metadata
     */
    BeanIntrospectionWriter(String generatingType, int index, ClassElement classElement, AnnotationMetadata beanAnnotationMetadata) {
        super(computeReferenceName(generatingType) + index, beanAnnotationMetadata, true);
        final String className = classElement.getName();
        this.classElement = classElement;
        this.referenceWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        this.introspectionWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        this.introspectionName = computeIntrospectionName(generatingType, className);
        this.introspectionType = getTypeReference(introspectionName);
        this.beanType = getTypeReference(className);
    }

    /**
     * @return The class element
     */
    ClassElement getClassElement() {
        return classElement;
    }

    /**
     * The instropection type.
     * @return The type
     */
    Type getIntrospectionType() {
        return introspectionType;
    }

    /**
     * The bean type.
     * @return The bean type
     */
    public Type getBeanType() {
        return beanType;
    }

    /**
     * Visit a property.
     *
     * @param type The property type
     * @param name The property name
     * @param readMethod The read method
     * @param writeMethod The write methodname
     * @param isReadOnly Is the property read only
     * @param annotationMetadata The property annotation metadata
     * @param typeArguments The type arguments
     */
    void visitProperty(
            @Nonnull TypedElement type,
            @Nonnull String name,
            @Nullable MethodElement readMethod,
            @Nullable MethodElement writeMethod,
            boolean isReadOnly,
            @Nullable AnnotationMetadata annotationMetadata,
            @Nullable Map<String, ClassElement> typeArguments) {

        final Type propertyType = getTypeForElement(type);

        DefaultAnnotationMetadata.contributeDefaults(
                this.annotationMetadata,
                annotationMetadata
        );
        propertyDefinitions.add(
                new BeanPropertyWriter(
                        this,
                        type,
                        propertyType,
                        name,
                        readMethod,
                        writeMethod,
                        isReadOnly,
                        propertyIndex++,
                        annotationMetadata,
                        typeArguments
        ));
    }

    /**
     * Builds an index for the given property and annotation.
     *
     * @param annotation The annotation
     * @param property The property
     * @param value the value of the annotation
     */
    void indexProperty(AnnotationValue<?> annotation, String property, @Nullable String value) {
        indexes.computeIfAbsent(property, s -> new HashSet<>(2)).add(new AnnotationValueIndex(annotation, property, value));
    }

    @Override
    public void accept(ClassWriterOutputVisitor classWriterOutputVisitor) throws IOException {
        // write the reference
        writeIntrospectionReference(classWriterOutputVisitor);
        // write the introspection
        writeIntrospectionClass(classWriterOutputVisitor);
    }

    private void writeIntrospectionClass(ClassWriterOutputVisitor classWriterOutputVisitor) throws IOException {
        final Type superType = Type.getType(AbstractBeanIntrospection.class);

        try (OutputStream introspectionStream = classWriterOutputVisitor.visitClass(introspectionName)) {

            startFinalClass(introspectionWriter, introspectionType.getInternalName(), superType);
            final GeneratorAdapter constructorWriter = startConstructor(introspectionWriter);

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

            // 3rd argument: The number of properties
            constructorWriter.push(propertyDefinitions.size());

            invokeConstructor(
                    constructorWriter,
                    AbstractBeanIntrospection.class,
                    Class.class,
                    AnnotationMetadata.class,
                    int.class);

            // process the properties, creating them etc.
            for (BeanPropertyWriter propertyWriter : propertyDefinitions) {
                propertyWriter.accept(classWriterOutputVisitor);
                final Type writerType = propertyWriter.getType();
                constructorWriter.loadThis();
                constructorWriter.newInstance(writerType);
                constructorWriter.dup();
                constructorWriter.loadThis();
                constructorWriter.invokeConstructor(writerType, new Method(CONSTRUCTOR_NAME, getConstructorDescriptor(BeanIntrospection.class)));
                constructorWriter.visitMethodInsn(INVOKESPECIAL,
                        superType.getInternalName(),
                        METHOD_ADD_PROPERTY.getName(),
                        METHOD_ADD_PROPERTY.getDescriptor(),
                        false);

                final String propertyName = propertyWriter.getPropertyName();
                if (indexes.containsKey(propertyName)) {
                    final Collection<AnnotationValueIndex> annotations = indexes.get(propertyName);
                    for (AnnotationValueIndex index : annotations) {
                        constructorWriter.loadThis();
                        final Type typeReference = getTypeReference(index.annotationValue.getAnnotationName());
                        constructorWriter.push(typeReference);
                        constructorWriter.push(propertyName);
                        constructorWriter.push(index.value);
                        constructorWriter.visitMethodInsn(INVOKESPECIAL,
                                superType.getInternalName(),
                                METHOD_INDEX_PROPERTY.getName(),
                                METHOD_INDEX_PROPERTY.getDescriptor(),
                                false);

                    }
                }
            }

            // RETURN
            constructorWriter.visitInsn(RETURN);
            // MAXSTACK = 2
            // MAXLOCALS = 1
            constructorWriter.visitMaxs(2, 1);


            // write the instantiate method
            writeInstantiateMethod();

            // write constructor arguments
            if (constructor != null && ArrayUtils.isNotEmpty(constructor.getParameters())) {
                writeConstructorArguments();
            }

            for (GeneratorAdapter generatorAdapter : localLoadTypeMethods.values()) {
                generatorAdapter.visitMaxs(1, 1);
                generatorAdapter.visitEnd();
            }
            introspectionStream.write(introspectionWriter.toByteArray());
        }
    }

    private void writeConstructorArguments() {
        final GeneratorAdapter getConstructorArguments = startPublicMethodZeroArgs(introspectionWriter, Argument[].class, "getConstructorArguments");
        ParameterElement[] constructorArguments = constructor.getParameters();
        final Map<String, Object> args = toParameterTypes(constructorArguments);
        Map<String, AnnotationMetadata> annotationMetadataMap = new LinkedHashMap<>(args.size());
        for (ParameterElement constructorArgument : constructorArguments) {
            annotationMetadataMap.put(constructorArgument.getName(), constructorArgument.getAnnotationMetadata());
        }
        pushBuildArgumentsForMethod(
                introspectionType,
                introspectionWriter,
                getConstructorArguments,
                args,
                annotationMetadataMap,
                toTypeArguments(constructorArguments),
                localLoadTypeMethods);

        getConstructorArguments.returnValue();
        getConstructorArguments.visitMaxs(1, 1);
        getConstructorArguments.endMethod();

        final String desc = getMethodDescriptor(Object.class, Collections.singleton(Object[].class));
        final GeneratorAdapter instantiateInternal = new GeneratorAdapter(introspectionWriter.visitMethod(
                ACC_PUBLIC,
                "instantiateInternal",
                desc,
                null,
                null
        ), ACC_PUBLIC,
                "instantiateInternal",
                desc);

        Collection<Type> argumentTypes = Arrays.stream(constructorArguments).map(pe ->
            getTypeForElement(pe.getType())
        ).collect(Collectors.toList());

        boolean isConstructor = constructor instanceof ConstructorElement;
        boolean isCompanion = constructor.getDeclaringType().getSimpleName().endsWith("$Companion");

        if (isConstructor) {
            instantiateInternal.newInstance(beanType);
            instantiateInternal.dup();
        } else if (isCompanion) {
            instantiateInternal.getStatic(beanType, "Companion", getTypeReference(constructor.getDeclaringType().getName()));
        }

        int i = 0;
        for (Type argumentType : argumentTypes) {
            instantiateInternal.loadArg(0);
            instantiateInternal.push(i++);
            instantiateInternal.arrayLoad(TYPE_OBJECT);
            pushCastToType(instantiateInternal, argumentType);
        }

        if (isConstructor) {
            final String constructorDescriptor = getConstructorDescriptor((Collection) argumentTypes);
            instantiateInternal.invokeConstructor(beanType, new Method("<init>", constructorDescriptor));
        } else if (constructor.isStatic()) {
            final String methodDescriptor = getMethodDescriptor(beanType, (Collection) argumentTypes);
            instantiateInternal.invokeStatic(beanType, new Method(constructor.getName(), methodDescriptor));
        } else if (isCompanion) {
            instantiateInternal.invokeVirtual(getTypeReference(constructor.getDeclaringType().getName()), new Method(constructor.getName(), getMethodDescriptor(beanType, (Collection) argumentTypes)));
        }

        instantiateInternal.visitInsn(ARETURN);
        instantiateInternal.visitMaxs(2, 1);
        instantiateInternal.visitEnd();

    }

    private void writeInstantiateMethod() {
        final GeneratorAdapter instantiateMethod = startPublicMethod(introspectionWriter, "instantiate", Object.class.getName());
        if (defaultConstructor != null) {
            if (defaultConstructor instanceof ConstructorElement) {
                pushNewInstance(instantiateMethod, beanType);
            } else if (defaultConstructor.isStatic()) {
                final String methodDescriptor = getMethodDescriptor(beanType, Collections.emptyList());
                instantiateMethod.invokeStatic(beanType, new Method(defaultConstructor.getName(), methodDescriptor));
            } else if (constructor.getDeclaringType().getSimpleName().endsWith("$Companion")) {
                instantiateMethod.getStatic(beanType, "Companion", getTypeReference(constructor.getDeclaringType().getName()));
                instantiateMethod.invokeVirtual(getTypeReference(constructor.getDeclaringType().getName()), new Method(constructor.getName(), getMethodDescriptor(beanType, Collections.emptyList())));
            }

            instantiateMethod.visitInsn(ARETURN);
            instantiateMethod.visitMaxs(2, 1);
            instantiateMethod.visitEnd();
        } else {
            Type exceptionType = Type.getType(InstantiationException.class);
            instantiateMethod.newInstance(exceptionType);
            instantiateMethod.dup();
            instantiateMethod.visitLdcInsn("No default constructor exists");
            instantiateMethod.invokeConstructor(exceptionType, Method.getMethod(
                    ReflectionUtils.getRequiredInternalConstructor(
                            InstantiationException.class,
                            String.class
                    )
            ));
            instantiateMethod.throwException();
            instantiateMethod.visitMaxs(3, 1);
            instantiateMethod.visitEnd();
        }
    }

    private void writeIntrospectionReference(ClassWriterOutputVisitor classWriterOutputVisitor) throws IOException {

        Type superType = Type.getType(AbstractBeanIntrospectionReference.class);
        final String referenceName = targetClassType.getClassName();
        classWriterOutputVisitor.visitServiceDescriptor(BeanIntrospectionReference.class, referenceName);

        try (OutputStream referenceStream = classWriterOutputVisitor.visitClass(referenceName)) {
            startPublicFinalClass(referenceWriter, targetClassType.getInternalName(), superType);
            final ClassWriter classWriter = generateClassBytes(referenceWriter);
            for (GeneratorAdapter generatorAdapter : loadTypeMethods.values()) {
                generatorAdapter.visitMaxs(1, 1);
                generatorAdapter.visitEnd();
            }
            referenceStream.write(classWriter.toByteArray());
        }
    }

    private ClassWriter generateClassBytes(ClassWriter classWriter) {
        writeAnnotationMetadataStaticInitializer(classWriter);

        GeneratorAdapter cv = startConstructor(classWriter);

        // ALOAD 0
        cv.loadThis();

        // INVOKESPECIAL AbstractBeanIntrospectionReference.<init>
        invokeConstructor(cv, AbstractBeanIntrospectionReference.class);

        // RETURN
        cv.visitInsn(RETURN);
        // MAXSTACK = 2
        // MAXLOCALS = 1
        cv.visitMaxs(2, 1);

        // start method: BeanIntrospection load()
        GeneratorAdapter loadMethod = startPublicMethodZeroArgs(classWriter, BeanIntrospection.class, "load");

        // return new BeanIntrospection()
        pushNewInstance(loadMethod, this.introspectionType);

        // RETURN
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

    @NotNull
    private static String computeReferenceName(String className) {
        String packageName = NameUtils.getPackageName(className);
        final String shortName = NameUtils.getSimpleName(className);
        return packageName + ".$" + shortName + REFERENCE_SUFFIX;
    }

    @NotNull
    private static String computeIntrospectionName(String className) {
        String packageName = NameUtils.getPackageName(className);
        final String shortName = NameUtils.getSimpleName(className);
        return packageName + ".$" + shortName + INTROSPECTION_SUFFIX;
    }

    @NotNull
    private static String computeIntrospectionName(String generatingName, String className) {
        final String packageName = NameUtils.getPackageName(generatingName);
        return packageName + ".$" + className.replace('.', '_') + INTROSPECTION_SUFFIX;
    }

    /**
     * Visit the constructor. If any.
     * @param constructor The constructor method
     */
    void visitConstructor(MethodElement constructor) {
        this.constructor = constructor;
    }

    /**
     * Visit the default constructor. If any.
     * @param constructor The constructor method
     */
    void visitDefaultConstructor(MethodElement constructor) {
        this.defaultConstructor = constructor;
    }

    /**
     * index to be created.
     */
    private class AnnotationValueIndex {
        final @Nonnull AnnotationValue annotationValue;
        final @Nonnull String property;
        final @Nullable String value;

        public AnnotationValueIndex(@Nonnull AnnotationValue annotationValue, @Nonnull String property, @Nullable String value) {
            this.annotationValue = annotationValue;
            this.property = property;
            this.value = value;
        }
    }
}
