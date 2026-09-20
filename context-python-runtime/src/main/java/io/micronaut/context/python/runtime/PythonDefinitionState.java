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
package io.micronaut.context.python.runtime;

import io.micronaut.context.AbstractExecutableMethodsDefinition;
import io.micronaut.context.AbstractInitializableBeanDefinition;
import io.micronaut.context.Qualifier;
import io.micronaut.context.python.runtime.model.ArgumentModel;
import io.micronaut.context.python.runtime.model.BeanDefinitionModel;
import io.micronaut.context.python.runtime.model.ClassModel;
import io.micronaut.context.python.runtime.model.ExecutableMethodModel;
import io.micronaut.context.python.runtime.model.InjectedMethodModel;
import io.micronaut.context.python.runtime.model.InjectionPointModel;
import io.micronaut.context.python.runtime.model.PrecalculatedInfoModel;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * The immutable, context-independent objects a generated definition class initializes itself with: what the
 * build-time writer would have created in the static initializer of the definition. Materialized once per class.
 * Failures to materialize the constructor and injection references are recorded, like the writer's {@code $FAILURE},
 * and reported when the definition is loaded into a context.
 *
 * @since 5.3.0
 */
@Internal
@UsedByGeneratedCode
public final class PythonDefinitionState {

    private final AnnotationMetadata annotationMetadata;
    private final AbstractInitializableBeanDefinition.PrecalculatedInfo info;
    private final AbstractInitializableBeanDefinition.@Nullable MethodReference constructor;
    private final AbstractInitializableBeanDefinition.MethodReference @Nullable [] methodInjection;
    private final @Nullable Map<String, Argument<?>[]> typeArguments;
    private final @Nullable Throwable failure;
    private final Set<Class<?>> exposedTypes;
    private final AbstractExecutableMethodsDefinition.MethodReference @Nullable [] executableMethods;
    private final int[] processingIndexes;
    private final Injection injection;

    private PythonDefinitionState(AnnotationMetadata annotationMetadata,
                                  AbstractInitializableBeanDefinition.PrecalculatedInfo info,
                                  AbstractInitializableBeanDefinition.@Nullable MethodReference constructor,
                                  AbstractInitializableBeanDefinition.MethodReference @Nullable [] methodInjection,
                                  @Nullable Map<String, Argument<?>[]> typeArguments,
                                  @Nullable Throwable failure,
                                  Set<Class<?>> exposedTypes,
                                  AbstractExecutableMethodsDefinition.MethodReference @Nullable [] executableMethods,
                                  int[] processingIndexes,
                                  Injection injection) {
        this.annotationMetadata = annotationMetadata;
        this.info = info;
        this.constructor = constructor;
        this.methodInjection = methodInjection;
        this.typeArguments = typeArguments;
        this.failure = failure;
        this.exposedTypes = exposedTypes;
        this.executableMethods = executableMethods;
        this.processingIndexes = processingIndexes;
        this.injection = injection;
    }

    /**
     * Materializes the state of a definition.
     *
     * @param beanType The bean type
     * @param model    The class model, which must have a bean definition
     * @return The state
     */
    static PythonDefinitionState of(Class<?> beanType, ClassModel model) {
        BeanDefinitionModel definition = model.beanDefinition();
        if (definition == null) {
            throw new IllegalArgumentException("The model of " + beanType.getName() + " has no bean definition");
        }
        ModelMaterializer materializer = new ModelMaterializer(beanType.getClassLoader());
        AnnotationMetadata annotationMetadata = materializer.annotationMetadata(model.annotationMetadata());
        PrecalculatedInfoModel infoModel = definition.info();
        List<ExecutableMethodModel> executables = definition.executableMethods();
        int[] processingIndexes = IntStream.range(0, executables.size()).filter(i -> executables.get(i).processOnStartup()).toArray();
        AbstractInitializableBeanDefinition.PrecalculatedInfo info = new AbstractInitializableBeanDefinition.PrecalculatedInfo(
            Optional.ofNullable(infoModel.scope()), infoModel.isAbstract(), infoModel.isIterable(), infoModel.isSingleton(),
            infoModel.isPrimary(), infoModel.isConfigurationProperties(), infoModel.isContainerType(), processingIndexes.length > 0, false);
        Set<Class<?>> exposedTypes = new LinkedHashSet<>();
        try {
            for (String exposedType : definition.exposedTypes()) {
                exposedTypes.add(materializer.resolve(exposedType));
            }
        } catch (ClassNotFoundException | LinkageError e) {
            // Like the writer's static initializer: an exposed type that cannot be loaded leaves the set empty
            exposedTypes = new LinkedHashSet<>();
        }
        exposedTypes = Collections.unmodifiableSet(exposedTypes);
        try {
            List<ArgumentModel> parameters = definition.constructor().parameters();
            Argument<?>[] constructorArguments = parameters.isEmpty() ? null : materializer.arguments(parameters);
            AbstractInitializableBeanDefinition.MethodReference constructor = new AbstractInitializableBeanDefinition.MethodReference(
                beanType, "<init>", constructorArguments, materializer.annotationMetadata(definition.constructor().annotationMetadata()), false, false);
            Qualifier<?>[] constructorQualifiers = new Qualifier[parameters.size()];
            Argument<?>[] constructorGenericTypes = new Argument[parameters.size()];
            resolveInjection(materializer, beanType, definition.constructor().injectionPoints(), constructorArguments, constructorQualifiers, constructorGenericTypes);
            List<InjectedMethodModel> methods = definition.methods();
            AbstractInitializableBeanDefinition.MethodReference[] methodInjection = new AbstractInitializableBeanDefinition.MethodReference[methods.size()];
            Qualifier<?>[][] methodQualifiers = new Qualifier[methods.size()][];
            Argument<?>[][] methodGenericTypes = new Argument[methods.size()][];
            for (int i = 0; i < methods.size(); i++) {
                InjectedMethodModel method = methods.get(i);
                List<ArgumentModel> methodParameters = method.method().parameters();
                Argument<?>[] arguments = methodParameters.isEmpty() ? null : materializer.arguments(methodParameters);
                methodInjection[i] = new AbstractInitializableBeanDefinition.MethodReference(
                    materializer.resolve(method.method().declaringType()), method.method().name(), arguments,
                    materializer.annotationMetadata(method.annotationMetadata()), method.postConstruct(), method.preDestroy());
                methodQualifiers[i] = new Qualifier[methodParameters.size()];
                methodGenericTypes[i] = new Argument[methodParameters.size()];
                resolveInjection(materializer, beanType, method.injectionPoints(), arguments, methodQualifiers[i], methodGenericTypes[i]);
            }
            AbstractExecutableMethodsDefinition.MethodReference[] executableMethods = new AbstractExecutableMethodsDefinition.MethodReference[executables.size()];
            for (int i = 0; i < executables.size(); i++) {
                ExecutableMethodModel executable = executables.get(i);
                AnnotationMetadata methodMetadata = materializer.annotationMetadata(executable.annotationMetadata());
                if (executable.hierarchy()) {
                    // The writer's hierarchy: the bean's metadata as the root, the method's declared metadata on top
                    methodMetadata = new AnnotationMetadataHierarchy(annotationMetadata, methodMetadata);
                }
                List<ArgumentModel> methodParameters = executable.method().parameters();
                executableMethods[i] = new AbstractExecutableMethodsDefinition.MethodReference(
                    materializer.resolve(executable.method().declaringType()), methodMetadata, executable.method().name(),
                    materializer.argument(executable.returnArgument()), materializer.arguments(methodParameters),
                    executable.isAbstract(), false);
            }
            Map<String, Argument<?>[]> typeArguments = null;
            if (!definition.typeArguments().isEmpty()) {
                typeArguments = new LinkedHashMap<>();
                for (Map.Entry<String, List<ArgumentModel>> entry : definition.typeArguments().entrySet()) {
                    typeArguments.put(entry.getKey(), materializer.arguments(entry.getValue()));
                }
            }
            return new PythonDefinitionState(annotationMetadata, info, constructor, methods.isEmpty() ? null : methodInjection, typeArguments, null,
                exposedTypes, executables.isEmpty() ? null : executableMethods, processingIndexes,
                new Injection(constructorQualifiers, constructorGenericTypes, methodQualifiers, methodGenericTypes));
        } catch (ClassNotFoundException | LinkageError | RuntimeException e) {
            // Like the writer's static initializer: the definition still constructs, and reports the failure when loaded
            return new PythonDefinitionState(annotationMetadata, info, null, null, null, e,
                exposedTypes, null, processingIndexes, Injection.NONE);
        }
    }

    private static void resolveInjection(ModelMaterializer materializer,
                                         Class<?> beanType,
                                         List<InjectionPointModel> injectionPoints,
                                         Argument<?> @Nullable [] arguments,
                                         Qualifier<?>[] qualifiers,
                                         Argument<?>[] genericTypes) throws ClassNotFoundException {
        for (int i = 0; i < injectionPoints.size(); i++) {
            InjectionPointModel point = injectionPoints.get(i);
            Argument<?> argument = arguments == null ? materializer.argument(point.argument()) : arguments[i];
            qualifiers[i] = PythonQualifiers.qualifier(argument, beanType.getClassLoader());
            String beanTypeName = point.beanTypeName();
            if (beanTypeName != null) {
                genericTypes[i] = Argument.of(materializer.resolve(beanTypeName));
            }
        }
    }

    /**
     * @return The bean annotation metadata
     */
    public AnnotationMetadata annotationMetadata() {
        return annotationMetadata;
    }

    /**
     * @return The precalculated info
     */
    public AbstractInitializableBeanDefinition.PrecalculatedInfo info() {
        return info;
    }

    /**
     * @return The constructor reference, or null when materialization failed
     */
    public AbstractInitializableBeanDefinition.@Nullable MethodOrFieldReference constructor() {
        return constructor;
    }

    /**
     * @return The injected and lifecycle method references, or null when there are none
     */
    public AbstractInitializableBeanDefinition.MethodReference @Nullable [] methodInjection() {
        return methodInjection;
    }

    /**
     * @return The type arguments, or null when there are none
     */
    public @Nullable Map<String, Argument<?>[]> typeArguments() {
        return typeArguments;
    }

    /**
     * @return The materialization failure, if any
     */
    public @Nullable Throwable failure() {
        return failure;
    }

    /**
     * @return The executable method references, or null when there are none or materialization failed
     */
    public AbstractExecutableMethodsDefinition.MethodReference @Nullable [] executableMethods() {
        return executableMethods;
    }

    /**
     * @return The indexes of the executable methods processed on startup
     */
    public int[] processingIndexes() {
        return processingIndexes;
    }

    /**
     * @return The exposed types
     */
    public Set<Class<?>> exposedTypes() {
        return exposedTypes;
    }

    /**
     * @param index The constructor argument index
     * @return The qualifier of the argument, or null
     */
    public @Nullable Qualifier<?> constructorQualifier(int index) {
        return injection.constructorQualifiers()[index];
    }

    /**
     * @param index The constructor argument index
     * @return The bean type argument of a collection, optional or map argument
     */
    public Argument<?> constructorGenericType(int index) {
        return injection.constructorGenericTypes()[index];
    }

    /**
     * @param methodIndex   The method index
     * @param argumentIndex The argument index
     * @return The qualifier of the argument, or null
     */
    public @Nullable Qualifier<?> methodQualifier(int methodIndex, int argumentIndex) {
        return injection.methodQualifiers()[methodIndex][argumentIndex];
    }

    /**
     * @param methodIndex   The method index
     * @param argumentIndex The argument index
     * @return The bean type argument of a collection, optional or map argument
     */
    public Argument<?> methodGenericType(int methodIndex, int argumentIndex) {
        return injection.methodGenericTypes()[methodIndex][argumentIndex];
    }

    /**
     * The qualifiers and collection element types of the injected arguments.
     *
     * @param constructorQualifiers   The qualifier of each constructor argument, or null
     * @param constructorGenericTypes The element type of each collection-like constructor argument, or null
     * @param methodQualifiers        The qualifier of each argument of each injected method, or null
     * @param methodGenericTypes      The element type of each collection-like method argument, or null
     */
    private record Injection(Qualifier<?>[] constructorQualifiers, Argument<?>[] constructorGenericTypes,
                             Qualifier<?>[][] methodQualifiers, Argument<?>[][] methodGenericTypes) {
        static final Injection NONE = new Injection(new Qualifier[0], new Argument[0], new Qualifier[0][], new Argument[0][]);
    }
}
