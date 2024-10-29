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

import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Generated;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospectionReference;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.inject.annotation.AnnotationMetadataGenUtils;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.annotation.AnnotationMetadataReference;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.EnumConstantElement;
import io.micronaut.inject.ast.EnumElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.KotlinParameterElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.beans.AbstractEnumBeanIntrospectionAndReference;
import io.micronaut.inject.beans.AbstractInitializableBeanIntrospection;
import io.micronaut.inject.beans.AbstractInitializableBeanIntrospectionAndReference;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.ArgumentExpUtils;
import io.micronaut.inject.writer.ClassOutputWriter;
import io.micronaut.inject.writer.ClassWriterOutputVisitor;
import io.micronaut.inject.writer.DispatchWriter;
import io.micronaut.inject.writer.EvaluatedExpressionProcessor;
import io.micronaut.inject.writer.OriginatingElements;
import io.micronaut.inject.writer.MethodGenUtils;
import io.micronaut.sourcegen.bytecode.ByteCodeWriter;
import io.micronaut.sourcegen.model.AnnotationDef;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.FieldDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.StatementDef;
import io.micronaut.sourcegen.model.TypeDef;
import io.micronaut.sourcegen.model.VariableDef;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * A class file writer that writes a {@link BeanIntrospectionReference} and associated
 * {@link BeanIntrospection} for the given class.
 *
 * @author graemerocher
 * @author Denis Stepanov
 * @since 1.1
 */
@Internal
final class BeanIntrospectionWriter implements OriginatingElements, ClassOutputWriter {
    private static final String INTROSPECTION_SUFFIX = "$Introspection";

    private static final String FIELD_CONSTRUCTOR_ANNOTATION_METADATA = "$FIELD_CONSTRUCTOR_ANNOTATION_METADATA";
    private static final String FIELD_CONSTRUCTOR_ARGUMENTS = "$CONSTRUCTOR_ARGUMENTS";
    private static final String FIELD_BEAN_PROPERTIES_REFERENCES = "$PROPERTIES_REFERENCES";
    private static final String FIELD_BEAN_METHODS_REFERENCES = "$METHODS_REFERENCES";
    private static final String FIELD_ENUM_CONSTANTS_REFERENCES = "$ENUM_CONSTANTS_REFERENCES";
    private static final java.lang.reflect.Method FIND_PROPERTY_BY_INDEX_METHOD =
        ReflectionUtils.getRequiredInternalMethod(AbstractInitializableBeanIntrospection.class, "getPropertyByIndex", int.class);

    private static final java.lang.reflect.Method FIND_INDEXED_PROPERTY_METHOD =
        ReflectionUtils.getRequiredInternalMethod(AbstractInitializableBeanIntrospection.class, "findIndexedProperty", Class.class, String.class);

    private static final java.lang.reflect.Method GET_INDEXED_PROPERTIES =
        ReflectionUtils.getRequiredInternalMethod(AbstractInitializableBeanIntrospection.class, "getIndexedProperties", Class.class);

    private static final java.lang.reflect.Method GET_BP_INDEXED_SUBSET_METHOD =
        ReflectionUtils.getRequiredInternalMethod(AbstractInitializableBeanIntrospection.class, "getBeanPropertiesIndexedSubset", int[].class);

    private static final java.lang.reflect.Constructor<?> BEAN_METHOD_REF_CONSTRUCTOR = ReflectionUtils.getRequiredInternalConstructor(
        AbstractInitializableBeanIntrospection.BeanMethodRef.class,
        Argument.class,
        String.class,
        AnnotationMetadata.class,
        Argument[].class,
        int.class
    );

    private static final java.lang.reflect.Constructor<?> ENUM_CONSTANT_DYNAMIC_REF_CONSTRUCTOR = ReflectionUtils.getRequiredInternalConstructor(
        AbstractEnumBeanIntrospectionAndReference.EnumConstantDynamicRef.class,
        AnnotationClassValue.class,
        String.class,
        AnnotationMetadata.class
    );

    private static final java.lang.reflect.Constructor<?> INTROSPECTION_SUPER_CONSTRUCTOR = ReflectionUtils.getRequiredInternalConstructor(
        AbstractInitializableBeanIntrospectionAndReference.class,
        Class.class,
        AnnotationMetadata.class,
        AnnotationMetadata.class,
        Argument[].class,
        AbstractInitializableBeanIntrospection.BeanPropertyRef[].class,
        AbstractInitializableBeanIntrospection.BeanMethodRef[].class
    );

    private static final java.lang.reflect.Constructor<?> ENUM_INTROSPECTION_SUPER_CONSTRUCTOR = ReflectionUtils.getRequiredInternalConstructor(
        AbstractEnumBeanIntrospectionAndReference.class,
        Class.class,
        AnnotationMetadata.class,
        AnnotationMetadata.class,
        Argument[].class,
        AbstractInitializableBeanIntrospection.BeanPropertyRef[].class,
        AbstractInitializableBeanIntrospection.BeanMethodRef[].class,
        AbstractEnumBeanIntrospectionAndReference.EnumConstantDynamicRef[].class
    );

    private static final java.lang.reflect.Constructor<?> BEAN_PROPERTY_REF_CONSTRUCTOR = ReflectionUtils.getRequiredInternalConstructor(
        AbstractInitializableBeanIntrospection.BeanPropertyRef.class,
        Argument.class,
        Argument.class,
        Argument.class,
        int.class,
        int.class,
        int.class,
        boolean.class,
        boolean.class
    );

    private static final java.lang.reflect.Method BEAN_PROPERTY_REF_READ_ONLY_STATIC = ReflectionUtils.getRequiredMethod(
        AbstractInitializableBeanIntrospection.BeanPropertyRef.class,
        "readOnly",
        Argument.class,
        int.class,
        int.class,
        int.class,
        boolean.class,
        boolean.class
    );

    private static final java.lang.reflect.Method BEAN_PROPERTY_REF_WRITE_ONLY_STATIC = ReflectionUtils.getRequiredMethod(
        AbstractInitializableBeanIntrospection.BeanPropertyRef.class,
        "writeOnly",
        Argument.class,
        int.class,
        int.class,
        int.class,
        boolean.class,
        boolean.class
    );

    private static final java.lang.reflect.Method BEAN_PROPERTY_REF_READ_WRITE_STATIC = ReflectionUtils.getRequiredMethod(
        AbstractInitializableBeanIntrospection.BeanPropertyRef.class,
        "readWrite",
        Argument.class,
        int.class,
        int.class,
        int.class,
        boolean.class,
        boolean.class
    );

    private static final java.lang.reflect.Method INSTANTIATE_METHOD = ReflectionUtils.getRequiredMethod(
            AbstractInitializableBeanIntrospection.class,
        "instantiate"
    );

    private static final java.lang.reflect.Method INSTANTIATE_INTERNAL_METHOD = ReflectionUtils.getRequiredMethod(
            AbstractInitializableBeanIntrospection.class,
        "instantiateInternal", Object[].class
    );

    private static final java.lang.reflect.Method HAS_BUILDER_METHOD = ReflectionUtils.getRequiredMethod(
            BeanIntrospection.class,
        "hasBuilder"
    );

    private static final java.lang.reflect.Method IS_BUILDABLE_METHOD = ReflectionUtils.getRequiredMethod(
            BeanIntrospection.class,
        "isBuildable"
    );

    private final String introspectionName;
    private final ClassTypeDef introspectionTypeDef;
    private final Map<AnnotationWithValue, String> indexByAnnotationAndValue = new HashMap<>(2);
    private final Map<String, Set<String>> indexByAnnotations = new HashMap<>(2);
    private final Map<String, FieldDef> annotationIndexFields = new HashMap<>(2);
    private final ClassTypeDef beanType;
    private final ClassElement beanClassElement;
    private boolean executed = false;
    private MethodElement constructor;
    private MethodElement defaultConstructor;

    private final List<BeanPropertyData> beanProperties = new ArrayList<>();
    private final List<BeanMethodData> beanMethods = new ArrayList<>();

    private final DispatchWriter dispatchWriter;
    private final EvaluatedExpressionProcessor evaluatedExpressionProcessor;
    private final AnnotationMetadata annotationMetadata;

    private final OriginatingElements originatingElements;

    private CopyConstructorDispatchTarget copyConstructorDispatchTarget;

    /**
     * Default constructor.
     *
     * @param beanClassElement   The class element
     * @param annotationMetadata The bean annotation metadata
     * @param visitorContext     The visitor context
     */
    BeanIntrospectionWriter(String targetPackage, ClassElement beanClassElement, AnnotationMetadata annotationMetadata,
                            VisitorContext visitorContext) {
        final String name = beanClassElement.getName();
        this.beanClassElement = beanClassElement;
        this.beanType = ClassTypeDef.of(beanClassElement);
        this.introspectionName = computeShortIntrospectionName(targetPackage, name);
        this.introspectionTypeDef = ClassTypeDef.of(introspectionName);
        this.dispatchWriter = new DispatchWriter();
        this.annotationMetadata = annotationMetadata.getTargetAnnotationMetadata();
        this.originatingElements = OriginatingElements.of(beanClassElement);
        evaluatedExpressionProcessor = new EvaluatedExpressionProcessor(visitorContext, beanClassElement);
        evaluatedExpressionProcessor.processEvaluatedExpressions(annotationMetadata, null);
    }

    /**
     * Constructor used to generate a reference for already compiled classes.
     *
     * @param generatingType     The originating type
     * @param index              A unique index
     * @param originatingElement The originating element
     * @param beanClassElement   The class element
     * @param annotationMetadata The bean annotation metadata
     * @param visitorContext     The visitor context
     */
    BeanIntrospectionWriter(
        String targetPackage,
        String generatingType,
        int index,
        ClassElement originatingElement,
        ClassElement beanClassElement,
        AnnotationMetadata annotationMetadata,
        VisitorContext visitorContext) {
        final String className = beanClassElement.getName();
        this.beanClassElement = beanClassElement;
        this.beanType = ClassTypeDef.of(beanClassElement);
        this.introspectionName = computeIntrospectionName(targetPackage, className);
        this.introspectionTypeDef = ClassTypeDef.of(introspectionName);
        this.dispatchWriter = new DispatchWriter();
        this.annotationMetadata = annotationMetadata.getTargetAnnotationMetadata();
        this.originatingElements = OriginatingElements.of(originatingElement);
        evaluatedExpressionProcessor = new EvaluatedExpressionProcessor(visitorContext, beanClassElement);
        evaluatedExpressionProcessor.processEvaluatedExpressions(annotationMetadata, null);
    }

    /**
     * @return The name of the class that the introspection will write.
     */
    public String getIntrospectionName() {
        return introspectionName;
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
    public ClassTypeDef getBeanType() {
        return beanType;
    }

    /**
     * Visit a property.
     *
     * @param type        The property type
     * @param genericType The generic type
     * @param name        The property name
     * @param readMember  The read method
     * @param readType    The read type
     * @param writeMember The write member
     * @param writeType   The write type
     * @param isReadOnly  Is read only
     */
    void visitProperty(
        @NonNull ClassElement type,
        @NonNull ClassElement genericType,
        @NonNull String name,
        @Nullable MemberElement readMember,
        @Nullable MemberElement writeMember,
        @Nullable ClassElement readType,
        @Nullable ClassElement writeType,
        boolean isReadOnly) {
        this.evaluatedExpressionProcessor.processEvaluatedExpressions(genericType.getAnnotationMetadata(), beanClassElement);
        int readDispatchIndex = -1;
        if (readMember != null) {
            if (readMember instanceof MethodElement element) {
                readDispatchIndex = dispatchWriter.addMethod(beanClassElement, element, true);
            } else if (readMember instanceof FieldElement element) {
                readDispatchIndex = dispatchWriter.addGetField(element);
            } else {
                throw new IllegalStateException();
            }
        }
        int writeDispatchIndex = -1;
        int withMethodIndex = -1;
        if (writeMember != null) {
            if (writeMember instanceof MethodElement element) {
                writeDispatchIndex = dispatchWriter.addMethod(beanClassElement, element, true);
            } else if (writeMember instanceof FieldElement element) {
                writeDispatchIndex = dispatchWriter.addSetField(element);
            } else {
                throw new IllegalStateException();
            }
        }
        boolean isMutable = !isReadOnly || hasAssociatedConstructorArgument(name, genericType);
        if (isMutable) {
            if (writeMember == null) {
                final String prefix = this.annotationMetadata.stringValue(Introspected.class, "withPrefix").orElse("with");
                ElementQuery<MethodElement> elementQuery = ElementQuery.of(MethodElement.class)
                    .onlyAccessible()
                    .onlyDeclared()
                    .onlyInstance()
                    .filter((methodElement -> {
                        ParameterElement[] parameters = methodElement.getParameters();
                        String methodName = methodElement.getName();
                        return methodName.startsWith(prefix) && methodName.equals(prefix + NameUtils.capitalize(name))
                            && parameters.length == 1
                            && methodElement.getGenericReturnType().getName().equals(beanClassElement.getName())
                            && type.getType().isAssignable(parameters[0].getType());
                    }));
                MethodElement withMethod = beanClassElement.getEnclosedElement(elementQuery).orElse(null);
                if (withMethod != null) {
                    withMethodIndex = dispatchWriter.addMethod(beanClassElement, withMethod, true);
                } else {
                    MethodElement constructor = this.constructor == null ? defaultConstructor : this.constructor;
                    if (constructor != null) {
                        if (copyConstructorDispatchTarget == null) {
                            copyConstructorDispatchTarget = new CopyConstructorDispatchTarget(beanType, beanProperties, dispatchWriter, constructor);
                        }
                        copyConstructorDispatchTarget.propertyNames.put(name, dispatchWriter.getDispatchTargets().size());
                        withMethodIndex = dispatchWriter.addDispatchTarget(copyConstructorDispatchTarget);
                    }
                }
            }
            // Otherwise, set method would be used in BeanProperty
        } else {
            withMethodIndex = dispatchWriter.addDispatchTarget(new ExceptionDispatchTarget(
                UnsupportedOperationException.class,
                "Cannot mutate property [" + name + "] that is not mutable via a setter method, field or constructor argument for type: " + beanType.getName()
            ));
        }

        beanProperties.add(new BeanPropertyData(
            name,
            genericType,
            readType,
            writeType,
            readDispatchIndex,
            writeDispatchIndex,
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
            int dispatchIndex = dispatchWriter.addMethod(beanClassElement, element);
            beanMethods.add(new BeanMethodData(element, dispatchIndex));
            this.evaluatedExpressionProcessor.processEvaluatedExpressions(element.getAnnotationMetadata(), beanClassElement);
            for (ParameterElement parameter : element.getParameters()) {
                this.evaluatedExpressionProcessor.processEvaluatedExpressions(parameter.getAnnotationMetadata(), beanClassElement);
            }
        }
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

            // First write the introspection for the annotation metadata can be populated with defaults that reference will contain
            writeIntrospectionClass(classWriterOutputVisitor);
            this.evaluatedExpressionProcessor.writeEvaluatedExpressions(classWriterOutputVisitor);
        }
    }

    private ExpressionDef pushBeanPropertyReference(BeanPropertyData beanPropertyData, Map<String, MethodDef> loadTypeMethods) {
        ClassTypeDef beanPropertyRefDef = ClassTypeDef.of(AbstractInitializableBeanIntrospection.BeanPropertyRef.class);

        boolean mutable = !beanPropertyData.isReadOnly || hasAssociatedConstructorArgument(beanPropertyData.name, beanPropertyData.type);

        if (beanPropertyData.type.equals(beanPropertyData.readType) && beanPropertyData.type.equals(beanPropertyData.writeType)) {
            return beanPropertyRefDef.invokeStatic(
                BEAN_PROPERTY_REF_READ_WRITE_STATIC,

                ArgumentExpUtils.pushCreateArgument(
                    annotationMetadata,
                    beanClassElement,
                    introspectionTypeDef,
                    beanPropertyData.name,
                    beanPropertyData.type,
                    loadTypeMethods
                ),
                ExpressionDef.constant(beanPropertyData.getDispatchIndex),
                ExpressionDef.constant(beanPropertyData.setDispatchIndex),
                ExpressionDef.constant(beanPropertyData.withMethodDispatchIndex),
                ExpressionDef.constant(beanPropertyData.isReadOnly),
                ExpressionDef.constant(mutable)
            );
        }
        if (beanPropertyData.type.equals(beanPropertyData.readType) && beanPropertyData.writeType == null) {
            return beanPropertyRefDef.invokeStatic(
                BEAN_PROPERTY_REF_READ_ONLY_STATIC,

                ArgumentExpUtils.pushCreateArgument(
                    annotationMetadata,
                    beanClassElement,
                    introspectionTypeDef,
                    beanPropertyData.name,
                    beanPropertyData.type,
                    loadTypeMethods
                ),
                ExpressionDef.constant(beanPropertyData.getDispatchIndex),
                ExpressionDef.constant(beanPropertyData.setDispatchIndex),
                ExpressionDef.constant(beanPropertyData.withMethodDispatchIndex),
                ExpressionDef.constant(beanPropertyData.isReadOnly),
                ExpressionDef.constant(mutable)
            );
        }
        if (beanPropertyData.type.equals(beanPropertyData.writeType) && beanPropertyData.readType == null) {
            return beanPropertyRefDef.invokeStatic(
                BEAN_PROPERTY_REF_WRITE_ONLY_STATIC,

                ArgumentExpUtils.pushCreateArgument(
                    annotationMetadata,
                    beanClassElement,
                    introspectionTypeDef,
                    beanPropertyData.name,
                    beanPropertyData.type,
                    loadTypeMethods
                ),
                ExpressionDef.constant(beanPropertyData.getDispatchIndex),
                ExpressionDef.constant(beanPropertyData.setDispatchIndex),
                ExpressionDef.constant(beanPropertyData.withMethodDispatchIndex),
                ExpressionDef.constant(beanPropertyData.isReadOnly),
                ExpressionDef.constant(mutable)
            );
        }
        return beanPropertyRefDef.instantiate(
            BEAN_PROPERTY_REF_CONSTRUCTOR,

            ArgumentExpUtils.pushCreateArgument(
                annotationMetadata,
                beanClassElement,
                introspectionTypeDef,
                beanPropertyData.name,
                beanPropertyData.type,
                loadTypeMethods
            ),
            beanPropertyData.readType == null ? ExpressionDef.nullValue() : ArgumentExpUtils.pushCreateArgument(
                annotationMetadata,
                beanClassElement,
                introspectionTypeDef,
                beanPropertyData.name,
                beanPropertyData.readType,
                loadTypeMethods
            ),
            beanPropertyData.writeType == null ? ExpressionDef.nullValue() : ArgumentExpUtils.pushCreateArgument(
                annotationMetadata,
                beanClassElement,
                introspectionTypeDef,
                beanPropertyData.name,
                beanPropertyData.writeType,
                loadTypeMethods
            ),
            ExpressionDef.constant(beanPropertyData.getDispatchIndex),
            ExpressionDef.constant(beanPropertyData.setDispatchIndex),
            ExpressionDef.constant(beanPropertyData.withMethodDispatchIndex),
            ExpressionDef.constant(beanPropertyData.isReadOnly),
            ExpressionDef.constant(mutable)
        );
    }

    private ExpressionDef newBeanMethodRef(BeanMethodData beanMethodData, Map<String, MethodDef> loadTypeMethods) {
        return ClassTypeDef.of(AbstractInitializableBeanIntrospection.BeanMethodRef.class)
            .instantiate(
                BEAN_METHOD_REF_CONSTRUCTOR,

                // 1: return argument
                ArgumentExpUtils.pushReturnTypeArgument(
                    annotationMetadata,
                    introspectionTypeDef,
                    beanMethodData.methodElement.getOwningType(),
                    beanMethodData.methodElement.getGenericReturnType(),
                    loadTypeMethods),
                // 2: name
                ExpressionDef.constant(beanMethodData.methodElement.getName()),
                // 3: annotation metadata
                getAnnotationMetadataExpression(beanMethodData.methodElement.getAnnotationMetadata(), loadTypeMethods),
                // 4: arguments
                beanMethodData.methodElement.getParameters().length == 0 ? ExpressionDef.nullValue() : ArgumentExpUtils.pushBuildArgumentsForMethod(
                    annotationMetadata,
                    beanClassElement,
                    introspectionTypeDef,
                    Arrays.asList(beanMethodData.methodElement.getParameters()),
                    loadTypeMethods
                ),
                // 5: method index
                ExpressionDef.constant(beanMethodData.dispatchIndex)
            );
    }

    private ExpressionDef newEnumConstantRef(EnumConstantElement enumConstantElement, Map<String, MethodDef> loadTypeMethods) {
        return ClassTypeDef.of(
            AbstractEnumBeanIntrospectionAndReference.EnumConstantDynamicRef.class
        ).instantiate(
            ENUM_CONSTANT_DYNAMIC_REF_CONSTRUCTOR,

            // 1: push annotation class value
            AnnotationMetadataGenUtils.invokeLoadClassValueMethod(introspectionTypeDef, loadTypeMethods, new AnnotationClassValue<>(enumConstantElement.getOwningType().getName())),
            // 2: push enum name
            ExpressionDef.constant(enumConstantElement.getName()),
            // 3: annotation metadata
            enumConstantElement.getAnnotationMetadata() == null ? (
                ClassTypeDef.of(AnnotationMetadata.class).getStaticField("EMPTY_METADATA", TypeDef.of(AnnotationMetadata.class))
            ) : getAnnotationMetadataExpression(enumConstantElement.getAnnotationMetadata(), loadTypeMethods)
        );
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
        boolean isEnum = beanClassElement.isEnum();

        Map<String, MethodDef> loadTypeMethods = new LinkedHashMap<>();

        ClassDef.ClassDefBuilder classDefBuilder = ClassDef.builder(introspectionName).addModifiers(Modifier.FINAL, Modifier.PUBLIC);
        classDefBuilder.superclass(isEnum ? ClassTypeDef.of(AbstractEnumBeanIntrospectionAndReference.class) : ClassTypeDef.of(AbstractInitializableBeanIntrospectionAndReference.class));

        classWriterOutputVisitor.visitServiceDescriptor(BeanIntrospectionReference.class, introspectionName, beanClassElement);

        classDefBuilder.addAnnotation(AnnotationDef.builder(Generated.class).addMember("service", introspectionName).build());
        // init expressions at build time
        evaluatedExpressionProcessor.registerExpressionForBuildTimeInit(classDefBuilder);

        FieldDef constructorAnnotationMetadataField;
        FieldDef constructorArgumentsField;
        FieldDef beanPropertiesField;
        FieldDef beanMethodsField;
        FieldDef enumsField;

        if (constructor != null) {
            if (!constructor.getAnnotationMetadata().isEmpty()) {
                constructorAnnotationMetadataField = FieldDef.builder(FIELD_CONSTRUCTOR_ANNOTATION_METADATA, AnnotationMetadata.class)
                    .addModifiers(Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
                    .initializer(getAnnotationMetadataExpression(constructor.getAnnotationMetadata(), loadTypeMethods))
                    .build();
                classDefBuilder.addField(
                    constructorAnnotationMetadataField
                );
            } else {
                constructorAnnotationMetadataField = null;
            }
            if (ArrayUtils.isNotEmpty(constructor.getParameters())) {
                constructorArgumentsField = FieldDef.builder(FIELD_CONSTRUCTOR_ARGUMENTS, Argument[].class)
                    .addModifiers(Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
                    .initializer(
                        ArgumentExpUtils.pushBuildArgumentsForMethod(
                            annotationMetadata,
                            constructor.getOwningType(),
                            introspectionTypeDef,
                            Arrays.asList(constructor.getParameters()),
                            loadTypeMethods
                        )
                    )
                    .build();
                classDefBuilder.addField(
                    constructorArgumentsField
                );
            } else {
                constructorArgumentsField = null;
            }
        } else {
            constructorArgumentsField = null;
            constructorAnnotationMetadataField = null;
        }
        if (!beanProperties.isEmpty()) {
            beanPropertiesField = FieldDef.builder(FIELD_BEAN_PROPERTIES_REFERENCES, AbstractInitializableBeanIntrospection.BeanPropertyRef[].class)
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
                .initializer(
                    ClassTypeDef.of(AbstractInitializableBeanIntrospection.BeanPropertyRef.class).array()
                        .instantiate(
                            beanProperties.stream()
                                .map(e -> pushBeanPropertyReference(e, loadTypeMethods))
                                .toList()
                        )
                )
                .build();
            classDefBuilder.addField(beanPropertiesField);
        } else {
            beanPropertiesField = null;
        }
        if (!beanMethods.isEmpty()) {
            beanMethodsField = FieldDef.builder(FIELD_BEAN_METHODS_REFERENCES, AbstractInitializableBeanIntrospection.BeanMethodRef[].class)
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
                .initializer(
                    ClassTypeDef.of(AbstractInitializableBeanIntrospection.BeanMethodRef.class).array()
                        .instantiate(
                            beanMethods.stream()
                                .map(e -> newBeanMethodRef(e, loadTypeMethods))
                                .toList()
                        )
                )
                .build();
            classDefBuilder.addField(beanMethodsField);
        } else {
            beanMethodsField = null;
        }
        if (isEnum) {
            enumsField = FieldDef.builder(FIELD_ENUM_CONSTANTS_REFERENCES, AbstractEnumBeanIntrospectionAndReference.EnumConstantDynamicRef[].class)
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
                .initializer(
                    ClassTypeDef.of(AbstractEnumBeanIntrospectionAndReference.EnumConstantDynamicRef.class).array()
                        .instantiate(
                            ((EnumElement) beanClassElement).elements().stream()
                                .map(e -> newEnumConstantRef(e, loadTypeMethods))
                                .toList()
                        )
                )
                .build();
            classDefBuilder.addField(enumsField);
        } else {
            enumsField = null;
        }

        int indexesIndex = 0;
        for (String annotationName : indexByAnnotations.keySet()) {
            int[] indexes = indexByAnnotations.get(annotationName)
                .stream()
                .mapToInt(this::getPropertyIndex)
                .toArray();

            FieldDef field = FieldDef.builder("INDEX_" + (++indexesIndex), int[].class)
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
                .initializer(
                    TypeDef.Primitive.INT.array()
                        .instantiate(
                            Arrays.stream(indexes).mapToObj(TypeDef.Primitive.INT::constant).toList()
                        )
                )
                .build();
            classDefBuilder.addField(
                field
            );

            annotationIndexFields.put(annotationName, field);
        }

        List<StatementDef> statements = new ArrayList<>();
        AnnotationMetadataGenUtils.writeAnnotationDefault(statements, introspectionTypeDef, annotationMetadata, loadTypeMethods);

        FieldDef annotationMetadataField = AnnotationMetadataGenUtils.createAnnotationMetadataField(introspectionTypeDef, annotationMetadata, loadTypeMethods);
        if (annotationMetadataField != null) {
            classDefBuilder.addField(
                annotationMetadataField
            );
        }

        if (!statements.isEmpty()) {
            classDefBuilder.addStaticInitializer(StatementDef.multi(statements));
        }

        classDefBuilder.addMethod(
            MethodDef.constructor()
                .addModifiers(Modifier.PUBLIC)
                .build((aThis, methodParameters) -> {
                    List<ExpressionDef> values = new ArrayList<>();
                    // 1st argument: The bean type
                    values.add(ExpressionDef.constant(beanType));
                    // 2nd argument: The annotation metadata
                    if (annotationMetadataField == null) {
                        values.add(ExpressionDef.nullValue());
                    } else {
                        values.add(introspectionTypeDef.getStaticField(annotationMetadataField));
                    }
                    // 3rd argument: constructor metadata
                    values.add(constructorAnnotationMetadataField != null ? introspectionTypeDef.getStaticField(constructorAnnotationMetadataField) : ExpressionDef.nullValue());
                    // 4th argument: constructor arguments
                    values.add(constructorArgumentsField != null ? introspectionTypeDef.getStaticField(constructorArgumentsField) : ExpressionDef.nullValue());

                    values.add(beanPropertiesField == null ? ExpressionDef.nullValue() : introspectionTypeDef.getStaticField(beanPropertiesField));
                    values.add(beanMethodsField == null ? ExpressionDef.nullValue() : introspectionTypeDef.getStaticField(beanMethodsField));

                    if (enumsField != null) {
                        values.add(introspectionTypeDef.getStaticField(enumsField));
                        return aThis.superRef().invokeConstructor(ENUM_INTROSPECTION_SUPER_CONSTRUCTOR, values);
                    } else {
                        return aThis.superRef().invokeConstructor(INTROSPECTION_SUPER_CONSTRUCTOR, values);
                    }
                })
        );

        MethodDef dispatchOneMethod = dispatchWriter.buildDispatchOneMethod();
        if (dispatchOneMethod != null) {
            classDefBuilder.addMethod(dispatchOneMethod);
        }
        MethodDef dispatchMethod = dispatchWriter.buildDispatchMethod();
        if (dispatchMethod != null) {
            classDefBuilder.addMethod(dispatchMethod);
        }
        MethodDef buildGetTargetMethodByIndex = dispatchWriter.buildGetTargetMethodByIndex();
        if (buildGetTargetMethodByIndex != null) {
            classDefBuilder.addMethod(buildGetTargetMethodByIndex);
        }

        MethodDef findIndexedProperty = getFindIndexedProperty();
        if (findIndexedProperty != null) {
            classDefBuilder.addMethod(findIndexedProperty);
        }
        MethodDef getIndexedProperties = getGetIndexedProperties();
        if (getIndexedProperties != null) {
            classDefBuilder.addMethod(getIndexedProperties);
        }

        boolean hasBuilder = annotationMetadata != null &&
            (annotationMetadata.isPresent(Introspected.class, "builder") || annotationMetadata.hasDeclaredAnnotation("lombok.Builder"));        if (defaultConstructor != null) {
            classDefBuilder.addMethod(
                getInstantiateMethod(defaultConstructor, INSTANTIATE_METHOD)
            );
            // in case invoked directly or via instantiateUnsafe
            if (constructor == null) {
                classDefBuilder.addMethod(
                    getInstantiateMethod(defaultConstructor, INSTANTIATE_INTERNAL_METHOD)
                );
                classDefBuilder.addMethod(
                    getBooleanMethod(IS_BUILDABLE_METHOD, true)
                );
            }
        }

        if (constructor != null) {
            if (defaultConstructor == null) {
                if (ArrayUtils.isEmpty(constructor.getParameters())) {
                    classDefBuilder.addMethod(
                        getInstantiateMethod(constructor, INSTANTIATE_METHOD)
                    );
                } else {
                    boolean kotlinAllDefault = Arrays.stream(constructor.getParameters())
                        .allMatch(p -> p instanceof KotlinParameterElement kp && kp.hasDefault());
                    if (kotlinAllDefault) {
                        classDefBuilder.addMethod(
                            getInstantiateMethod(constructor, INSTANTIATE_METHOD)
                        );
                    }
                }
            }
            classDefBuilder.addMethod(
                getInstantiateMethod(constructor, INSTANTIATE_INTERNAL_METHOD)
            );
            classDefBuilder.addMethod(
                getBooleanMethod(IS_BUILDABLE_METHOD, true)
            );
        } else if (defaultConstructor == null) {
            classDefBuilder.addMethod(
                getBooleanMethod(IS_BUILDABLE_METHOD, hasBuilder)
            );
        }
        classDefBuilder.addMethod(
            getBooleanMethod(HAS_BUILDER_METHOD, hasBuilder)
        );

        loadTypeMethods.values().forEach(classDefBuilder::addMethod);

        try (OutputStream outputStream = classWriterOutputVisitor.visitClass(introspectionName, getOriginatingElements())) {
            outputStream.write(new ByteCodeWriter(false, true).write(classDefBuilder.build()));
        }
    }

    private MethodDef getBooleanMethod(Method method, boolean state) {
        return MethodDef.override(method)
            .build((aThis, methodParameters) -> ExpressionDef.constant(state).returning());
    }

    @Nullable
    private MethodDef getFindIndexedProperty() {
        if (indexByAnnotationAndValue.isEmpty()) {
            return null;
        }
        TypeDef returnType = TypeDef.of(FIND_INDEXED_PROPERTY_METHOD.getReturnType());
        Set<String> keys = indexByAnnotationAndValue.keySet()
            .stream()
            .map(s -> s.annotationName)
            .collect(Collectors.toSet());
        return MethodDef.builder(FIND_INDEXED_PROPERTY_METHOD.getName())
            .addParameters(FIND_INDEXED_PROPERTY_METHOD.getParameterTypes())
            .addModifiers(Modifier.PUBLIC)
            .returns(returnType)
            .build((aThis, methodParameters) ->
                methodParameters.get(0).invoke("getName", TypeDef.STRING)
                    .asStatementSwitch(returnType, keys.stream()
                        .collect(Collectors.toMap(
                            ExpressionDef::constant,
                            annotationName -> onMatch(aThis, methodParameters, annotationName, returnType)
                        )), ExpressionDef.nullValue().returning()));
    }

    private StatementDef onMatch(VariableDef.This aThis, List<VariableDef.MethodParameter> parameters, String annotationName, TypeDef returnType) {
        List<StatementDef> statements = new ArrayList<>();
        VariableDef.MethodParameter annotationValueParameter = parameters.get(1);
        if (indexByAnnotationAndValue.keySet().stream().anyMatch(s -> s.annotationName.equals(annotationName) && s.value == null)) {
            String propertyName = indexByAnnotationAndValue.get(new AnnotationWithValue(annotationName, null));
            int propertyIndex = getPropertyIndex(propertyName);
            statements.add(
                annotationValueParameter.ifNonNull(
                    aThis.invoke(FIND_PROPERTY_BY_INDEX_METHOD, ExpressionDef.constant(propertyIndex)).returning()
                )
            );
        } else {
            statements.add(
                annotationValueParameter.ifNull(
                    ExpressionDef.nullValue().returning()
                )
            );
        }
        Set<String> valueMatches = indexByAnnotationAndValue.keySet()
            .stream()
            .filter(s -> s.annotationName.equals(annotationName) && s.value != null)
            .map(s -> s.value)
            .collect(Collectors.toSet());
        if (!valueMatches.isEmpty()) {
            statements.add(annotationValueParameter.asExpressionSwitch(returnType, valueMatches.stream()
                .collect(Collectors.toMap(ExpressionDef::constant, e -> {
                    String propertyName = indexByAnnotationAndValue.get(new AnnotationWithValue(annotationName, e));
                    int propertyIndex = getPropertyIndex(propertyName);
                    return aThis.invoke(FIND_PROPERTY_BY_INDEX_METHOD, ExpressionDef.constant(propertyIndex));
                })), ExpressionDef.nullValue()).returning()
            );
        }
        statements.add(ExpressionDef.nullValue().returning());
        return StatementDef.multi(statements);
    }

    @Nullable
    private MethodDef getGetIndexedProperties() {
        if (indexByAnnotations.isEmpty()) {
            return null;
        }
        TypeDef returnType = TypeDef.of(GET_INDEXED_PROPERTIES.getReturnType());
        return MethodDef.builder(GET_INDEXED_PROPERTIES.getName())
            .returns(returnType)
            .addModifiers(Modifier.PUBLIC)
            .addParameters(GET_INDEXED_PROPERTIES.getParameterTypes())
            .build((aThis, methodParameters) ->
                methodParameters.get(0).invoke("getName", TypeDef.STRING)
                    .asExpressionSwitch(returnType, indexByAnnotations.keySet().stream()
                        .collect(Collectors.toMap(
                            ExpressionDef::constant,
                            annotationName ->
                                aThis.invoke(
                                    GET_BP_INDEXED_SUBSET_METHOD,
                                    introspectionTypeDef.getStaticField(annotationIndexFields.get(annotationName))
                                )
                        )), ExpressionDef.nullValue())
                    .returning());
    }

    private int getPropertyIndex(String propertyName) {
        BeanPropertyData beanPropertyData = beanProperties.stream().filter(bp -> bp.name.equals(propertyName)).findFirst().orElse(null);
        if (beanPropertyData != null) {
            return beanProperties.indexOf(beanPropertyData);
        }
        throw new IllegalStateException("Property not found: " + propertyName + " " + beanClassElement.getName());
    }

    private MethodDef getInstantiateMethod(MethodElement constructor, Method method) {
        return MethodDef.override(method)
            .build((aThis, methodParameters) -> {
                if (method.getParameters().length == 0) {
                    return MethodGenUtils.invokeBeanConstructor(constructor, true, null).returning();
                } else {
                    List<ExpressionDef> values = IntStream.range(0, constructor.getSuspendParameters().length)
                            .<ExpressionDef>mapToObj(index -> methodParameters.get(0).arrayElement(index))
                            .toList();
                    return MethodGenUtils.invokeBeanConstructor(constructor, true, values).returning();
                }
            });
    }

    private ExpressionDef getAnnotationMetadataExpression(AnnotationMetadata annotationMetadata, Map<String, MethodDef> loadTypeMethods) {
        MutableAnnotationMetadata.contributeDefaults(
            this.annotationMetadata,
            annotationMetadata
        );

        annotationMetadata = annotationMetadata.getTargetAnnotationMetadata();
        if (annotationMetadata.isEmpty()) {
            return ExpressionDef.nullValue();
        } else if (annotationMetadata instanceof AnnotationMetadataReference annotationMetadataReference) {
            return AnnotationMetadataGenUtils.annotationMetadataReference(annotationMetadataReference);
        } else if (annotationMetadata instanceof AnnotationMetadataHierarchy annotationMetadataHierarchy) {
            return AnnotationMetadataGenUtils.instantiateNewMetadataHierarchy(
                introspectionTypeDef,
                annotationMetadataHierarchy,
                loadTypeMethods);
        } else if (annotationMetadata instanceof MutableAnnotationMetadata mutableAnnotationMetadata) {
            return AnnotationMetadataGenUtils.instantiateNewMetadata(
                introspectionTypeDef,
                mutableAnnotationMetadata,
                loadTypeMethods);
        } else {
            throw new IllegalStateException("Unknown annotation metadata:  " + annotationMetadata);
        }
    }

    @NonNull
    private static String computeShortIntrospectionName(String packageName, String className) {
        final String shortName = NameUtils.getSimpleName(className);
        return packageName + ".$" + shortName + INTROSPECTION_SUFFIX;
    }

    @NonNull
    private static String computeIntrospectionName(String packageName, String className) {
        return packageName + ".$" + className.replace('.', '_') + INTROSPECTION_SUFFIX;
    }

    /**
     * Visit the constructor. If any.
     *
     * @param constructor The constructor method
     */
    void visitConstructor(MethodElement constructor) {
        this.constructor = constructor;
        processConstructorEvaluatedMetadata(constructor);
    }

    /**
     * Visit the default constructor. If any.
     *
     * @param constructor The constructor method
     */
    void visitDefaultConstructor(MethodElement constructor) {
        this.defaultConstructor = constructor;
        processConstructorEvaluatedMetadata(constructor);
    }

    private void processConstructorEvaluatedMetadata(MethodElement constructor) {
        this.evaluatedExpressionProcessor.processEvaluatedExpressions(constructor.getAnnotationMetadata(), null);
        for (ParameterElement parameter : constructor.getParameters()) {
            this.evaluatedExpressionProcessor.processEvaluatedExpressions(parameter.getAnnotationMetadata(), null);
        }
    }

    @NonNull
    @Override
    public Element[] getOriginatingElements() {
        return originatingElements.getOriginatingElements();
    }

    @Override
    public void addOriginatingElement(@NonNull Element element) {
        originatingElements.addOriginatingElement(element);
    }

    private record ExceptionDispatchTarget(Class<?> exceptionType,
                                           String message) implements DispatchWriter.DispatchTarget {

        @Override
        public boolean supportsDispatchOne() {
            return true;
        }

        @Override
        public boolean supportsDispatchMulti() {
            return false;
        }

        @Override
        public StatementDef dispatch(ExpressionDef target, ExpressionDef valuesArray) {
            return ClassTypeDef.of(exceptionType).instantiate(ExpressionDef.constant(message)).doThrow();
        }

        @Override
        public StatementDef dispatchOne(int caseValue, ExpressionDef caseExpression, ExpressionDef target, ExpressionDef value) {
            return ClassTypeDef.of(exceptionType).instantiate(ExpressionDef.constant(message)).doThrow();
        }

        @Override
        public MethodElement getMethodElement() {
            return null;
        }

        @Override
        public TypedElement getDeclaringType() {
            return null;
        }
    }

    /**
     * Copy constructor "with" method writer.
     */
    private static final class CopyConstructorDispatchTarget implements DispatchWriter.DispatchTarget {

        private final ClassTypeDef beanType;
        private final List<BeanPropertyData> beanProperties;
        private final DispatchWriter dispatchWriter;
        private final MethodElement constructor;
        private final Map<String, Integer> propertyNames = new HashMap<>();
        private StatementDef statement;

        private CopyConstructorDispatchTarget(ClassTypeDef beanType,
                                              List<BeanPropertyData> beanProperties,
                                              DispatchWriter dispatchWriter,
                                              MethodElement constructor) {
            this.beanType = beanType;
            this.beanProperties = beanProperties;
            this.dispatchWriter = dispatchWriter;
            this.constructor = constructor;
        }

        @Override
        public boolean supportsDispatchOne() {
            return true;
        }

        @Override
        public boolean supportsDispatchMulti() {
            return false;
        }

        @Override
        public StatementDef dispatchOne(int caseValue, ExpressionDef caseExpression, ExpressionDef target, ExpressionDef value) {
            if (statement == null) {
                // The unique statement provided for the switch case should produce one case
                statement = createStatement(caseExpression, target, value);
            }
            return statement;
        }

        private StatementDef createStatement(ExpressionDef caseExpression, ExpressionDef target, ExpressionDef value) {
            // In this case we have to do the copy constructor approach
            Set<BeanPropertyData> constructorProps = new HashSet<>();

            boolean isMutable = true;
            String nonMutableMessage = null;
            ParameterElement[] parameters = constructor.getParameters();
            Object[] constructorArguments = new Object[parameters.length];

            for (int i = 0; i < parameters.length; i++) {
                ParameterElement parameter = parameters[i];
                String parameterName = parameter.getName();

                BeanPropertyData prop = beanProperties.stream()
                    .filter(bp -> bp.name.equals(parameterName))
                    .findAny().orElse(null);

                int readDispatchIndex = prop == null ? -1 : prop.getDispatchIndex;
                if (readDispatchIndex != -1) {
                    Object member;
                    ClassElement propertyType;
                    DispatchWriter.DispatchTarget dispatchTarget = dispatchWriter.getDispatchTargets().get(readDispatchIndex);
                    if (dispatchTarget instanceof DispatchWriter.MethodDispatchTarget methodDispatchTarget) {
                        MethodElement methodElement = methodDispatchTarget.getMethodElement();
                        propertyType = methodElement.getGenericReturnType();
                        member = methodElement;
                    } else if (dispatchTarget instanceof DispatchWriter.FieldGetDispatchTarget fieldGetDispatchTarget) {
                        FieldElement field = fieldGetDispatchTarget.getField();
                        propertyType = field.getGenericType();
                        member = field;
                    } else {
                        throw new IllegalStateException();
                    }
                    if (propertyType.isAssignable(parameter.getGenericType())) {
                        constructorArguments[i] = member;
                        constructorProps.add(prop);
                    } else {
                        isMutable = false;
                        nonMutableMessage = "Cannot create copy of type [" + beanType.getName() + "]. Property of type [" + propertyType.getName() + "] is not assignable to constructor argument [" + parameterName + "]";
                    }
                } else {
                    isMutable = false;
                    nonMutableMessage = "Cannot create copy of type [" + beanType.getName() + "]. Constructor contains argument [" + parameterName + "] that is not a readable property";
                    break;
                }
            }

            if (isMutable) {
                return target.cast(beanType).newLocal("prevBean", prevBeanVar -> {
                    List<ExpressionDef> values = new ArrayList<>(constructorArguments.length);
                    for (int i = 0; i < parameters.length; i++) {
                        ParameterElement parameter = parameters[i];
                        Object constructorArgument = constructorArguments[i];

                        ExpressionDef oldValueExp;
                        if (constructorArgument instanceof MethodElement readMethod) {
                            oldValueExp = prevBeanVar.invoke(readMethod);
                        } else {
                            oldValueExp = prevBeanVar.field((FieldElement) constructorArgument);
                        }

                        Integer propertyIndex = propertyNames.get(parameter.getName());
                        ExpressionDef paramExp;
                        if (propertyIndex != null) {
                            ExpressionDef.Cast newPropertyValue = value.cast(TypeDef.erasure(parameter.getType()));
                            if (propertyNames.size() == 1) {
                                paramExp =  newPropertyValue;
                            } else {
                                paramExp = caseExpression.equalsStructurally(ExpressionDef.constant((int) propertyIndex)).doIfElse(
                                        newPropertyValue,
                                        oldValueExp
                                );
                            }
                        } else {
                            paramExp = oldValueExp;
                        }
                        values.add(paramExp);
                    }

                    // NOTE: It doesn't make sense to check defaults for the copy constructor
                    ExpressionDef newInstance = MethodGenUtils.invokeBeanConstructor(constructor, false, values);
                    return withSetSettersAndFields(newInstance, prevBeanVar, constructorProps);
                });
            } else {
                // In this case the bean cannot be mutated via either copy constructor or setter so simply throw an exception
                return ClassTypeDef.of(UnsupportedOperationException.class).instantiate(ExpressionDef.constant(nonMutableMessage)).doThrow();
            }
        }

        @Override
        public StatementDef dispatch(ExpressionDef target, ExpressionDef valuesArray) {
            throw new IllegalStateException("Not supported");
        }

        private StatementDef withSetSettersAndFields(ExpressionDef newInstance,
                                                     VariableDef prevBeanVar,
                                                     Set<BeanPropertyData> constructorProps) {
            List<BeanPropertyData> readWriteProps = beanProperties.stream()
                    .filter(bp -> bp.setDispatchIndex != -1 && bp.getDispatchIndex != -1 && !constructorProps.contains(bp)).toList();

            if (readWriteProps.isEmpty()) {
                return newInstance.returning();
            }
            return newInstance
                    .newLocal("newBean", newBeanVar -> {
                        List<StatementDef> statements = new ArrayList<>();
                        for (BeanPropertyData readWriteProp : readWriteProps) {
                            DispatchWriter.DispatchTarget readDispatch = dispatchWriter.getDispatchTargets().get(readWriteProp.getDispatchIndex);
                            ExpressionDef oldValueExp;
                            if (readDispatch instanceof DispatchWriter.MethodDispatchTarget methodDispatchTarget) {
                                MethodElement readMethod = methodDispatchTarget.getMethodElement();
                                oldValueExp = prevBeanVar.invoke(readMethod);
                            } else if (readDispatch instanceof DispatchWriter.FieldGetDispatchTarget fieldGetDispatchTarget) {
                                FieldElement fieldElement = fieldGetDispatchTarget.getField();
                                oldValueExp = prevBeanVar.field(fieldElement);
                            } else {
                                throw new IllegalStateException();
                            }

                            DispatchWriter.DispatchTarget writeDispatch = dispatchWriter.getDispatchTargets().get(readWriteProp.setDispatchIndex);
                            if (writeDispatch instanceof DispatchWriter.MethodDispatchTarget methodDispatchTarget) {
                                MethodElement writeMethod = methodDispatchTarget.getMethodElement();
                                statements.add(
                                        newBeanVar.invoke(
                                                writeMethod,
                                                oldValueExp
                                        )
                                );
                            } else if (writeDispatch instanceof DispatchWriter.FieldSetDispatchTarget fieldSetDispatchTarget) {
                                FieldElement fieldElement = fieldSetDispatchTarget.getField();
                                statements.add(
                                        newBeanVar.field(fieldElement).assign(oldValueExp)
                                );
                            } else {
                                throw new IllegalStateException();
                            }

                        }
                        statements.add(newBeanVar.returning());
                        return StatementDef.multi(statements);
                    });
        }

        @Override
        public MethodElement getMethodElement() {
            return null;
        }

        @Override
        public TypedElement getDeclaringType() {
            return constructor.getDeclaringType();
        }

    }

    private record BeanMethodData(MethodElement methodElement, int dispatchIndex) {
    }

    /**
     * @param name
     * @param type
     * @param readType
     * @param writeType
     * @param getDispatchIndex
     * @param setDispatchIndex
     * @param withMethodDispatchIndex
     * @param isReadOnly
     */
    private record BeanPropertyData(@NonNull String name,
                                    @NonNull ClassElement type,
                                    @Nullable ClassElement readType,
                                    @Nullable ClassElement writeType,
                                    int getDispatchIndex,
                                    int setDispatchIndex,
                                    int withMethodDispatchIndex,
                                    boolean isReadOnly) {
    }

    /**
     * index to be created.
     *
     * @param annotationName The annotation name
     * @param value          The annotation value
     */
    private record AnnotationWithValue(@NonNull String annotationName, @Nullable String value) {

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
            return annotationName.hashCode();
        }
    }
}
