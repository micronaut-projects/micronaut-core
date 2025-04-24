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
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.annotation.AnnotationMetadataReference;
import io.micronaut.inject.annotation.AnnotationMetadataWriter;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
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
import io.micronaut.inject.processing.JavaModelUtils;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.AbstractClassFileWriter;
import io.micronaut.inject.writer.ClassWriterOutputVisitor;
import io.micronaut.inject.writer.DispatchWriter;
import io.micronaut.inject.writer.EvaluatedExpressionProcessor;
import io.micronaut.inject.writer.StringSwitchWriter;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static io.micronaut.inject.writer.AbstractAnnotationMetadataWriter.FIELD_ANNOTATION_METADATA;
import static io.micronaut.inject.writer.AbstractAnnotationMetadataWriter.initializeAnnotationMetadata;
import static io.micronaut.inject.writer.AbstractAnnotationMetadataWriter.writeAnnotationDefault;
import static io.micronaut.inject.writer.WriterUtils.invokeBeanConstructor;

/**
 * A class file writer that writes a {@link BeanIntrospectionReference} and associated
 * {@link BeanIntrospection} for the given class.
 *
 * @author graemerocher
 * @author Denis Stepanov
 * @since 1.1
 */
@Internal
final class BeanIntrospectionWriter extends AbstractClassFileWriter {
    private static final String INTROSPECTION_SUFFIX = "$Introspection";

    private static final String FIELD_CONSTRUCTOR_ANNOTATION_METADATA = "$FIELD_CONSTRUCTOR_ANNOTATION_METADATA";
    private static final String FIELD_CONSTRUCTOR_ARGUMENTS = "$CONSTRUCTOR_ARGUMENTS";
    private static final String FIELD_BEAN_PROPERTIES_REFERENCES = "$PROPERTIES_REFERENCES";
    private static final String FIELD_BEAN_METHODS_REFERENCES = "$METHODS_REFERENCES";
    private static final String FIELD_ENUM_CONSTANTS_REFERENCES = "$ENUM_CONSTANTS_REFERENCES";
    private static final Method FIND_PROPERTY_BY_INDEX_METHOD = Method.getMethod(
        ReflectionUtils.getRequiredInternalMethod(AbstractInitializableBeanIntrospection.class, "getPropertyByIndex", int.class)
    );
    private static final Method FIND_INDEXED_PROPERTY_METHOD = Method.getMethod(
        ReflectionUtils.getRequiredInternalMethod(AbstractInitializableBeanIntrospection.class, "findIndexedProperty", Class.class, String.class)
    );
    private static final Method GET_INDEXED_PROPERTIES = Method.getMethod(
        ReflectionUtils.getRequiredInternalMethod(AbstractInitializableBeanIntrospection.class, "getIndexedProperties", Class.class)
    );
    private static final Method GET_BP_INDEXED_SUBSET_METHOD = Method.getMethod(
        ReflectionUtils.getRequiredInternalMethod(AbstractInitializableBeanIntrospection.class, "getBeanPropertiesIndexedSubset", int[].class)
    );
    private static final Method COLLECTIONS_EMPTY_LIST = Method.getMethod(
        ReflectionUtils.getRequiredInternalMethod(Collections.class, "emptyList")
    );
    private static final String METHOD_IS_BUILDABLE = "isBuildable";

    private final VisitorContext visitorContext;
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
    private final List<BeanMethodData> beanMethods = new ArrayList<>();

    private final DispatchWriter dispatchWriter;
    private final EvaluatedExpressionProcessor evaluatedExpressionProcessor;
    private final AnnotationMetadata annotationMetadata;
    private final Map<String, GeneratorAdapter> loadTypeMethods = new HashMap<>();
    private final Map<String, Integer> defaults = new HashMap<>();

    /**
     * Default constructor.
     *
     * @param classElement           The class element
     * @param annotationMetadata The bean annotation metadata
     * @param visitorContext          The visitor context
     */
    BeanIntrospectionWriter(String targetPackage, ClassElement classElement, AnnotationMetadata annotationMetadata,
                            VisitorContext visitorContext) {
        super(classElement);
        this.visitorContext = visitorContext;
        final String name = classElement.getName();
        this.classElement = classElement;
        this.introspectionName = computeShortIntrospectionName(targetPackage, name);
        this.introspectionType = getTypeReferenceForName(introspectionName);
        this.beanType = getTypeReferenceForName(name);
        this.dispatchWriter = new DispatchWriter(introspectionType, Type.getType(AbstractInitializableBeanIntrospection.class));
        this.annotationMetadata = annotationMetadata.getTargetAnnotationMetadata();
        evaluatedExpressionProcessor = new EvaluatedExpressionProcessor(visitorContext, getOriginatingElement());
        evaluatedExpressionProcessor.processEvaluatedExpressions(annotationMetadata, null);
    }

    /**
     * Constructor used to generate a reference for already compiled classes.
     *
     * @param generatingType         The originating type
     * @param index                  A unique index
     * @param originatingElement     The originating element
     * @param classElement           The class element
     * @param annotationMetadata The bean annotation metadata
     * @param visitorContext          The visitor context
     */
    BeanIntrospectionWriter(
        String targetPackage,
        String generatingType,
        int index,
        ClassElement originatingElement,
        ClassElement classElement,
        AnnotationMetadata annotationMetadata,
        VisitorContext visitorContext) {
        super(originatingElement);
        this.visitorContext = visitorContext;
        final String className = classElement.getName();
        this.classElement = classElement;
        this.introspectionName = computeIntrospectionName(targetPackage, className);
        this.introspectionType = getTypeReferenceForName(introspectionName);
        this.beanType = getTypeReferenceForName(className);
        this.dispatchWriter = new DispatchWriter(introspectionType);
        this.annotationMetadata = annotationMetadata.getTargetAnnotationMetadata();
        evaluatedExpressionProcessor = new EvaluatedExpressionProcessor(visitorContext, getOriginatingElement());
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
    public Type getBeanType() {
        return beanType;
    }

    /**
     * Visit a property.
     *
     * @param type               The property type
     * @param genericType        The generic type
     * @param name               The property name
     * @param readMember         The read method
     * @param readType           The read type
     * @param writeMember        The write member
     * @param writeType          The write type
     * @param isReadOnly         Is read only
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
        this.evaluatedExpressionProcessor.processEvaluatedExpressions(genericType.getAnnotationMetadata(), classElement);
        int readDispatchIndex = -1;
        if (readMember != null) {
            if (readMember instanceof MethodElement element) {
                readDispatchIndex = dispatchWriter.addMethod(classElement, element, true);
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
                writeDispatchIndex = dispatchWriter.addMethod(classElement, element, true);
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
                            && methodElement.getGenericReturnType().getName().equals(classElement.getName())
                            && type.getType().isAssignable(parameters[0].getType());
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
                "Cannot mutate property [" + name + "] that is not mutable via a setter method, field or constructor argument for type: " + beanType.getClassName()
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
            int dispatchIndex = dispatchWriter.addMethod(classElement, element);
            beanMethods.add(new BeanMethodData(element, dispatchIndex));
            this.evaluatedExpressionProcessor.processEvaluatedExpressions(element.getAnnotationMetadata(), classElement);
            for (ParameterElement parameter : element.getParameters()) {
                this.evaluatedExpressionProcessor.processEvaluatedExpressions(parameter.getAnnotationMetadata(), classElement);
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

            loadTypeMethods.clear();
        }
    }

    private void buildStaticInit(ClassWriter classWriter, boolean isEnum) {
        GeneratorAdapter staticInit = visitStaticInitializer(classWriter);
        Map<String, Integer> defaults = new HashMap<>();

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
                    annotationMetadata,
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
        if (!beanProperties.isEmpty()) {
            Type refType = Type.getType(AbstractInitializableBeanIntrospection.BeanPropertyRef.class);
            List<Method> methods = new ArrayList<>();
            for (BeanPropertyData propertyData : beanProperties) {
                Type methodType = Type.getMethodType(refType);
                String methodName = "$property$" + propertyData.name + "$metadata";
                GeneratorAdapter methodMetadata = new GeneratorAdapter(
                    classWriter.visitMethod(
                        ACC_PRIVATE | ACC_STATIC | ACC_FINAL,
                        methodName,
                        methodType.getDescriptor(),
                        null,
                        null
                    ), ACC_PUBLIC, methodName, methodType.getDescriptor());
                pushBeanPropertyReference(
                    classWriter,
                    methodMetadata,
                    propertyData
                );
                methodMetadata.returnValue();
                methodMetadata.visitMaxs(2, 1);
                methodMetadata.endMethod();
                methods.add(new Method(methodName, refType, new Type[0]));
            }

            Type fieldType = Type.getType(AbstractInitializableBeanIntrospection.BeanPropertyRef[].class);
            classWriter.visitField(ACC_PRIVATE | ACC_FINAL | ACC_STATIC, FIELD_BEAN_PROPERTIES_REFERENCES, fieldType.getDescriptor(), null, null);

            pushNewArray(
                staticInit,
                AbstractInitializableBeanIntrospection.BeanPropertyRef.class,
                methods,
                method -> staticInit.invokeStatic(introspectionType, method)
            );
            staticInit.putStatic(introspectionType, FIELD_BEAN_PROPERTIES_REFERENCES, fieldType);
        }
        if (!beanMethods.isEmpty()) {
            Type refType = Type.getType(AbstractInitializableBeanIntrospection.BeanMethodRef.class);
            List<Method> methods = new ArrayList<>();
            Set<String> usedNames = new HashSet<>();
            for (BeanMethodData beanMethod : beanMethods) {
                String methodId = beanMethod.methodElement().getName();
                int index = 2;
                while (!usedNames.add(methodId)) {
                    methodId += index++;
                }
                Type methodType = Type.getMethodType(refType);
                String methodName = "$method$" + methodId + "$metadata";
                GeneratorAdapter methodMetadata = new GeneratorAdapter(
                    classWriter.visitMethod(
                        ACC_PRIVATE | ACC_STATIC | ACC_FINAL,
                        methodName,
                        methodType.getDescriptor(),
                        null,
                        null
                    ), ACC_PUBLIC, methodName, methodType.getDescriptor());
                pushBeanMethodReference(
                    classWriter,
                    methodMetadata,
                    beanMethod
                );
                methodMetadata.returnValue();
                methodMetadata.visitMaxs(2, 1);
                methodMetadata.endMethod();
                methods.add(new Method(methodName, refType, new Type[0]));
            }

            Type fieldType = Type.getType(AbstractInitializableBeanIntrospection.BeanMethodRef[].class);
            classWriter.visitField(ACC_PRIVATE | ACC_FINAL | ACC_STATIC, FIELD_BEAN_METHODS_REFERENCES, fieldType.getDescriptor(), null, null);
            pushNewArray(
                staticInit,
                AbstractInitializableBeanIntrospection.BeanMethodRef.class,
                methods,
                method -> staticInit.invokeStatic(introspectionType, method)
            );
            staticInit.putStatic(introspectionType, FIELD_BEAN_METHODS_REFERENCES, fieldType);
        }
        if (isEnum) {
            Type type = Type.getType(AbstractEnumBeanIntrospectionAndReference.EnumConstantDynamicRef[].class);
            classWriter.visitField(
                ACC_PRIVATE | ACC_FINAL | ACC_STATIC, FIELD_ENUM_CONSTANTS_REFERENCES,
                type.getDescriptor(),
                null,
                null
            );
            pushNewArray(staticInit, AbstractEnumBeanIntrospectionAndReference.EnumConstantDynamicRef.class, ((EnumElement) classElement).elements(), enumConstantElement -> {
                pushEnumConstantReference(
                    classWriter,
                    staticInit,
                    enumConstantElement
                );
            });
            staticInit.putStatic(introspectionType, FIELD_ENUM_CONSTANTS_REFERENCES, type);
        }

        int indexesIndex = 0;
        for (String annotationName : indexByAnnotations.keySet()) {
            int[] indexes = indexByAnnotations.get(annotationName)
                .stream()
                .mapToInt(this::getPropertyIndex)
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

        writeAnnotationDefault(classWriter, staticInit, introspectionType, annotationMetadata, defaults, loadTypeMethods);
        initializeAnnotationMetadata(staticInit, classWriter, introspectionType, annotationMetadata, defaults, loadTypeMethods);

        staticInit.returnValue();
        staticInit.visitMaxs(DEFAULT_MAX_STACK, 1);
        staticInit.visitEnd();
    }

    private void pushBeanPropertyReference(ClassWriter classWriter,
                                           GeneratorAdapter staticInit,
                                           BeanPropertyData beanPropertyData) {

        Runnable pushTypeArgument = () -> pushCreateArgument(
            annotationMetadata,
            classElement.getName(),
            introspectionType,
            classWriter,
            staticInit,
            beanPropertyData.name,
            beanPropertyData.type,
            defaults,
            loadTypeMethods
        );

        int typeLocal = -1;
        int readTypeLocal = -1;
        int writeTypeLocal = -1;

        Type argumentType = Type.getType(Argument.class);
        if (beanPropertyData.type.equals(beanPropertyData.readType)) {
            typeLocal = staticInit.newLocal(argumentType);
            pushTypeArgument.run();
            staticInit.storeLocal(typeLocal, argumentType);
            readTypeLocal = typeLocal;
        }
        if (beanPropertyData.type.equals(beanPropertyData.writeType)) {
            if (typeLocal == -1) {
                typeLocal = staticInit.newLocal(argumentType);
                pushTypeArgument.run();
                staticInit.storeLocal(typeLocal, argumentType);
            }
            writeTypeLocal = typeLocal;
        }

        staticInit.newInstance(Type.getType(AbstractInitializableBeanIntrospection.BeanPropertyRef.class));
        staticInit.dup();

        if (typeLocal != -1) {
            staticInit.loadLocal(typeLocal, argumentType);
        } else {
            pushTypeArgument.run();
        }

        if (beanPropertyData.readType == null) {
            staticInit.push((String) null);
        } else if (readTypeLocal != -1) {
            staticInit.loadLocal(readTypeLocal, argumentType);
        } else {
            pushCreateArgument(
                annotationMetadata,
                classElement.getName(),
                introspectionType,
                classWriter,
                staticInit,
                beanPropertyData.name,
                beanPropertyData.readType,
                defaults,
                loadTypeMethods
            );
        }

        if (beanPropertyData.writeType == null) {
            staticInit.push((String) null);
        } else if (writeTypeLocal != -1) {
            staticInit.loadLocal(writeTypeLocal, argumentType);
        } else {
            pushCreateArgument(
                annotationMetadata,
                classElement.getName(),
                introspectionType,
                classWriter,
                staticInit,
                beanPropertyData.name,
                beanPropertyData.writeType,
                defaults,
                loadTypeMethods
            );
        }
        staticInit.push(beanPropertyData.getDispatchIndex);
        staticInit.push(beanPropertyData.setDispatchIndex);
        staticInit.push(beanPropertyData.withMethodDispatchIndex);
        staticInit.push(beanPropertyData.isReadOnly);
        staticInit.push(!beanPropertyData.isReadOnly || hasAssociatedConstructorArgument(beanPropertyData.name, beanPropertyData.type));

        invokeConstructor(
            staticInit,
            AbstractInitializableBeanIntrospection.BeanPropertyRef.class,
            Argument.class,
            Argument.class,
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
        pushReturnTypeArgument(annotationMetadata, introspectionType, classWriter, staticInit, classElement.getName(), genericReturnType, defaults, loadTypeMethods);
        // 2: name
        staticInit.push(beanMethodData.methodElement.getName());
        // 3: annotation metadata
        pushAnnotationMetadata(classWriter, staticInit, beanMethodData.methodElement.getAnnotationMetadata());
        // 4: arguments
        if (beanMethodData.methodElement.getParameters().length == 0) {
            staticInit.push((String) null);
        } else {
            pushBuildArgumentsForMethod(
                annotationMetadata,
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

    private void pushEnumConstantReference(ClassWriter classWriter,
                                           GeneratorAdapter staticInit,
                                           EnumConstantElement enumConstantElement) {
        staticInit.newInstance(Type.getType(AbstractEnumBeanIntrospectionAndReference.EnumConstantDynamicRef.class));
        staticInit.dup();
        // 1: push annotation class value
        AnnotationMetadataWriter.invokeLoadClassValueMethod(introspectionType, classWriter, staticInit, loadTypeMethods, new AnnotationClassValue<>(enumConstantElement.getOwningType().getName()));
        // 2: push enum name
        staticInit.push(enumConstantElement.getName());
        // 3: annotation metadata
        AnnotationMetadata annotationMetadata = enumConstantElement.getAnnotationMetadata();
        if (annotationMetadata.isEmpty()) {
            staticInit.getStatic(Type.getType(AnnotationMetadata.class), "EMPTY_METADATA", Type.getType(AnnotationMetadata.class));
        } else {
            pushAnnotationMetadata(classWriter, staticInit, annotationMetadata);
        }

        invokeConstructor(
            staticInit,
            AbstractEnumBeanIntrospectionAndReference.EnumConstantDynamicRef.class,
            AnnotationClassValue.class,
            String.class,
            AnnotationMetadata.class
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
        boolean isEnum = classElement.isEnum();
        final Type superType = isEnum ? Type.getType(AbstractEnumBeanIntrospectionAndReference.class) : Type.getType(AbstractInitializableBeanIntrospectionAndReference.class);

        ClassWriter classWriter = new AptClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES, visitorContext);
        classWriter.visit(
            V17,
            ACC_SYNTHETIC | ACC_FINAL | ACC_PUBLIC,
            introspectionType.getInternalName(),
            null,
            superType.getInternalName(),
            null
        );

        classWriterOutputVisitor.visitServiceDescriptor(BeanIntrospectionReference.class, introspectionName, getOriginatingElement());

        annotateAsGeneratedAndService(classWriter, introspectionName);
        // init expressions at build time
        evaluatedExpressionProcessor.registerExpressionForBuildTimeInit(classWriter);

        buildStaticInit(classWriter, isEnum);

        final GeneratorAdapter constructorWriter = startConstructor(classWriter);

        // writer the constructor
        constructorWriter.loadThis();
        // 1st argument: The bean type
        constructorWriter.push(beanType);

        // 2nd argument: The annotation metadata
        if (annotationMetadata == null || annotationMetadata.isEmpty()) {
            constructorWriter.visitInsn(ACONST_NULL);
        } else {
            constructorWriter.getStatic(introspectionType, FIELD_ANNOTATION_METADATA, Type.getType(AnnotationMetadata.class));
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

        if (beanProperties.isEmpty()) {
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
        if (isEnum) {
            constructorWriter.getStatic(introspectionType,
                FIELD_ENUM_CONSTANTS_REFERENCES,
                Type.getType(AbstractEnumBeanIntrospectionAndReference.EnumConstantDynamicRef[].class)
            );
            invokeConstructor(
                constructorWriter,
                AbstractEnumBeanIntrospectionAndReference.class,
                Class.class,
                AnnotationMetadata.class,
                AnnotationMetadata.class,
                Argument[].class,
                AbstractInitializableBeanIntrospection.BeanPropertyRef[].class,
                AbstractInitializableBeanIntrospection.BeanMethodRef[].class,
                AbstractEnumBeanIntrospectionAndReference.EnumConstantDynamicRef[].class
            );
        } else {
            invokeConstructor(
                constructorWriter,
                AbstractInitializableBeanIntrospectionAndReference.class,
                Class.class,
                AnnotationMetadata.class,
                AnnotationMetadata.class,
                Argument[].class,
                AbstractInitializableBeanIntrospection.BeanPropertyRef[].class,
                AbstractInitializableBeanIntrospection.BeanMethodRef[].class
            );
        }

        constructorWriter.returnValue();
        constructorWriter.visitMaxs(2, 1);
        constructorWriter.visitEnd();

        dispatchWriter.buildDispatchOneMethod(classWriter);
        dispatchWriter.buildDispatchMethod(classWriter);
        dispatchWriter.buildGetTargetMethodByIndex(classWriter);
        buildFindIndexedProperty(classWriter);
        buildGetIndexedProperties(classWriter);
        boolean hasBuilder = annotationMetadata != null &&
            (annotationMetadata.isPresent(Introspected.class, "builder") || annotationMetadata.hasDeclaredAnnotation("lombok.Builder"));
        if (defaultConstructor != null || constructor != null) {
            writeBooleanMethod(classWriter, "hasConstructor", true);
        }
        if (defaultConstructor != null) {

            writeInstantiateMethod(classWriter, defaultConstructor, "instantiate");
            // in case invoked directly or via instantiateUnsafe
            if (constructor == null) {
                writeInstantiateMethod(classWriter, defaultConstructor, "instantiateInternal", Object[].class);
                writeBooleanMethod(classWriter, METHOD_IS_BUILDABLE, true);
            }
        }

        if (constructor != null) {
            if (defaultConstructor == null) {
                if (ArrayUtils.isEmpty(constructor.getParameters())) {
                    writeInstantiateMethod(classWriter, constructor, "instantiate");
                } else {
                    List<ParameterElement> constructorArguments = Arrays.asList(constructor.getParameters());
                    boolean kotlinAllDefault = constructorArguments.stream().allMatch(p -> p instanceof KotlinParameterElement kp && kp.hasDefault());
                    if (kotlinAllDefault) {
                        writeInstantiateMethod(classWriter, constructor, "instantiate");
                    }
                }
            }
            writeInstantiateMethod(classWriter, constructor, "instantiateInternal", Object[].class);
            writeBooleanMethod(classWriter, METHOD_IS_BUILDABLE, true);
        } else if (defaultConstructor == null) {
            writeBooleanMethod(classWriter, METHOD_IS_BUILDABLE, hasBuilder);
        }

        writeBooleanMethod(classWriter, "hasBuilder", hasBuilder);

        for (GeneratorAdapter method : loadTypeMethods.values()) {
            method.visitMaxs(3, 1);
            method.visitEnd();
        }

        classWriter.visitEnd();

        try (OutputStream outputStream = classWriterOutputVisitor.visitClass(introspectionName, getOriginatingElements())) {
            outputStream.write(classWriter.toByteArray());
        }
    }

    private void writeBooleanMethod(ClassWriter classWriter, String methodName, boolean state) {
        GeneratorAdapter booleanMethod = startPublicFinalMethodZeroArgs(classWriter, boolean.class, methodName);
        booleanMethod.push(state);
        booleanMethod.returnValue();
        booleanMethod.visitMaxs(2, 1);
        booleanMethod.endMethod();
    }

    private void buildFindIndexedProperty(ClassWriter classWriter) {
        if (indexByAnnotationAndValue.isEmpty()) {
            return;
        }
        GeneratorAdapter writer = new GeneratorAdapter(classWriter.visitMethod(
            ACC_PROTECTED | ACC_FINAL,
            FIND_INDEXED_PROPERTY_METHOD.getName(),
            FIND_INDEXED_PROPERTY_METHOD.getDescriptor(),
            null,
            null),
            ACC_PROTECTED | ACC_FINAL,
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
            ACC_PUBLIC | ACC_FINAL,
            GET_INDEXED_PROPERTIES.getName(),
            GET_INDEXED_PROPERTIES.getDescriptor(),
            null,
            null),
            ACC_PUBLIC | ACC_FINAL,
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
        throw new IllegalStateException("Property not found: " + propertyName + " " + classElement.getName());
    }

    private void writeInstantiateMethod(ClassWriter classWriter, MethodElement constructor, String methodName, Class<?>... args) {
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

        if (args.length == 0) {
            invokeBeanConstructor(ClassElement.of(introspectionName), instantiateInternal, constructor, true, null);
        } else {
            invokeBeanConstructor(ClassElement.of(introspectionName), instantiateInternal, constructor, true, (index, parameter) -> {
                instantiateInternal.loadArg(0);
                instantiateInternal.push(index);
                instantiateInternal.arrayLoad(TYPE_OBJECT);
                pushCastToType(instantiateInternal, JavaModelUtils.getTypeReference(parameter));
            });
        }

        instantiateInternal.returnValue();
        instantiateInternal.visitMaxs(3, 1);
        instantiateInternal.visitEnd();
    }

    private void pushAnnotationMetadata(ClassWriter classWriter, GeneratorAdapter staticInit, AnnotationMetadata annotationMetadata) {
        MutableAnnotationMetadata.contributeDefaults(
            this.annotationMetadata,
            annotationMetadata
        );

        annotationMetadata = annotationMetadata.getTargetAnnotationMetadata();
        if (annotationMetadata.isEmpty()) {
            staticInit.push((String) null);
        } else if (annotationMetadata instanceof AnnotationMetadataReference annotationMetadataReference) {
            String className = annotationMetadataReference.getClassName();
            staticInit.getStatic(getTypeReferenceForName(className), FIELD_ANNOTATION_METADATA, Type.getType(AnnotationMetadata.class));
        } else if (annotationMetadata instanceof AnnotationMetadataHierarchy annotationMetadataHierarchy) {
            AnnotationMetadataWriter.instantiateNewMetadataHierarchy(
                introspectionType,
                classWriter,
                staticInit,
                annotationMetadataHierarchy,
                defaults,
                loadTypeMethods);
        } else if (annotationMetadata instanceof MutableAnnotationMetadata mutableAnnotationMetadata) {
            AnnotationMetadataWriter.instantiateNewMetadata(
                introspectionType,
                classWriter,
                staticInit,
                mutableAnnotationMetadata,
                defaults,
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

    private record ExceptionDispatchTarget(Class<?> exceptionType, String message) implements DispatchWriter.DispatchTarget {

        @Override
        public boolean supportsDispatchOne() {
            return true;
        }

        @Override
        public void writeDispatchOne(GeneratorAdapter writer, int index) {
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
        public boolean writeDispatchOne(GeneratorAdapter writer, int methodIndex, Map<String, DispatchWriter.DispatchTargetState> stateMap) {
            CopyConstructorDispatchState state = (CopyConstructorDispatchState) stateMap.computeIfAbsent(CopyConstructorDispatchState.KEY, k -> new CopyConstructorDispatchState(constructor, writer.newLabel()));
            state.propertyNames.put(parameterName, methodIndex);
            writer.goTo(state.label);
            return false;
        }
    }

    /**
     * Shared implementation of {@link CopyConstructorDispatchTarget#writeDispatchOne}. <br>
     *
     * A non-shared copy constructor implementation would be O(n²) in the number of properties: For
     * every property we generate a constructor call, and that constructor call has that many
     * parameters too that all have to be loaded.<br>
     *
     * This shared implementation instead only generates one constructor call, and branches on each
     * loaded property to figure out whether to copy it or to use the replacement from the
     * {@code dispatchOne} parameter.
     */
    private class CopyConstructorDispatchState implements DispatchWriter.DispatchTargetState {
        static final String KEY = CopyConstructorDispatchState.class.getName();

        final MethodElement constructor;
        final Label label;
        final Map<String, Integer> propertyNames = new HashMap<>();

        CopyConstructorDispatchState(MethodElement constructor, Label label) {
            this.constructor = constructor;
            this.label = label;
        }

        @Override
        public void complete(GeneratorAdapter writer) {
            writer.visitLabel(label);
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
                        nonMutableMessage = "Cannot create copy of type [" + beanType.getClassName() + "]. Property of type [" + propertyType.getName() + "] is not assignable to constructor argument [" + parameterName + "]";
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

                // NOTE: It doesn't make sense to check defaults for the copy constructor

                invokeBeanConstructor(ClassElement.of(introspectionName), writer, constructor, false, (paramIndex, parameter) -> {
                    Object constructorArgument = constructorArguments[paramIndex];
                    TypedElement supplierType;
                    if (constructorArgument instanceof MethodElement readMethod) {
                        supplierType = readMethod.getReturnType();
                    } else if (constructorArgument instanceof FieldElement fieldElement) {
                        supplierType = fieldElement;
                    } else {
                        throw new IllegalStateException();
                    }

                    Label endOfProperty = null;
                    Integer target = propertyNames.get(parameter.getName());
                    if (target != null) {
                        if (propertyNames.size() == 1) {
                            writer.loadArg(2); // Load new property value
                            pushCastFromObjectToType(writer, parameter);
                            // if we're the only replaceable property, we can skip the second branch
                            return;
                        }
                        // replace property with new value
                        Label nonReplaceBranch = writer.newLabel();
                        writer.loadArg(0);
                        writer.push(target);
                        writer.ifICmp(GeneratorAdapter.NE, nonReplaceBranch);
                        writer.loadArg(2); // Load new property value
                        pushCastFromObjectToType(writer, parameter);
                        endOfProperty = writer.newLabel();
                        writer.goTo(endOfProperty);
                        writer.visitLabel(nonReplaceBranch);
                    }

                    if (constructorArgument instanceof MethodElement readMethod) {
                        writer.loadLocal(prevBeanTypeLocal, beanType);
                        invokeMethod(writer, readMethod);
                    } else {
                        writer.loadLocal(prevBeanTypeLocal, beanType);
                        invokeGetField(writer, (FieldElement) constructorArgument);
                    }
                    pushCastToType(writer, supplierType, parameter);

                    if (endOfProperty != null) {
                        writer.visitLabel(endOfProperty);
                    }

                });

                List<BeanPropertyData> readWriteProps = beanProperties.stream()
                    .filter(bp -> bp.setDispatchIndex != -1 && bp.getDispatchIndex != -1 && !constructorProps.contains(bp)).toList();

                if (!readWriteProps.isEmpty()) {
                    int beanTypeLocal = writer.newLocal(beanType);
                    writer.storeLocal(beanTypeLocal, beanType);

                    for (BeanPropertyData readWriteProp : readWriteProps) {
                        DispatchWriter.DispatchTarget readDispatch = dispatchWriter.getDispatchTargets().get(readWriteProp.getDispatchIndex);
                        if (readDispatch instanceof DispatchWriter.MethodDispatchTarget methodDispatchTarget) {
                            MethodElement readMethod = methodDispatchTarget.getMethodElement();
                            writer.loadLocal(beanTypeLocal, beanType);
                            writer.loadLocal(prevBeanTypeLocal, beanType);
                            invokeMethod(writer, readMethod);
                        } else if (readDispatch instanceof DispatchWriter.FieldGetDispatchTarget fieldGetDispatchTarget) {
                            FieldElement fieldElement = fieldGetDispatchTarget.getField();
                            writer.loadLocal(beanTypeLocal, beanType);
                            writer.loadLocal(prevBeanTypeLocal, beanType);
                            invokeGetField(writer, fieldElement);
                        } else {
                            throw new IllegalStateException();
                        }

                        DispatchWriter.DispatchTarget writeDispatch = dispatchWriter.getDispatchTargets().get(readWriteProp.setDispatchIndex);
                        if (writeDispatch instanceof DispatchWriter.MethodDispatchTarget methodDispatchTarget) {
                            MethodElement writeMethod = methodDispatchTarget.getMethodElement();
                            ClassElement writeReturnType = invokeMethod(writer, writeMethod);
                            if (!writeReturnType.isVoid()) {
                                writer.pop();
                            }
                        } else if (writeDispatch instanceof DispatchWriter.FieldSetDispatchTarget fieldSetDispatchTarget) {
                            FieldElement fieldElement = fieldSetDispatchTarget.getField();
                            invokeSetField(writer, fieldElement);
                        } else {
                            throw new IllegalStateException();
                        }

                    }
                    writer.loadLocal(beanTypeLocal, beanType);
                }
                writer.returnValue();
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

        private void invokeGetField(GeneratorAdapter mutateMethod, FieldElement field) {
            mutateMethod.getField(beanType, field.getName(), JavaModelUtils.getTypeReference(field.getType()));
        }

        private void invokeSetField(GeneratorAdapter mutateMethod, FieldElement field) {
            mutateMethod.putField(beanType, field.getName(), JavaModelUtils.getTypeReference(field.getType()));
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
     * @param value The annotation value
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
