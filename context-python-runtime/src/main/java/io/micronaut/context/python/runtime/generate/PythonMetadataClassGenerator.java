/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.context.python.runtime.generate;

import io.micronaut.context.python.runtime.ModelTypes;
import io.micronaut.context.python.runtime.model.AnnotationMetadataModel;
import io.micronaut.context.python.runtime.model.ArgumentModel;
import io.micronaut.context.python.runtime.model.BeanDefinitionModel;
import io.micronaut.context.python.runtime.model.ClassModel;
import io.micronaut.context.python.runtime.model.ExecutableMethodModel;
import io.micronaut.context.python.runtime.model.FactoryMethodModel;
import io.micronaut.context.python.runtime.model.InjectedMethodModel;
import io.micronaut.context.python.runtime.model.InjectionPointModel;
import io.micronaut.context.python.runtime.model.IntrospectionModel;
import io.micronaut.context.python.runtime.model.MethodModel;
import io.micronaut.context.python.runtime.model.PropertyIndexModel;
import io.micronaut.context.python.runtime.model.PropertyModel;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Generates the {@code $Definition} and {@code $Introspection} classes of a Python class from its resolved model.
 * The generated classes extend the same base classes the build-time writers use, so the dependency injection and
 * introspection behavior is the framework's; only the member invocations, from the recorded descriptors, and the
 * initialization from the model are generated. Generation is a pure function of the model: the same model yields the
 * same bytes in both backends.
 *
 * @since 5.3.0
 */
@Internal
public final class PythonMetadataClassGenerator {

    private static final String REQUIRES = "io.micronaut.context.annotation.Requires";
    private static final String CONTEXT_SCOPE = "io.micronaut.context.annotation.Context";
    private static final String SUPPORT = "io/micronaut/context/python/runtime/PythonMetadataSupport";
    private static final String DEFINITION_STATE = "io/micronaut/context/python/runtime/PythonDefinitionState";
    private static final String INTROSPECTION_STATE = "io/micronaut/context/python/runtime/PythonIntrospectionState";
    private static final String DEFINITION_SUPER = "io/micronaut/context/AbstractInitializableBeanDefinitionAndReference";
    private static final String INTROSPECTION_SUPER = "io/micronaut/inject/beans/AbstractInitializableBeanIntrospectionAndReference";
    private static final String BEAN_DEFINITION = "io/micronaut/inject/BeanDefinition";
    private static final String BEAN_CONTEXT = "io/micronaut/context/BeanContext";
    private static final String RESOLUTION_CONTEXT = "io/micronaut/context/BeanResolutionContext";
    private static final String QUALIFIER = "io/micronaut/context/Qualifier";
    private static final String ARGUMENT = "io/micronaut/core/type/Argument";
    private static final String ANNOTATION_METADATA = "io/micronaut/core/annotation/AnnotationMetadata";
    private static final String CONDITION = "io/micronaut/context/condition/Condition";
    private static final String METHOD_REFERENCE = "io/micronaut/context/AbstractInitializableBeanDefinition$MethodReference";
    private static final String METHOD_OR_FIELD_REFERENCE = "io/micronaut/context/AbstractInitializableBeanDefinition$MethodOrFieldReference";
    private static final String PRECALCULATED_INFO = "io/micronaut/context/AbstractInitializableBeanDefinition$PrecalculatedInfo";
    private static final String PROPERTY_REF = "io/micronaut/inject/beans/AbstractInitializableBeanIntrospection$BeanPropertyRef";
    private static final String METHOD_REF = "io/micronaut/inject/beans/AbstractInitializableBeanIntrospection$BeanMethodRef";
    private static final String GENERATED = "Lio/micronaut/core/annotation/Generated;";
    private static final String EXEC_SUPER = "io/micronaut/context/AbstractExecutableMethodsDefinition";
    private static final String EXEC_METHOD_REFERENCE = "io/micronaut/context/AbstractExecutableMethodsDefinition$MethodReference";
    private static final String INTROSPECTION_BASE = "io/micronaut/inject/beans/AbstractInitializableBeanIntrospection";
    private static final String EXEC_SUFFIX = "$Exec";
    private static final String EXEC_FIELD = "$EXEC";
    private static final String STATE_FIELD = "$STATE";
    private static final String OBJECT = "java/lang/Object";
    private static final String OBJECT_DESC = "Ljava/lang/Object;";
    private static final String STATE_DESC_D = "L" + DEFINITION_STATE + ";";
    private static final String STATE_DESC_I = "L" + INTROSPECTION_STATE + ";";
    private static final String CONTEXT_PARAMS = "(L" + RESOLUTION_CONTEXT + ";L" + BEAN_CONTEXT + ";";
    private static final String LIFECYCLE_DESC = CONTEXT_PARAMS + OBJECT_DESC + ")" + OBJECT_DESC;

    private PythonMetadataClassGenerator() {
    }

    /**
     * Generates a bean definition class of a model.
     *
     * @param model      The class model
     * @param definition The definition, one of the model's
     * @return The class bytes
     */
    public static byte[] beanDefinition(ClassModel model, BeanDefinitionModel definition) {
        Type ownerType = ModelTypes.type(model.className());
        Type beanType = ModelTypes.type(definition.beanTypeName());
        String name = definition.definitionClassName().replace('.', '/');
        boolean postConstruct = definition.methods().stream().anyMatch(InjectedMethodModel::postConstruct);
        boolean preDestroy = definition.methods().stream().anyMatch(InjectedMethodModel::preDestroy);
        boolean inject = definition.methods().stream().anyMatch(m -> !m.postConstruct() && !m.preDestroy());
        List<String> interfaces = new ArrayList<>(2);
        if (postConstruct) {
            interfaces.add("io/micronaut/inject/InitializingBeanDefinition");
        }
        if (preDestroy) {
            interfaces.add("io/micronaut/inject/DisposableBeanDefinition");
        }
        ClassWriter writer = new GeneratedClassWriter();
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_SYNTHETIC, name,
            "L" + DEFINITION_SUPER + "<" + beanType.getDescriptor() + ">;", DEFINITION_SUPER, interfaces.toArray(String[]::new));
        generatedAnnotation(writer, "io.micronaut.inject.BeanDefinitionReference");
        writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, STATE_FIELD, STATE_DESC_D, null, null).visitEnd();
        boolean executable = !definition.executableMethods().isEmpty();
        String execName = name + EXEC_SUFFIX;
        String execDesc = "L" + execName + ";";
        if (executable) {
            writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, EXEC_FIELD, execDesc, null, null).visitEnd();
        }

        MethodVisitor clinit = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        clinit.visitCode();
        clinit.visitLdcInsn(ownerType);
        clinit.visitLdcInsn(definition.definitionClassName());
        clinit.visitMethodInsn(Opcodes.INVOKESTATIC, SUPPORT, "definitionState", "(Ljava/lang/Class;Ljava/lang/String;)" + STATE_DESC_D, false);
        clinit.visitFieldInsn(Opcodes.PUTSTATIC, name, STATE_FIELD, STATE_DESC_D);
        if (executable) {
            clinit.visitTypeInsn(Opcodes.NEW, execName);
            clinit.visitInsn(Opcodes.DUP);
            clinit.visitMethodInsn(Opcodes.INVOKESPECIAL, execName, "<init>", "()V", false);
            clinit.visitFieldInsn(Opcodes.PUTSTATIC, name, EXEC_FIELD, execDesc);
        }
        clinit.visitInsn(Opcodes.RETURN);
        clinit.visitMaxs(0, 0);
        clinit.visitEnd();

        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitLdcInsn(beanType);
        stateCall(init, name, STATE_DESC_D, DEFINITION_STATE, "constructor", "()L" + METHOD_OR_FIELD_REFERENCE + ";");
        stateCall(init, name, STATE_DESC_D, DEFINITION_STATE, "annotationMetadata", "()L" + ANNOTATION_METADATA + ";");
        stateCall(init, name, STATE_DESC_D, DEFINITION_STATE, "methodInjection", "()[L" + METHOD_REFERENCE + ";");
        init.visitInsn(Opcodes.ACONST_NULL);
        init.visitInsn(Opcodes.ACONST_NULL);
        if (executable) {
            init.visitFieldInsn(Opcodes.GETSTATIC, name, EXEC_FIELD, execDesc);
        } else {
            init.visitInsn(Opcodes.ACONST_NULL);
        }
        stateCall(init, name, STATE_DESC_D, DEFINITION_STATE, "typeArguments", "()Ljava/util/Map;");
        stateCall(init, name, STATE_DESC_D, DEFINITION_STATE, "info", "()L" + PRECALCULATED_INFO + ";");
        init.visitInsn(Opcodes.ICONST_0);
        init.visitTypeInsn(Opcodes.ANEWARRAY, CONDITION);
        init.visitInsn(Opcodes.ICONST_0);
        init.visitTypeInsn(Opcodes.ANEWARRAY, CONDITION);
        stateCall(init, name, STATE_DESC_D, DEFINITION_STATE, "failure", "()Ljava/lang/Throwable;");
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, DEFINITION_SUPER, "<init>",
            "(Ljava/lang/Class;L" + METHOD_OR_FIELD_REFERENCE + ";L" + ANNOTATION_METADATA + ";[L" + METHOD_REFERENCE
                + ";[Lio/micronaut/context/AbstractInitializableBeanDefinition$FieldReference;"
                + "[Lio/micronaut/context/AbstractInitializableBeanDefinition$AnnotationReference;"
                + "Lio/micronaut/inject/ExecutableMethodsDefinition;Ljava/util/Map;L" + PRECALCULATED_INFO + ";[L" + CONDITION
                + ";[L" + CONDITION + ";Ljava/lang/Throwable;)V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();

        MethodVisitor load = writer.visitMethod(Opcodes.ACC_PUBLIC, "load", "()L" + BEAN_DEFINITION + ";", null, null);
        load.visitCode();
        load.visitTypeInsn(Opcodes.NEW, name);
        load.visitInsn(Opcodes.DUP);
        load.visitMethodInsn(Opcodes.INVOKESPECIAL, name, "<init>", "()V", false);
        load.visitInsn(Opcodes.ARETURN);
        load.visitMaxs(0, 0);
        load.visitEnd();

        if (!hasStereotype(model, definition, REQUIRES)) {
            booleanMethod(writer, "isEnabled", "(L" + BEAN_CONTEXT + ";)Z", true);
            booleanMethod(writer, "isEnabled", "(L" + BEAN_CONTEXT + ";L" + RESOLUTION_CONTEXT + ";)Z", true);
        }

        if (model.declares(definition, CONTEXT_SCOPE)) {
            // The writer's eager initialization marker: a @Context bean is created when the context starts
            booleanMethod(writer, "isContextScope", "()Z", true);
        }

        if (definition.executableMethods().stream().anyMatch(ExecutableMethodModel::processOnStartup)) {
            MethodVisitor indexes = writer.visitMethod(Opcodes.ACC_PROTECTED, "getIndexesOfExecutableMethodsForProcessing", "()[I", null, null);
            indexes.visitCode();
            stateCall(indexes, name, STATE_DESC_D, DEFINITION_STATE, "processingIndexes", "()[I");
            indexes.visitInsn(Opcodes.ARETURN);
            indexes.visitMaxs(0, 0);
            indexes.visitEnd();
        }

        if (!definition.exposedTypes().isEmpty()) {
            MethodVisitor exposed = writer.visitMethod(Opcodes.ACC_PUBLIC, "getExposedTypes", "()Ljava/util/Set;", null, null);
            exposed.visitCode();
            stateCall(exposed, name, STATE_DESC_D, DEFINITION_STATE, "exposedTypes", "()Ljava/util/Set;");
            exposed.visitInsn(Opcodes.ARETURN);
            exposed.visitMaxs(0, 0);
            exposed.visitEnd();
            if (definition.typeArguments().isEmpty() && !definition.info().isContainerType()) {
                MethodVisitor candidate = writer.visitMethod(Opcodes.ACC_PUBLIC, "isCandidateBean", "(L" + ARGUMENT + ";)Z", null, null);
                candidate.visitCode();
                if (definition.exposedTypesDeclared()) {
                    // declared exposed types: a candidate for exactly those types
                    Label no = new Label();
                    candidate.visitVarInsn(Opcodes.ALOAD, 1);
                    candidate.visitJumpInsn(Opcodes.IFNULL, no);
                    stateCall(candidate, name, STATE_DESC_D, DEFINITION_STATE, "exposedTypes", "()Ljava/util/Set;");
                    candidate.visitVarInsn(Opcodes.ALOAD, 1);
                    candidate.visitMethodInsn(Opcodes.INVOKEINTERFACE, ARGUMENT, "getType", "()Ljava/lang/Class;", true);
                    candidate.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Set", "contains", "(Ljava/lang/Object;)Z", true);
                    candidate.visitInsn(Opcodes.IRETURN);
                    candidate.visitLabel(no);
                    candidate.visitInsn(Opcodes.ICONST_0);
                    candidate.visitInsn(Opcodes.IRETURN);
                } else {
                    candidate.visitVarInsn(Opcodes.ALOAD, 1);
                    candidate.visitMethodInsn(Opcodes.INVOKEINTERFACE, ARGUMENT, "getType", "()Ljava/lang/Class;", true);
                    candidate.visitLdcInsn(beanType);
                    candidate.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "isAssignableFrom", "(Ljava/lang/Class;)Z", false);
                    candidate.visitInsn(Opcodes.IRETURN);
                }
                candidate.visitMaxs(0, 0);
                candidate.visitEnd();
            }
        }

        MethodVisitor instantiate = writer.visitMethod(Opcodes.ACC_PUBLIC, "instantiate", CONTEXT_PARAMS + ")" + OBJECT_DESC, null, null);
        instantiate.visitCode();
        List<ArgumentModel> parameters = definition.constructor().parameters();
        FactoryMethodModel factory = definition.factory();
        if (factory == null) {
            instantiate.visitTypeInsn(Opcodes.NEW, beanType.getInternalName());
            instantiate.visitInsn(Opcodes.DUP);
        } else if (!factory.isStatic()) {
            // The writer's factory lookup: the factory bean through the resolution context, qualified by the factory class
            Type factoryType = ModelTypes.type(factory.factoryTypeName());
            instantiate.visitVarInsn(Opcodes.ALOAD, 1);
            instantiate.visitLdcInsn(factoryType);
            stateCall(instantiate, name, STATE_DESC_D, DEFINITION_STATE, "factoryQualifier", "()L" + QUALIFIER + ";");
            instantiate.visitMethodInsn(Opcodes.INVOKEINTERFACE, RESOLUTION_CONTEXT, "getBean", "(Ljava/lang/Class;L" + QUALIFIER + ";)" + OBJECT_DESC, true);
            instantiate.visitTypeInsn(Opcodes.CHECKCAST, factoryType.getInternalName());
            instantiate.visitVarInsn(Opcodes.ALOAD, 1);
            instantiate.visitMethodInsn(Opcodes.INVOKEINTERFACE, RESOLUTION_CONTEXT, "markDependentAsFactory", "()V", true);
        }
        for (int i = 0; i < parameters.size(); i++) {
            constructorArgument(instantiate, name, definition.constructor().injectionPoints().get(i), i);
            convert(instantiate, parameters.get(i).typeName());
        }
        if (factory == null) {
            instantiate.visitMethodInsn(Opcodes.INVOKESPECIAL, beanType.getInternalName(), "<init>", descriptor(parameters, "void"), false);
        } else {
            Type factoryType = ModelTypes.type(factory.factoryTypeName());
            instantiate.visitMethodInsn(factory.isStatic() ? Opcodes.INVOKESTATIC : Opcodes.INVOKEVIRTUAL, factoryType.getInternalName(),
                factory.methodName(), descriptor(parameters, factory.returnType().typeName()), false);
            box(instantiate, factory.returnType().typeName());
        }
        if (!inject && !postConstruct) {
            instantiate.visitInsn(Opcodes.ARETURN);
        } else {
            instantiate.visitVarInsn(Opcodes.ASTORE, 3);
            Label skip = new Label();
            instantiate.visitVarInsn(Opcodes.ALOAD, 1);
            instantiate.visitVarInsn(Opcodes.ALOAD, 0);
            instantiate.visitVarInsn(Opcodes.ALOAD, 3);
            instantiate.visitMethodInsn(Opcodes.INVOKEINTERFACE, RESOLUTION_CONTEXT, "shouldInitializeBean", "(L" + BEAN_DEFINITION + ";" + OBJECT_DESC + ")Z", true);
            instantiate.visitJumpInsn(Opcodes.IFEQ, skip);
            if (inject) {
                instantiate.visitVarInsn(Opcodes.ALOAD, 0);
                instantiate.visitVarInsn(Opcodes.ALOAD, 1);
                instantiate.visitVarInsn(Opcodes.ALOAD, 2);
                instantiate.visitVarInsn(Opcodes.ALOAD, 3);
                instantiate.visitMethodInsn(Opcodes.INVOKEVIRTUAL, name, "inject", LIFECYCLE_DESC, false);
                instantiate.visitInsn(Opcodes.POP);
            }
            if (postConstruct) {
                instantiate.visitVarInsn(Opcodes.ALOAD, 0);
                instantiate.visitVarInsn(Opcodes.ALOAD, 1);
                instantiate.visitVarInsn(Opcodes.ALOAD, 2);
                instantiate.visitVarInsn(Opcodes.ALOAD, 3);
                instantiate.visitMethodInsn(Opcodes.INVOKEVIRTUAL, name, "initialize", LIFECYCLE_DESC, false);
                instantiate.visitInsn(Opcodes.ARETURN);
            } else {
                instantiate.visitVarInsn(Opcodes.ALOAD, 3);
                instantiate.visitInsn(Opcodes.ARETURN);
            }
            instantiate.visitLabel(skip);
            instantiate.visitVarInsn(Opcodes.ALOAD, 3);
            instantiate.visitInsn(Opcodes.ARETURN);
        }
        instantiate.visitMaxs(0, 0);
        instantiate.visitEnd();

        if (inject) {
            lifecycleMethod(writer, name, beanType, definition, "inject", null, m -> !m.postConstruct() && !m.preDestroy());
        }
        if (postConstruct) {
            lifecycleMethod(writer, name, beanType, definition, "initialize", "postConstruct", InjectedMethodModel::postConstruct);
        }
        if (preDestroy) {
            lifecycleMethod(writer, name, beanType, definition, "dispose", "preDestroy", InjectedMethodModel::preDestroy);
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    /**
     * Generates the executable methods definition class of a definition, its {@code $Exec} companion.
     *
     * @param model      The class model
     * @param definition The definition, one of the model's, which must have executable methods
     * @return The class bytes
     */
    public static byte[] executableMethods(ClassModel model, BeanDefinitionModel definition) {
        if (definition.executableMethods().isEmpty()) {
            throw new IllegalArgumentException("The definition " + definition.definitionClassName() + " has no executable methods");
        }
        Type ownerType = ModelTypes.type(model.className());
        Type beanType = ModelTypes.type(definition.beanTypeName());
        String name = definition.definitionClassName().replace('.', '/') + EXEC_SUFFIX;
        ClassWriter writer = new GeneratedClassWriter();
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_SYNTHETIC, name,
            "L" + EXEC_SUPER + "<" + beanType.getDescriptor() + ">;", EXEC_SUPER, null);
        generatedAnnotation(writer, "");

        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitLdcInsn(ownerType);
        init.visitLdcInsn(definition.definitionClassName());
        init.visitMethodInsn(Opcodes.INVOKESTATIC, SUPPORT, "executableMethods", "(Ljava/lang/Class;Ljava/lang/String;)[L" + EXEC_METHOD_REFERENCE + ";", false);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, EXEC_SUPER, "<init>", "([L" + EXEC_METHOD_REFERENCE + ";)V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();

        List<ExecutableMethodModel> executables = definition.executableMethods();
        MethodVisitor dispatch = writer.visitMethod(Opcodes.ACC_PROTECTED, "dispatch", "(I" + OBJECT_DESC + "[" + OBJECT_DESC + ")" + OBJECT_DESC, null, null);
        dispatch.visitCode();
        Label[] labels = labels(executables.size());
        Label unknown = new Label();
        dispatch.visitVarInsn(Opcodes.ILOAD, 1);
        tableSwitch(dispatch, labels, unknown);
        for (int i = 0; i < executables.size(); i++) {
            MethodModel method = executables.get(i).method();
            dispatch.visitLabel(labels[i]);
            if (!method.isStatic()) {
                dispatch.visitVarInsn(Opcodes.ALOAD, 2);
                dispatch.visitTypeInsn(Opcodes.CHECKCAST, beanType.getInternalName());
            }
            for (int p = 0; p < method.parameters().size(); p++) {
                dispatch.visitVarInsn(Opcodes.ALOAD, 3);
                pushInt(dispatch, p);
                dispatch.visitInsn(Opcodes.AALOAD);
                convert(dispatch, method.parameters().get(p).typeName());
            }
            invoke(dispatch, method);
            box(dispatch, method.returnType().typeName());
            dispatch.visitInsn(Opcodes.ARETURN);
        }
        dispatch.visitLabel(unknown);
        unknownDispatch(dispatch, EXEC_SUPER);
        dispatch.visitMaxs(0, 0);
        dispatch.visitEnd();

        MethodVisitor target = writer.visitMethod(Opcodes.ACC_PROTECTED, "getTargetMethodByIndex", "(I)Ljava/lang/reflect/Method;", null, null);
        target.visitCode();
        Label[] targetLabels = labels(executables.size());
        Label unknownTarget = new Label();
        target.visitVarInsn(Opcodes.ILOAD, 1);
        tableSwitch(target, targetLabels, unknownTarget);
        for (int i = 0; i < executables.size(); i++) {
            target.visitLabel(targetLabels[i]);
            requiredMethod(target, executables.get(i).method());
            target.visitInsn(Opcodes.ARETURN);
        }
        target.visitLabel(unknownTarget);
        unknownDispatch(target, EXEC_SUPER);
        target.visitMaxs(0, 0);
        target.visitEnd();

        booleanMethod(writer, "requiresMethodProcessing", "()Z", executables.stream().anyMatch(ExecutableMethodModel::processOnStartup));
        writer.visitEnd();
        return writer.toByteArray();
    }

    /**
     * Generates the introspection class of a model.
     *
     * @param model The class model, which must have an introspection
     * @return The class bytes
     */
    public static byte[] introspection(ClassModel model) {
        IntrospectionModel introspection = model.introspection();
        if (introspection == null) {
            throw new IllegalArgumentException("The model of " + model.className() + " has no introspection");
        }
        Type beanType = ModelTypes.type(model.className());
        String name = introspection.introspectionClassName().replace('.', '/');
        ClassWriter writer = new GeneratedClassWriter();
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_SYNTHETIC, name,
            "L" + INTROSPECTION_SUPER + "<" + beanType.getDescriptor() + ">;", INTROSPECTION_SUPER, null);
        generatedAnnotation(writer, "io.micronaut.core.beans.BeanIntrospectionReference");
        writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, STATE_FIELD, STATE_DESC_I, null, null).visitEnd();

        MethodVisitor clinit = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        clinit.visitCode();
        clinit.visitLdcInsn(beanType);
        clinit.visitMethodInsn(Opcodes.INVOKESTATIC, SUPPORT, "introspectionState", "(Ljava/lang/Class;)" + STATE_DESC_I, false);
        clinit.visitFieldInsn(Opcodes.PUTSTATIC, name, STATE_FIELD, STATE_DESC_I);
        clinit.visitInsn(Opcodes.RETURN);
        clinit.visitMaxs(0, 0);
        clinit.visitEnd();

        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitLdcInsn(beanType);
        stateCall(init, name, STATE_DESC_I, INTROSPECTION_STATE, "annotationMetadata", "()L" + ANNOTATION_METADATA + ";");
        stateCall(init, name, STATE_DESC_I, INTROSPECTION_STATE, "constructorAnnotationMetadata", "()L" + ANNOTATION_METADATA + ";");
        stateCall(init, name, STATE_DESC_I, INTROSPECTION_STATE, "constructorArguments", "()[L" + ARGUMENT + ";");
        stateCall(init, name, STATE_DESC_I, INTROSPECTION_STATE, "propertyRefs", "()[L" + PROPERTY_REF + ";");
        init.visitInsn(Opcodes.ACONST_NULL);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, INTROSPECTION_SUPER, "<init>", "(Ljava/lang/Class;L" + ANNOTATION_METADATA + ";L"
            + ANNOTATION_METADATA + ";[L" + ARGUMENT + ";[L" + PROPERTY_REF + ";[L" + METHOD_REF + ";)V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();

        // The dispatch table: read, then write or the immutability exception, per property, as the writer numbers them
        List<Dispatch> dispatches = new ArrayList<>();
        for (PropertyModel property : introspection.properties()) {
            if (property.readMethod() != null) {
                dispatches.add(new Dispatch(Dispatch.Kind.READ, property, property.readMethod()));
            }
            if (property.writeMethod() != null) {
                dispatches.add(new Dispatch(Dispatch.Kind.WRITE, property, property.writeMethod()));
            } else if (property.readOnly()) {
                dispatches.add(new Dispatch(Dispatch.Kind.IMMUTABLE, property, null));
            }
        }

        MethodVisitor dispatchOne = writer.visitMethod(Opcodes.ACC_PROTECTED, "dispatchOne", "(I" + OBJECT_DESC + OBJECT_DESC + ")" + OBJECT_DESC, null, null);
        dispatchOne.visitCode();
        Label[] labels = labels(dispatches.size());
        Label unknown = new Label();
        dispatchOne.visitVarInsn(Opcodes.ILOAD, 1);
        tableSwitch(dispatchOne, labels, unknown);
        for (int i = 0; i < dispatches.size(); i++) {
            Dispatch dispatch = dispatches.get(i);
            dispatchOne.visitLabel(labels[i]);
            MethodModel method = dispatch.method();
            switch (dispatch.kind()) {
                case READ -> {
                    Objects.requireNonNull(method);
                    dispatchOne.visitVarInsn(Opcodes.ALOAD, 2);
                    dispatchOne.visitTypeInsn(Opcodes.CHECKCAST, beanType.getInternalName());
                    invoke(dispatchOne, method);
                    box(dispatchOne, method.returnType().typeName());
                    dispatchOne.visitInsn(Opcodes.ARETURN);
                }
                case WRITE -> {
                    Objects.requireNonNull(method);
                    dispatchOne.visitVarInsn(Opcodes.ALOAD, 2);
                    dispatchOne.visitTypeInsn(Opcodes.CHECKCAST, beanType.getInternalName());
                    dispatchOne.visitVarInsn(Opcodes.ALOAD, 3);
                    convert(dispatchOne, method.parameters().getFirst().typeName());
                    invoke(dispatchOne, method);
                    pop(dispatchOne, method.returnType().typeName());
                    dispatchOne.visitInsn(Opcodes.ACONST_NULL);
                    dispatchOne.visitInsn(Opcodes.ARETURN);
                }
                case IMMUTABLE -> {
                    dispatchOne.visitTypeInsn(Opcodes.NEW, "java/lang/UnsupportedOperationException");
                    dispatchOne.visitInsn(Opcodes.DUP);
                    dispatchOne.visitLdcInsn("Cannot mutate property [" + dispatch.property().name()
                        + "] that is not mutable via a setter method, field or constructor argument for type: " + model.className());
                    dispatchOne.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/UnsupportedOperationException", "<init>", "(Ljava/lang/String;)V", false);
                    dispatchOne.visitInsn(Opcodes.ATHROW);
                }
                default -> throw new IllegalStateException();
            }
        }
        dispatchOne.visitLabel(unknown);
        unknownDispatch(dispatchOne, INTROSPECTION_BASE);
        dispatchOne.visitMaxs(0, 0);
        dispatchOne.visitEnd();

        MethodVisitor target = writer.visitMethod(Opcodes.ACC_PROTECTED, "getTargetMethodByIndex", "(I)Ljava/lang/reflect/Method;", null, null);
        target.visitCode();
        Label[] targetLabels = labels(dispatches.size());
        Label unknownTarget = new Label();
        target.visitVarInsn(Opcodes.ILOAD, 1);
        tableSwitch(target, targetLabels, unknownTarget);
        for (int i = 0; i < dispatches.size(); i++) {
            Dispatch dispatch = dispatches.get(i);
            target.visitLabel(targetLabels[i]);
            MethodModel method = dispatch.method();
            if (method == null) {
                unknownDispatch(target, INTROSPECTION_BASE);
                continue;
            }
            requiredMethod(target, method);
            target.visitInsn(Opcodes.ARETURN);
        }
        target.visitLabel(unknownTarget);
        unknownDispatch(target, INTROSPECTION_BASE);
        target.visitMaxs(0, 0);
        target.visitEnd();

        indexMethods(writer, introspection);
        booleanMethod(writer, "hasConstructor", "()Z", true);
        List<ArgumentModel> constructorArguments = introspection.constructorArguments();
        if (constructorArguments.isEmpty()) {
            MethodVisitor instantiate = writer.visitMethod(Opcodes.ACC_PUBLIC, "instantiate", "()" + OBJECT_DESC, null, null);
            instantiate.visitCode();
            instantiate.visitTypeInsn(Opcodes.NEW, beanType.getInternalName());
            instantiate.visitInsn(Opcodes.DUP);
            instantiate.visitMethodInsn(Opcodes.INVOKESPECIAL, beanType.getInternalName(), "<init>", "()V", false);
            instantiate.visitInsn(Opcodes.ARETURN);
            instantiate.visitMaxs(0, 0);
            instantiate.visitEnd();
        }
        MethodVisitor internal = writer.visitMethod(Opcodes.ACC_PROTECTED, "instantiateInternal", "([" + OBJECT_DESC + ")" + OBJECT_DESC, null, null);
        internal.visitCode();
        internal.visitTypeInsn(Opcodes.NEW, beanType.getInternalName());
        internal.visitInsn(Opcodes.DUP);
        for (int i = 0; i < constructorArguments.size(); i++) {
            internal.visitVarInsn(Opcodes.ALOAD, 1);
            pushInt(internal, i);
            internal.visitInsn(Opcodes.AALOAD);
            convert(internal, constructorArguments.get(i).typeName());
        }
        internal.visitMethodInsn(Opcodes.INVOKESPECIAL, beanType.getInternalName(), "<init>", descriptor(constructorArguments, "void"), false);
        internal.visitInsn(Opcodes.ARETURN);
        internal.visitMaxs(0, 0);
        internal.visitEnd();
        booleanMethod(writer, "isBuildable", "()Z", true);
        booleanMethod(writer, "hasBuilder", "()Z", false);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void indexMethods(ClassWriter writer, IntrospectionModel introspection) {
        if (introspection.indexes().isEmpty()) {
            return;
        }
        // The same two maps the writer keeps: property indexes by annotation, and by annotation and value
        Map<String, List<Integer>> byAnnotation = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> byValue = new LinkedHashMap<>();
        Map<String, Integer> nullValue = new LinkedHashMap<>();
        for (PropertyIndexModel index : introspection.indexes()) {
            List<Integer> properties = byAnnotation.computeIfAbsent(index.annotationName(), k -> new ArrayList<>());
            if (!properties.contains(index.propertyIndex())) {
                properties.add(index.propertyIndex());
            }
            if (index.value() == null) {
                nullValue.put(index.annotationName(), index.propertyIndex());
            } else {
                byValue.computeIfAbsent(index.annotationName(), k -> new LinkedHashMap<>()).put(index.value(), index.propertyIndex());
            }
        }
        String beanProperty = "Lio/micronaut/core/beans/BeanProperty;";
        MethodVisitor find = writer.visitMethod(Opcodes.ACC_PUBLIC, "findIndexedProperty", "(Ljava/lang/Class;Ljava/lang/String;)" + beanProperty, null, null);
        find.visitCode();
        for (String annotationName : byAnnotation.keySet()) {
            Label next = new Label();
            annotationNameCheck(find, annotationName, next);
            Label valued = new Label();
            find.visitVarInsn(Opcodes.ALOAD, 2);
            find.visitJumpInsn(Opcodes.IFNONNULL, valued);
            Integer unvalued = nullValue.get(annotationName);
            if (unvalued != null) {
                propertyByIndex(find, unvalued);
            } else {
                find.visitInsn(Opcodes.ACONST_NULL);
            }
            find.visitInsn(Opcodes.ARETURN);
            find.visitLabel(valued);
            for (Map.Entry<String, Integer> entry : byValue.getOrDefault(annotationName, Map.of()).entrySet()) {
                Label other = new Label();
                find.visitVarInsn(Opcodes.ALOAD, 2);
                find.visitLdcInsn(entry.getKey());
                find.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
                find.visitJumpInsn(Opcodes.IFEQ, other);
                propertyByIndex(find, entry.getValue());
                find.visitInsn(Opcodes.ARETURN);
                find.visitLabel(other);
            }
            find.visitInsn(Opcodes.ACONST_NULL);
            find.visitInsn(Opcodes.ARETURN);
            find.visitLabel(next);
        }
        find.visitInsn(Opcodes.ACONST_NULL);
        find.visitInsn(Opcodes.ARETURN);
        find.visitMaxs(0, 0);
        find.visitEnd();

        MethodVisitor all = writer.visitMethod(Opcodes.ACC_PUBLIC, "getIndexedProperties", "(Ljava/lang/Class;)Ljava/util/Collection;", null, null);
        all.visitCode();
        for (Map.Entry<String, List<Integer>> entry : byAnnotation.entrySet()) {
            Label next = new Label();
            annotationNameCheck(all, entry.getKey(), next);
            all.visitVarInsn(Opcodes.ALOAD, 0);
            pushInt(all, entry.getValue().size());
            all.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_INT);
            for (int i = 0; i < entry.getValue().size(); i++) {
                all.visitInsn(Opcodes.DUP);
                pushInt(all, i);
                pushInt(all, entry.getValue().get(i));
                all.visitInsn(Opcodes.IASTORE);
            }
            all.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "io/micronaut/inject/beans/AbstractInitializableBeanIntrospection", "getBeanPropertiesIndexedSubset",
                "([I)Ljava/util/Collection;", false);
            all.visitInsn(Opcodes.ARETURN);
            all.visitLabel(next);
        }
        all.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/List", "of", "()Ljava/util/List;", true);
        all.visitInsn(Opcodes.ARETURN);
        all.visitMaxs(0, 0);
        all.visitEnd();
    }

    private static void annotationNameCheck(MethodVisitor mv, String annotationName, Label next) {
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getName", "()Ljava/lang/String;", false);
        mv.visitLdcInsn(annotationName);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
        mv.visitJumpInsn(Opcodes.IFEQ, next);
    }

    private static void propertyByIndex(MethodVisitor mv, int propertyIndex) {
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        pushInt(mv, propertyIndex);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "io/micronaut/inject/beans/AbstractInitializableBeanIntrospection", "getPropertyByIndex",
            "(I)Lio/micronaut/core/beans/BeanProperty;", false);
    }

    private static void lifecycleMethod(ClassWriter writer, String name, Type beanType, BeanDefinitionModel definition,
                                        String methodName, @Nullable String superHook, java.util.function.Predicate<InjectedMethodModel> selector) {
        MethodVisitor mv = writer.visitMethod(Opcodes.ACC_PUBLIC, methodName, LIFECYCLE_DESC, null, null);
        mv.visitCode();
        if (superHook != null) {
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitVarInsn(Opcodes.ALOAD, 2);
            mv.visitVarInsn(Opcodes.ALOAD, 3);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, name, superHook, LIFECYCLE_DESC, false);
        } else {
            mv.visitVarInsn(Opcodes.ALOAD, 3);
        }
        mv.visitTypeInsn(Opcodes.CHECKCAST, beanType.getInternalName());
        mv.visitVarInsn(Opcodes.ASTORE, 4);
        List<InjectedMethodModel> methods = definition.methods();
        for (int methodIndex = 0; methodIndex < methods.size(); methodIndex++) {
            InjectedMethodModel method = methods.get(methodIndex);
            if (!selector.test(method)) {
                continue;
            }
            List<ArgumentModel> parameters = method.method().parameters();
            if (method.required() || parameters.isEmpty()) {
                mv.visitVarInsn(Opcodes.ALOAD, 4);
                for (int p = 0; p < parameters.size(); p++) {
                    methodArgument(mv, name, method.injectionPoints().get(p), methodIndex, p);
                    convert(mv, parameters.get(p).typeName());
                }
                invoke(mv, method.method());
                pop(mv, method.method().returnType().typeName());
            } else {
                // An injection that is not required is invoked only when every argument resolved
                pushInt(mv, parameters.size());
                mv.visitTypeInsn(Opcodes.ANEWARRAY, OBJECT);
                for (int p = 0; p < parameters.size(); p++) {
                    mv.visitInsn(Opcodes.DUP);
                    pushInt(mv, p);
                    methodArgument(mv, name, method.injectionPoints().get(p), methodIndex, p);
                    mv.visitInsn(Opcodes.AASTORE);
                }
                mv.visitVarInsn(Opcodes.ASTORE, 5);
                Label skip = new Label();
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                pushInt(mv, methodIndex);
                mv.visitVarInsn(Opcodes.ALOAD, 5);
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, name, "isMethodResolved", "(I[" + OBJECT_DESC + ")Z", false);
                mv.visitJumpInsn(Opcodes.IFEQ, skip);
                mv.visitVarInsn(Opcodes.ALOAD, 4);
                for (int p = 0; p < parameters.size(); p++) {
                    mv.visitVarInsn(Opcodes.ALOAD, 5);
                    pushInt(mv, p);
                    mv.visitInsn(Opcodes.AALOAD);
                    convert(mv, parameters.get(p).typeName());
                }
                invoke(mv, method.method());
                pop(mv, method.method().returnType().typeName());
                mv.visitLabel(skip);
            }
        }
        mv.visitVarInsn(Opcodes.ALOAD, 4);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void constructorArgument(MethodVisitor mv, String name, InjectionPointModel point, int index) {
        switch (point.kind()) {
            case BEAN_CONTEXT -> mv.visitVarInsn(Opcodes.ALOAD, 2);
            case RESOLUTION_CONTEXT -> mv.visitVarInsn(Opcodes.ALOAD, 1);
            case BEAN -> {
                contextArguments(mv);
                pushInt(mv, index);
                stateIndexed(mv, name, "constructorQualifier", index, -1, "L" + QUALIFIER + ";");
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, name, "getBeanForConstructorArgument", CONTEXT_PARAMS + "IL" + QUALIFIER + ";)" + OBJECT_DESC, false);
            }
            case BEANS, OPTIONAL_BEAN, MAP_OF_BEANS, STREAM_OF_BEANS, BEAN_REGISTRATION, BEAN_REGISTRATIONS -> {
                contextArguments(mv);
                pushInt(mv, index);
                stateIndexed(mv, name, "constructorGenericType", index, -1, "L" + ARGUMENT + ";");
                stateIndexed(mv, name, "constructorQualifier", index, -1, "L" + QUALIFIER + ";");
                String[] helper = genericHelper(point.kind(), "ConstructorArgument");
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, name, helper[0], CONTEXT_PARAMS + "IL" + ARGUMENT + ";L" + QUALIFIER + ";)" + helper[1], false);
            }
            case VALUE -> {
                contextArguments(mv);
                pushInt(mv, index);
                mv.visitLdcInsn(point.value());
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, name, "getPropertyPlaceholderValueForConstructorArgument", CONTEXT_PARAMS + "ILjava/lang/String;)" + OBJECT_DESC, false);
            }
            case PROPERTY -> {
                contextArguments(mv);
                pushInt(mv, index);
                mv.visitLdcInsn(point.propertyPath());
                mv.visitInsn(Opcodes.ACONST_NULL);
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, name, "getPropertyValueForConstructorArgument", CONTEXT_PARAMS + "ILjava/lang/String;Ljava/lang/String;)" + OBJECT_DESC, false);
            }
            default -> throw new IllegalStateException("Unsupported constructor injection point: " + point.kind());
        }
    }

    private static void methodArgument(MethodVisitor mv, String name, InjectionPointModel point, int methodIndex, int index) {
        switch (point.kind()) {
            case BEAN_CONTEXT -> mv.visitVarInsn(Opcodes.ALOAD, 2);
            case RESOLUTION_CONTEXT -> mv.visitVarInsn(Opcodes.ALOAD, 1);
            case BEAN -> {
                contextArguments(mv);
                pushInt(mv, methodIndex);
                pushInt(mv, index);
                stateIndexed(mv, name, "methodQualifier", methodIndex, index, "L" + QUALIFIER + ";");
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, name, "getBeanForMethodArgument", CONTEXT_PARAMS + "IIL" + QUALIFIER + ";)" + OBJECT_DESC, false);
            }
            case BEANS, OPTIONAL_BEAN, MAP_OF_BEANS, STREAM_OF_BEANS, BEAN_REGISTRATION, BEAN_REGISTRATIONS -> {
                contextArguments(mv);
                pushInt(mv, methodIndex);
                pushInt(mv, index);
                stateIndexed(mv, name, "methodGenericType", methodIndex, index, "L" + ARGUMENT + ";");
                stateIndexed(mv, name, "methodQualifier", methodIndex, index, "L" + QUALIFIER + ";");
                String[] helper = genericHelper(point.kind(), "MethodArgument");
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, name, helper[0], CONTEXT_PARAMS + "IIL" + ARGUMENT + ";L" + QUALIFIER + ";)" + helper[1], false);
            }
            case VALUE -> {
                contextArguments(mv);
                pushInt(mv, methodIndex);
                pushInt(mv, index);
                mv.visitLdcInsn(point.value());
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, name, "getPropertyPlaceholderValueForMethodArgument", CONTEXT_PARAMS + "IILjava/lang/String;)" + OBJECT_DESC, false);
            }
            case PROPERTY -> {
                contextArguments(mv);
                pushInt(mv, methodIndex);
                pushInt(mv, index);
                mv.visitLdcInsn(point.propertyPath());
                mv.visitInsn(Opcodes.ACONST_NULL);
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, name, "getPropertyValueForMethodArgument", CONTEXT_PARAMS + "IILjava/lang/String;Ljava/lang/String;)" + OBJECT_DESC, false);
            }
            default -> throw new IllegalStateException("Unsupported method injection point: " + point.kind());
        }
    }

    /**
     * Pushes the receiver and the two context arguments of a resolution helper of the definition.
     */
    /**
     * The helper of the definition base class resolving a collection-like injection point, and its return descriptor.
     */
    private static String[] genericHelper(InjectionPointModel.Kind kind, String target) {
        return switch (kind) {
            case BEANS -> new String[]{"getBeansOfTypeFor" + target + "Object", OBJECT_DESC};
            case OPTIONAL_BEAN -> new String[]{"findBeanFor" + target + "Object", OBJECT_DESC};
            case MAP_OF_BEANS -> new String[]{"getMapOfTypeFor" + target + "Object", OBJECT_DESC};
            case STREAM_OF_BEANS -> new String[]{"getStreamOfTypeFor" + target, "Ljava/util/stream/Stream;"};
            case BEAN_REGISTRATION -> new String[]{"getBeanRegistrationFor" + target, "Lio/micronaut/context/BeanRegistration;"};
            case BEAN_REGISTRATIONS -> new String[]{"getBeanRegistrationsFor" + target + "Object", OBJECT_DESC};
            default -> throw new IllegalStateException(kind.toString());
        };
    }

    private static void contextArguments(MethodVisitor mv) {
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitVarInsn(Opcodes.ALOAD, 2);
    }

    private static void stateIndexed(MethodVisitor mv, String owner, String method, int first, int second, String returnDescriptor) {
        mv.visitFieldInsn(Opcodes.GETSTATIC, owner, STATE_FIELD, STATE_DESC_D);
        pushInt(mv, first);
        if (second >= 0) {
            pushInt(mv, second);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, DEFINITION_STATE, method, "(II)" + returnDescriptor, false);
        } else {
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, DEFINITION_STATE, method, "(I)" + returnDescriptor, false);
        }
    }

    private static void stateCall(MethodVisitor mv, String owner, String stateDescriptor, String stateType, String method, String descriptor) {
        mv.visitFieldInsn(Opcodes.GETSTATIC, owner, STATE_FIELD, stateDescriptor);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, stateType, method, descriptor, false);
    }

    private static void invoke(MethodVisitor mv, MethodModel method) {
        String owner = ModelTypes.type(method.declaringType()).getInternalName();
        mv.visitMethodInsn(method.isStatic() ? Opcodes.INVOKESTATIC : Opcodes.INVOKEVIRTUAL, owner, method.name(),
            descriptor(method.parameters(), method.returnType().typeName()), false);
    }

    private static String descriptor(List<ArgumentModel> parameters, String returnTypeName) {
        Type[] types = new Type[parameters.size()];
        for (int i = 0; i < types.length; i++) {
            types[i] = ModelTypes.type(parameters.get(i).typeName());
        }
        return Type.getMethodDescriptor(ModelTypes.type(returnTypeName), types);
    }

    private static void convert(MethodVisitor mv, String typeName) {
        Type type = ModelTypes.type(typeName);
        switch (type.getSort()) {
            case Type.OBJECT -> {
                if (!type.getInternalName().equals(OBJECT)) {
                    mv.visitTypeInsn(Opcodes.CHECKCAST, type.getInternalName());
                }
            }
            case Type.ARRAY -> mv.visitTypeInsn(Opcodes.CHECKCAST, type.getDescriptor());
            default -> {
                String boxed = boxedName(type);
                mv.visitTypeInsn(Opcodes.CHECKCAST, boxed);
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, boxed, type.getClassName() + "Value", "()" + type.getDescriptor(), false);
            }
        }
    }

    private static void box(MethodVisitor mv, String typeName) {
        Type type = ModelTypes.type(typeName);
        if (type.getSort() == Type.VOID) {
            mv.visitInsn(Opcodes.ACONST_NULL);
        } else if (type.getSort() != Type.OBJECT && type.getSort() != Type.ARRAY) {
            String boxed = boxedName(type);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, boxed, "valueOf", "(" + type.getDescriptor() + ")L" + boxed + ";", false);
        }
    }

    private static void pop(MethodVisitor mv, String returnTypeName) {
        Type type = ModelTypes.type(returnTypeName);
        if (type.getSort() == Type.VOID) {
            return;
        }
        mv.visitInsn(type.getSize() == 2 ? Opcodes.POP2 : Opcodes.POP);
    }

    private static String boxedName(Type primitive) {
        return switch (primitive.getSort()) {
            case Type.BOOLEAN -> "java/lang/Boolean";
            case Type.BYTE -> "java/lang/Byte";
            case Type.SHORT -> "java/lang/Short";
            case Type.INT -> "java/lang/Integer";
            case Type.LONG -> "java/lang/Long";
            case Type.FLOAT -> "java/lang/Float";
            case Type.DOUBLE -> "java/lang/Double";
            case Type.CHAR -> "java/lang/Character";
            default -> throw new IllegalArgumentException("Not a primitive: " + primitive);
        };
    }

    private static void classConstant(MethodVisitor mv, String typeName) {
        Type type = ModelTypes.type(typeName);
        if (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY) {
            mv.visitLdcInsn(type);
        } else {
            mv.visitFieldInsn(Opcodes.GETSTATIC, boxedName(type), "TYPE", "Ljava/lang/Class;");
        }
    }

    private static void pushInt(MethodVisitor mv, int value) {
        if (value >= -1 && value <= 5) {
            mv.visitInsn(Opcodes.ICONST_0 + value);
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.BIPUSH, value);
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.SIPUSH, value);
        } else {
            mv.visitLdcInsn(value);
        }
    }

    private static Label[] labels(int count) {
        Label[] labels = new Label[count];
        for (int i = 0; i < count; i++) {
            labels[i] = new Label();
        }
        return labels;
    }

    private static void tableSwitch(MethodVisitor mv, Label[] labels, Label unknown) {
        if (labels.length == 0) {
            mv.visitInsn(Opcodes.POP);
            mv.visitJumpInsn(Opcodes.GOTO, unknown);
        } else {
            mv.visitTableSwitchInsn(0, labels.length - 1, unknown, labels);
        }
    }

    private static void unknownDispatch(MethodVisitor mv, String owner) {
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ILOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, "unknownDispatchAtIndexException", "(I)Ljava/lang/RuntimeException;", false);
        mv.visitInsn(Opcodes.ATHROW);
    }

    private static void requiredMethod(MethodVisitor mv, MethodModel method) {
        mv.visitLdcInsn(ModelTypes.type(method.declaringType()));
        mv.visitLdcInsn(method.name());
        pushInt(mv, method.parameters().size());
        mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Class");
        for (int p = 0; p < method.parameters().size(); p++) {
            mv.visitInsn(Opcodes.DUP);
            pushInt(mv, p);
            classConstant(mv, method.parameters().get(p).typeName());
            mv.visitInsn(Opcodes.AASTORE);
        }
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "io/micronaut/core/reflect/ReflectionUtils", "getRequiredMethod",
            "(Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;", false);
    }

    private static void booleanMethod(ClassWriter writer, String name, String descriptor, boolean value) {
        MethodVisitor mv = writer.visitMethod(Opcodes.ACC_PUBLIC, name, descriptor, null, null);
        mv.visitCode();
        mv.visitInsn(value ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void generatedAnnotation(ClassWriter writer, String service) {
        AnnotationVisitor annotation = writer.visitAnnotation(GENERATED, true);
        annotation.visit("service", service);
        annotation.visitEnd();
    }

    private static boolean hasStereotype(ClassModel model, BeanDefinitionModel definition, String annotationName) {
        return has(model.annotationMetadata(), annotationName)
            || (definition.annotationMetadata() != null && has(definition.annotationMetadata(), annotationName))
            || (definition.rootAnnotationMetadata() != null && has(definition.rootAnnotationMetadata(), annotationName));
    }

    private static boolean has(AnnotationMetadataModel metadata, String annotationName) {
        return metadata.allStereotypes().containsKey(annotationName) || metadata.allAnnotations().containsKey(annotationName);
    }

    private record Dispatch(Kind kind, PropertyModel property, @Nullable MethodModel method) {
        enum Kind { READ, WRITE, IMMUTABLE }
    }

    /**
     * Frames are computed; the generated methods never merge different reference types at a join, so the common
     * super class is never needed, and looking it up must not load application classes through this module's loader.
     */
    private static final class GeneratedClassWriter extends ClassWriter {
        GeneratedClassWriter() {
            super(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        }

        @Override
        protected String getCommonSuperClass(String type1, String type2) {
            return OBJECT;
        }
    }
}
