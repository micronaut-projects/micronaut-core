/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.context;

import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySourcePropertyResolver;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.GenericPlaceholder;
import io.micronaut.core.type.ReturnType;
import io.micronaut.core.type.UnsafeExecutable;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.ImmutableStringIntMap;
import io.micronaut.core.util.ObjectUtils;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.ExecutableMethodsDefinition;
import io.micronaut.inject.annotation.AbstractEnvironmentAnnotationMetadata;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.annotation.EvaluatedAnnotationMetadata;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Abstract base class for {@link ExecutableMethodsDefinition}.
 *
 * @param <T> The type
 * @author Denis Stepanov
 * @since 3.0
 */
@Internal
public abstract class AbstractExecutableMethodsDefinition<T> implements ExecutableMethodsDefinition<T>, EnvironmentConfigurable, BeanContextConfigurable {

    private final MethodReference[] methodsReferences;
    /**
     * The generated definition invoking the methods: this one, or the one a copy is made from.
     */
    private final AbstractExecutableMethodsDefinition<T> dispatcher;
    private final DispatchedExecutableMethod<T, ?>[] executableMethods;
    @Nullable
    private Environment environment;
    @Nullable
    private BeanContext beanContext;
    @Nullable
    private List<DispatchedExecutableMethod<T, ?>> executableMethodsList;
    @Nullable
    private List<ExecutableMethod<T, ?>> postConstructMethodsList;
    @Nullable
    private List<ExecutableMethod<T, ?>> preDestroyMethodsList;

    protected AbstractExecutableMethodsDefinition(MethodReference[] methodsReferences) {
        this.methodsReferences = methodsReferences;
        this.dispatcher = this;
        this.executableMethods = new DispatchedExecutableMethod[methodsReferences.length];
    }

    private AbstractExecutableMethodsDefinition(AbstractExecutableMethodsDefinition<T> dispatcher) {
        this.methodsReferences = dispatcher.methodsReferences;
        this.dispatcher = dispatcher;
        this.executableMethods = new DispatchedExecutableMethod[methodsReferences.length];
    }

    /**
     * A copy with its own methods, invoked by this definition. A generated bean definition keeps its methods
     * definition in a static field, shared by every context that loads the bean definition, so each loaded bean
     * definition takes a copy: the methods, their metadata and the values they keep then belong to one context, and
     * are dropped with it.
     *
     * @return The copy
     */
    AbstractExecutableMethodsDefinition<T> copy() {
        return new Copy<>(dispatcher);
    }

    @Override
    public void configure(Environment environment) {
        this.environment = environment;
        for (DispatchedExecutableMethod<T, ?> executableMethod : executableMethods) {
            if (executableMethod != null) {
                executableMethod.configure(environment);
            }
        }
    }

    @Override
    public void configure(BeanContext beanContext) {
        this.beanContext = beanContext;
        for (DispatchedExecutableMethod<T, ?> executableMethod : executableMethods) {
            if (executableMethod != null) {
                executableMethod.configure(beanContext);
            }
        }
    }

    @Override
    public List<ExecutableMethod<T, ?>> getExecutableMethods() {
        if (executableMethodsList == null) {
            // Initialize the collection
            for (int i = 0, methodsReferencesLength = methodsReferences.length; i < methodsReferencesLength; i++) {
                getExecutableMethodByIndex(i);
            }
            int[] postConstructIndexes = getPostConstructMethodIndexes();
            int[] preDestroyIndexes = getPreDestroyMethodIndexes();
            if (postConstructIndexes == null && preDestroyIndexes == null) {
                executableMethodsList = Arrays.asList(executableMethods);
            } else {
                // Lifecycle callbacks are dispatched through this definition so that lifecycle interceptors can
                // see and invoke them, but they are not executable methods of the bean: processors and adapters
                // must not observe them.
                List<DispatchedExecutableMethod<T, ?>> methods = new ArrayList<>(executableMethods.length);
                for (int i = 0; i < executableMethods.length; i++) {
                    if (!contains(postConstructIndexes, i) && !contains(preDestroyIndexes, i)) {
                        methods.add(executableMethods[i]);
                    }
                }
                executableMethodsList = Collections.unmodifiableList(methods);
            }
        }
        return (List) executableMethodsList;
    }

    @Override
    public List<ExecutableMethod<T, ?>> getPostConstructExecutableMethods() {
        if (postConstructMethodsList == null) {
            postConstructMethodsList = methodsAt(getPostConstructMethodIndexes());
        }
        return postConstructMethodsList;
    }

    @Override
    public List<ExecutableMethod<T, ?>> getPreDestroyExecutableMethods() {
        if (preDestroyMethodsList == null) {
            preDestroyMethodsList = methodsAt(getPreDestroyMethodIndexes());
        }
        return preDestroyMethodsList;
    }

    /**
     * The indexes of the {@link jakarta.annotation.PostConstruct} callbacks compiled into this definition, in
     * invocation order. Generated code overrides this when the bean intercepts its post-construct phase.
     *
     * @return The indexes, or {@code null} when no callbacks were compiled in
     * @since 5.2.0
     */
    protected int @Nullable [] getPostConstructMethodIndexes() {
        return null;
    }

    /**
     * The indexes of the {@link jakarta.annotation.PreDestroy} callbacks compiled into this definition, in
     * invocation order. Generated code overrides this when the bean intercepts its pre-destroy phase.
     *
     * @return The indexes, or {@code null} when no callbacks were compiled in
     * @since 5.2.0
     */
    protected int @Nullable [] getPreDestroyMethodIndexes() {
        return null;
    }

    private List<ExecutableMethod<T, ?>> methodsAt(int @Nullable [] indexes) {
        if (indexes == null || indexes.length == 0) {
            return List.of();
        }
        List<ExecutableMethod<T, ?>> methods = new ArrayList<>(indexes.length);
        for (int index : indexes) {
            methods.add(getExecutableMethodByIndex(index));
        }
        return Collections.unmodifiableList(methods);
    }

    private static boolean contains(int @Nullable [] indexes, int index) {
        if (indexes != null) {
            for (int i : indexes) {
                if (i == index) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public <R> Optional<ExecutableMethod<T, R>> findMethod(String name, Class<?>... argumentTypes) {
        return Optional.ofNullable(getMethod(name, argumentTypes));
    }

    @Override
    public <R> Stream<ExecutableMethod<T, R>> findPossibleMethods(String name) {
        int[] postConstructIndexes = getPostConstructMethodIndexes();
        int[] preDestroyIndexes = getPreDestroyMethodIndexes();
        return IntStream.range(0, methodsReferences.length)
                .filter(i -> !contains(postConstructIndexes, i) && !contains(preDestroyIndexes, i))
                .filter(i -> methodsReferences[i].methodName.equals(name))
                .mapToObj(this::getExecutableMethodByIndex);
    }

    /**
     * Gets {@link ExecutableMethod} method by its index.
     *
     * @param index The method index
     * @param <R>   The result type
     * @return The {@link ExecutableMethod}
     */
    @UsedByGeneratedCode
    @Override
    public <R> ExecutableMethod<T, R> getExecutableMethodByIndex(int index) {
        DispatchedExecutableMethod<T, R> executableMethod = (DispatchedExecutableMethod<T, R>) executableMethods[index];
        if (executableMethod == null) {
            MethodReference methodsReference = methodsReferences[index];
            executableMethod = new DispatchedExecutableMethod<>(dispatcher, index, methodsReference, methodsReference.annotationMetadata);
            if (environment != null) {
                executableMethod.configure(environment);
            }
            if (beanContext != null) {
                executableMethod.configure(beanContext);
            }
            executableMethods[index] = executableMethod;
        }
        return executableMethod;
    }

    /**
     * Finds executable method or returns a null otherwise.
     *
     * @param name          The method name
     * @param argumentTypes The method arguments
     * @param <R>           The return type
     * @return The {@link ExecutableMethod}
     */
    @UsedByGeneratedCode
    @Nullable
    protected <R> ExecutableMethod<T, R> getMethod(String name, Class<?>... argumentTypes) {
        int[] postConstructIndexes = getPostConstructMethodIndexes();
        int[] preDestroyIndexes = getPreDestroyMethodIndexes();
        for (int i = 0; i < methodsReferences.length; i++) {
            MethodReference methodReference = methodsReferences[i];
            if (!contains(postConstructIndexes, i)
                    && !contains(preDestroyIndexes, i)
                    && methodReference.methodName.equals(name)
                    && methodReference.arguments.length == argumentTypes.length
                    && argumentsTypesMatch(argumentTypes, methodReference.arguments)) {
                return getExecutableMethodByIndex(i);
            }
        }
        return null;
    }

    /**
     * Triggers the invocation of the method at index. Used by {@link ExecutableMethod#invoke(Object, Object...)}.
     *
     * @param index  The method index
     * @param target The target
     * @param args   The arguments
     * @return The result
     */
    @UsedByGeneratedCode
    protected Object dispatch(int index, T target, @Nullable Object[] args) {
        throw unknownDispatchAtIndexException(index);
    }

    /**
     * Find {@link Method} representation at the method by index. Used by {@link ExecutableMethod#getTargetMethod()}.
     *
     * @param index The index
     * @return The method
     */
    @UsedByGeneratedCode
    protected abstract Method getTargetMethodByIndex(int index);

    /**
     * Find {@link Method} representation at the method by index. Used by {@link ExecutableMethod#getTargetMethod()}.
     *
     * @param index The index
     * @return The method
     * @since 3.4.0
     */
    @UsedByGeneratedCode
    // this logic must allow reflection
    @SuppressWarnings("java:S3011")
    protected final Method getAccessibleTargetMethodByIndex(int index) {
        Method method = getTargetMethodByIndex(index);
        if (ClassUtils.REFLECTION_LOGGER.isDebugEnabled()) {
            ClassUtils.REFLECTION_LOGGER.debug("Reflectively accessing method {} of type {}", method, method.getDeclaringClass());
        }
        method.setAccessible(true);
        return method;
    }

    /**
     * Creates a new exception when the method at index is not found.
     *
     * @param index The method index
     * @return The exception
     */
    @UsedByGeneratedCode
    protected final Throwable unknownMethodAtIndexException(int index) {
        return new IllegalStateException("Unknown method at index: " + index);
    }

    /**
     * Creates a new exception when the dispatch at index is not found.
     *
     * @param index The method index
     * @return The exception
     */
    @UsedByGeneratedCode
    protected final RuntimeException unknownDispatchAtIndexException(int index) {
        return new IllegalStateException("Unknown dispatch at index: " + index);
    }

    /**
     * Checks if the method at index matches name and argument types.
     *
     * @param index         The method index
     * @param name          The method name
     * @param argumentTypes The method arguments
     * @return true if matches
     */
    @UsedByGeneratedCode
    protected final boolean methodAtIndexMatches(int index, String name, Class<?>[] argumentTypes) {
        MethodReference methodReference = methodsReferences[index];
        Argument<?>[] arguments = methodReference.arguments;
        if (arguments.length != argumentTypes.length || !methodReference.methodName.equals(name)) {
            return false;
        }
        return argumentsTypesMatch(argumentTypes, arguments);
    }

    private boolean argumentsTypesMatch(Class<?>[] argumentTypes, Argument<?>[] arguments) {
        for (int i = 0; i < arguments.length; i++) {
            if (!argumentTypes[i].equals(arguments[i].getType())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Internal class representing method's metadata.
     *
     * @param declaringType      The declaring type
     * @param annotationMetadata The metadata
     * @param methodName         The method name
     * @param returnArgument     The return argument
     * @param arguments          The arguments
     * @param isAbstract         Is abstract
     * @param isSuspend          Is suspend
     */
    public record MethodReference(Class<?> declaringType,
                                  AnnotationMetadata annotationMetadata,
                                  String methodName,
                                  @Nullable Argument<?> returnArgument,
                                  Argument<?>[] arguments,
                                  boolean isAbstract,
                                  boolean isSuspend) {
        /**
         * The constructor.
         *
         * @param declaringType      The declaring type
         * @param annotationMetadata The metadata
         * @param methodName         The method name
         * @param returnArgument     The return argument
         * @param arguments          The arguments
         * @param isAbstract         Is abstract
         * @param isSuspend          Is suspend
         */
        public MethodReference(Class<?> declaringType,
                               AnnotationMetadata annotationMetadata,
                               String methodName,
                               Argument<?> returnArgument,
                               Argument<?>[] arguments,
                               boolean isAbstract,
                               boolean isSuspend) {
            this.declaringType = declaringType;
            this.annotationMetadata = annotationMetadata == null ? AnnotationMetadata.EMPTY_METADATA : annotationMetadata;
            this.methodName = methodName;
            this.returnArgument = returnArgument;
            this.arguments = arguments == null ? Argument.ZERO_ARGUMENTS : arguments;
            this.isAbstract = isAbstract;
            this.isSuspend = isSuspend;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            MethodReference that = (MethodReference) o;
            return isAbstract == that.isAbstract && isSuspend == that.isSuspend && Objects.equals(declaringType, that.declaringType) && Objects.equals(annotationMetadata, that.annotationMetadata) && Objects.equals(methodName, that.methodName) && Objects.equals(returnArgument, that.returnArgument) && Arrays.equals(arguments, that.arguments);
        }

        @Override
        public int hashCode() {
            return methodName.hashCode();
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("MethodReference{");
            sb.append("declaringType=").append(declaringType);
            sb.append(", annotationMetadata=").append(annotationMetadata);
            sb.append(", methodName='").append(methodName).append('\'');
            sb.append(", returnArgument=").append(returnArgument);
            sb.append(", arguments=").append(Arrays.toString(arguments));
            sb.append(", isAbstract=").append(isAbstract);
            sb.append(", isSuspend=").append(isSuspend);
            sb.append('}');
            return sb.toString();
        }
    }

    /**
     * A copy of a generated definition with its own methods. The generated definition invokes them and answers the
     * lifecycle method indexes.
     *
     * @param <T> The type
     */
    private static final class Copy<T> extends AbstractExecutableMethodsDefinition<T> {

        private final AbstractExecutableMethodsDefinition<T> generated;

        private Copy(AbstractExecutableMethodsDefinition<T> generated) {
            super(generated);
            this.generated = generated;
        }

        @Override
        protected Object dispatch(int index, T target, @Nullable Object[] args) {
            return generated.dispatch(index, target, args);
        }

        @Override
        protected Method getTargetMethodByIndex(int index) {
            return generated.getTargetMethodByIndex(index);
        }

        @Override
        protected int @Nullable [] getPostConstructMethodIndexes() {
            return generated.getPostConstructMethodIndexes();
        }

        @Override
        protected int @Nullable [] getPreDestroyMethodIndexes() {
            return generated.getPreDestroyMethodIndexes();
        }

        @Override
        public boolean requiresMethodProcessing() {
            return generated.requiresMethodProcessing();
        }

        @Override
        protected <R> @Nullable ExecutableMethod<T, R> getMethod(String name, Class<?>... argumentTypes) {
            // The generated definition looks the method up by name, then the copy returns its own method at that index
            ExecutableMethod<T, R> method = generated.getMethod(name, argumentTypes);
            return method instanceof DispatchedExecutableMethod<T, R> dispatched ? getExecutableMethodByIndex(dispatched.index) : null;
        }
    }

    /**
     * An {@link ExecutableMethod} instance based on dispatching by index.
     *
     * @param <T> The type
     * @param <R> The result type
     */
    private static final class DispatchedExecutableMethod<T, R> implements ExecutableMethod<T, R>,
        EnvironmentConfigurable, BeanContextConfigurable, UnsafeExecutable<T, R> {

        /**
         * A kept {@code null} value.
         */
        private static final Object NULL_VALUE = new Object();
        private static final long NOT_CACHEABLE = -1;
        private static final int ENTRY_VALUE = 0;
        private static final int ENTRY_ANNOTATION_VALUE = 1;
        private static final int ENTRY_ANNOTATION = 2;
        private static final byte CACHE_NEVER = 0;
        private static final byte CACHE_STATIC = 1;
        private static final byte CACHE_PER_REVISION = 2;

        private final AbstractExecutableMethodsDefinition dispatcher;
        private final int index;
        private final MethodReference methodReference;
        /**
         * The generated metadata of the method, wrapped by the configured metadata.
         */
        private final AnnotationMetadata declaredAnnotationMetadata;
        /**
         * The metadata resolving the property placeholders with the environment of the last configured context.
         */
        private AnnotationMetadata environmentAnnotationMetadata;
        private AnnotationMetadata annotationMetadata;
        @Nullable
        private ReturnType<R> returnType;
        private final Argument<?>[] arguments;
        private final boolean argumentsAnnotationsWithExpressions;
        @Nullable
        @SuppressWarnings("java:S3077") // a Method is immutable: publishing the reference publishes it
        private volatile Method targetMethod;

        // Built on first use and published without synchronization: ImmutableStringIntMap is filled in
        // its constructor and only has final fields, so it is safe to share via a data race.
        // Concurrent first calls may each build an equal table; that is harmless.
        @Nullable
        private ImmutableStringIntMap argumentIndex;

        /**
         * The values kept by {@link #getCachedValue(Class, Function)} and the annotation lookups: the revision and the
         * metadata they were computed for, then key/entry pairs. {@code null} until a value is kept, then replaced by a longer copy
         * for each new key, or by a new array for a new revision.
         */
        @SuppressWarnings("java:S3077") // never modified once published: volatile publishes it
        private volatile Object @Nullable [] cachedValues;
        /**
         * How values derived from the metadata can be kept, updated when the metadata is replaced. The method is
         * configured before it is published to callers, so the configuration fields are not read concurrently with
         * their writes.
         */
        private byte cacheMode;
        /**
         * The properties that the placeholders of the metadata resolve from, set by the environment.
         */
        @Nullable
        private PropertySourcePropertyResolver properties;

        @Override
        @SuppressWarnings({"unchecked", "NullAway", "java:S2637"}) // V is nullable when the factory returns null
        public <V extends @Nullable Object> V getCachedValue(Class<V> key, Function<? super ExecutableMethod<T, R>, ? extends V> factory) {
            AnnotationMetadata metadata = annotationMetadata;
            long revision = revision();
            if (revision == NOT_CACHEABLE) {
                return factory.apply(this);
            }
            Object kept = find(metadata, revision, key, ENTRY_VALUE);
            if (kept != null) {
                return (V) unmask(kept);
            }
            return (V) unmask(keep(metadata, revision, key, mask(factory.apply(this)), ENTRY_VALUE));
        }

        @Override
        @SuppressWarnings("unchecked")
        public <A extends Annotation> @Nullable AnnotationValue<A> getCachedAnnotationValue(Class<A> annotationType) {
            AnnotationMetadata metadata = annotationMetadata;
            long revision = revision();
            if (revision == NOT_CACHEABLE) {
                return metadata.getAnnotation(annotationType);
            }
            Object kept = find(metadata, revision, annotationType, ENTRY_ANNOTATION_VALUE);
            if (kept == null) {
                kept = keep(metadata, revision, annotationType, new KeptAnnotationValue(metadata.getAnnotation(annotationType)), ENTRY_ANNOTATION_VALUE);
            }
            return (AnnotationValue<A>) ((KeptAnnotationValue) kept).value;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <A extends Annotation> @Nullable A getCachedAnnotation(Class<A> annotationType) {
            AnnotationMetadata metadata = annotationMetadata;
            long revision = revision();
            if (revision == NOT_CACHEABLE) {
                return ExecutableMethod.super.getCachedAnnotation(annotationType);
            }
            Object kept = find(metadata, revision, annotationType, ENTRY_ANNOTATION);
            if (kept == null) {
                // Kept apart from a value kept by getCachedValue under the same annotation type
                kept = keep(metadata, revision, annotationType, new KeptAnnotation(ExecutableMethod.super.getCachedAnnotation(annotationType)), ENTRY_ANNOTATION);
            }
            return (A) ((KeptAnnotation) kept).annotation;
        }

        /**
         * @param metadata The metadata of the method
         * @param revision The revision
         * @param key      The key
         * @param kind     The kind of entry
         * @return The kept entry, or {@code null} if none is kept for the metadata and the revision
         */
        @Nullable
        private Object find(AnnotationMetadata metadata, long revision, Class<?> key, int kind) {
            Object[] values = cachedValues;
            if (values != null && values[1] == metadata && (long) values[0] == revision) {
                for (int i = 2; i < values.length; i += 2) {
                    if (values[i] == key && entryKind(values[i + 1]) == kind) {
                        return values[i + 1];
                    }
                }
            }
            return null;
        }

        /**
         * The revision of what values derived from the metadata depend on, read before deriving them.
         *
         * @return {@code 0} when the metadata is static, the revision of the properties when it resolves property
         * placeholders, or {@link #NOT_CACHEABLE} when values can't be kept: evaluated expressions depend on the
         * invocation
         */
        private long revision() {
            return switch (cacheMode) {
                case CACHE_STATIC -> 0;
                case CACHE_PER_REVISION -> Objects.requireNonNull(properties).getRevision();
                default -> NOT_CACHEABLE;
            };
        }

        /**
         * Computes how values derived from the metadata can be kept.
         */
        private void updateCacheMode() {
            if (annotationMetadata.hasEvaluatedExpressions() || argumentsAnnotationsWithExpressions) {
                cacheMode = CACHE_NEVER;
            } else if (annotationMetadata.hasPropertyExpressions()) {
                cacheMode = properties != null ? CACHE_PER_REVISION : CACHE_NEVER;
            } else {
                cacheMode = CACHE_STATIC;
            }
        }

        /**
         * Keeps the entry under the key, unless another call kept one first, or the metadata or the properties it was
         * computed from changed meanwhile. Synchronized with the configuration, which replaces them.
         *
         * @param metadata The metadata the entry was computed from
         * @param revision The revision the entry was computed for
         * @param key      The key
         * @param entry    The entry
         * @param kind     The kind of entry
         * @return The kept entry
         */
        private synchronized Object keep(AnnotationMetadata metadata, long revision, Class<?> key, Object entry, int kind) {
            if (metadata != annotationMetadata || revision() != revision) {
                return entry;
            }
            Object[] values = cachedValues;
            if (values == null || values[1] != metadata || (long) values[0] != revision) {
                // The entries of other metadata or of an older revision are dropped
                cachedValues = new Object[] {revision, metadata, key, entry};
                return entry;
            }
            for (int i = 2; i < values.length; i += 2) {
                if (values[i] == key && entryKind(values[i + 1]) == kind) {
                    return values[i + 1];
                }
            }
            Object[] newValues = Arrays.copyOf(values, values.length + 2);
            newValues[values.length] = key;
            newValues[values.length + 1] = entry;
            cachedValues = newValues;
            return entry;
        }

        private static int entryKind(Object entry) {
            if (entry instanceof KeptAnnotationValue) {
                return ENTRY_ANNOTATION_VALUE;
            }
            return entry instanceof KeptAnnotation ? ENTRY_ANNOTATION : ENTRY_VALUE;
        }

        private static Object mask(@Nullable Object value) {
            return value != null ? value : NULL_VALUE;
        }

        @Nullable
        private static Object unmask(Object value) {
            return value == NULL_VALUE ? null : value;
        }

        private DispatchedExecutableMethod(AbstractExecutableMethodsDefinition dispatcher,
                                           int index,
                                           MethodReference methodReference,
                                           AnnotationMetadata annotationMetadata) {
            this.dispatcher = dispatcher;
            this.index = index;
            this.methodReference = methodReference;
            this.declaredAnnotationMetadata = annotationMetadata;
            this.environmentAnnotationMetadata = annotationMetadata;
            this.annotationMetadata = annotationMetadata;
            MethodArguments methodArguments = methodArguments(methodReference);
            this.arguments = methodArguments.arguments;
            this.argumentsAnnotationsWithExpressions = methodArguments.argumentsAnnotationsWithExpressions;
            updateCacheMode();
        }

        @Override
        public synchronized void configure(Environment environment) {
            cachedValues = null;
            if (declaredAnnotationMetadata.hasPropertyExpressions()) {
                environmentAnnotationMetadata = new MethodAnnotationMetadata(declaredAnnotationMetadata, environment);
                annotationMetadata = environmentAnnotationMetadata;
            }
            properties = environment instanceof PropertyResolverDelegate delegate
                && delegate.delegate() instanceof PropertySourcePropertyResolver resolver ? resolver : null;
            updateCacheMode();
        }

        @Override
        public synchronized void configure(BeanContext beanContext) {
            cachedValues = null;
            annotationMetadata = EvaluatedAnnotationMetadata.wrapIfNecessary(environmentAnnotationMetadata);
            if (annotationMetadata instanceof EvaluatedAnnotationMetadata eam) {
                eam.configure(beanContext);
            }
            updateCacheMode();
            if (argumentsAnnotationsWithExpressions) {
                for (Argument<?> argument : arguments) {
                    AnnotationMetadata argumentAnnotationMetadata = argument.getAnnotationMetadata();
                    if (argumentAnnotationMetadata instanceof EvaluatedAnnotationMetadata eam) {
                        eam.configure(beanContext);
                    }
                }
            }
        }

        @Override
        public boolean hasPropertyExpressions() {
            return annotationMetadata.hasPropertyExpressions();
        }

        @Override
        public boolean hasEvaluatedExpressions() {
            return annotationMetadata.hasEvaluatedExpressions();
        }

        @Override
        public boolean isAbstract() {
            return methodReference.isAbstract;
        }

        @Override
        public boolean isSuspend() {
            return methodReference.isSuspend;
        }

        @Override
        public Class<T> getDeclaringType() {
            return (Class<T>) methodReference.declaringType;
        }

        @Override
        public String getMethodName() {
            return methodReference.methodName;
        }

        @Override
        public Argument<?>[] getArguments() {
            return arguments;
        }

        @Override
        public int argumentIndexOf(String name) {
            ArgumentUtils.requireNonNull("name", name);
            ImmutableStringIntMap index = argumentIndex;
            if (index == null) {
                index = ImmutableStringIntMap.of(arguments, Argument::getName, true, null);
                argumentIndex = index;
            }
            return index.get(name, -1);
        }

        @Override
        public Method getTargetMethod() {
            // The generated getTargetMethodByIndex looks the method up reflectively on every call; resolve once
            Method method = targetMethod;
            if (method == null) {
                method = dispatcher.getTargetMethodByIndex(index);
                targetMethod = method;
            }
            return method;
        }

        @Override
        public ReturnType<R> getReturnType() {
            if (returnType == null) {
                // Return type also contains method annotations (Micronaut 3)
                Argument<R> returnTypeArgument = methodReference.returnArgument == null ? (Argument<R>) Argument.VOID : (Argument<R>) methodReference.returnArgument;
                AnnotationMetadata returnTypeAnnotationMetadata;
                if (!returnTypeArgument.getAnnotationMetadata().isEmpty() && !annotationMetadata.isEmpty()) {
                    returnTypeAnnotationMetadata = new AnnotationMetadataHierarchy(returnTypeArgument.getAnnotationMetadata(), annotationMetadata);
                } else if (!returnTypeArgument.getAnnotationMetadata().isEmpty()) {
                    returnTypeAnnotationMetadata = returnTypeArgument.getAnnotationMetadata();
                } else {
                    returnTypeAnnotationMetadata = annotationMetadata;
                }
                if (returnTypeArgument.getAnnotationMetadata() != returnTypeAnnotationMetadata) {
                    // An array keeps the component it was written with, and the annotations of it
                    Argument<?> componentType = returnTypeArgument.componentType();
                    returnTypeArgument = returnTypeArgument.isRawType() ? Argument.ofRawType(
                            returnTypeArgument.getType(),
                            null,
                            returnTypeAnnotationMetadata,
                            returnTypeArgument.getTypeParameters()
                    ) : Argument.of(
                            returnTypeArgument.getType(),
                            returnTypeAnnotationMetadata,
                            returnTypeArgument.getTypeParameters()
                    );
                    if (componentType != null) {
                        returnTypeArgument = returnTypeArgument.withComponentType(componentType);
                    }
                }
                returnType = new DefaultReturnType<>(
                        returnTypeArgument,
                        returnTypeAnnotationMetadata,
                        methodReference.isSuspend
                );
            }
            return returnType;
        }

        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return annotationMetadata;
        }

        @Override
        public R invoke(T instance, Object... arguments) {
            ArgumentUtils.validateArguments(this, methodReference.arguments, arguments);
            return (R) dispatcher.dispatch(index, instance, arguments);
        }

        @Override
        public R invokeUnsafe(T instance, Object... arguments) {
            return (R) dispatcher.dispatch(index, instance, arguments);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof AbstractExecutableMethodsDefinition.DispatchedExecutableMethod that)) {
                return false;
            }
            return Objects.equals(methodReference.declaringType, that.methodReference.declaringType) &&
                    Objects.equals(methodReference.methodName, that.methodReference.methodName) &&
                    Arrays.equals(methodReference.arguments, that.methodReference.arguments);
        }

        @Override
        public int hashCode() {
            return ObjectUtils.hash(
                    methodReference.declaringType,
                    methodReference.methodName,
                    Arrays.hashCode(methodReference.arguments)
            );
        }

        @Override
        public String toString() {
            String text = Argument.toString(getArguments());
            return getReturnType().getType().getSimpleName() + " " + getMethodName() + "(" + text + ")";
        }

        private record MethodArguments(Argument<?>[] arguments, boolean argumentsAnnotationsWithExpressions) {
        }

        private MethodArguments methodArguments(MethodReference methodReference) {
            final Argument<?>[] arguments= new Argument[methodReference.arguments.length];
            boolean foundExpressions = false;
            Argument<?>[] methodArguments = methodReference.arguments;
            for (int i = 0; i < methodArguments.length; i++) {
                Argument<?> argument = methodArguments[i];
                AnnotationMetadata argumentAnnotationMetadata = argument.getAnnotationMetadata();
                AnnotationMetadata wrappedArgumentAnnotationMetadata = EvaluatedAnnotationMetadata.wrapIfNecessary(argumentAnnotationMetadata);
                if (argumentAnnotationMetadata == wrappedArgumentAnnotationMetadata) {
                    arguments[i] = argument;
                } else {
                    foundExpressions = true;
                    if (argument instanceof GenericPlaceholder<?>) {
                        // the placeholder keeps what it is: the bounds of its variable, and whether it stands for a
                        // type resolved in place of the variable
                        arguments[i] = argument.withAnnotationMetadata(wrappedArgumentAnnotationMetadata);
                    } else {
                        arguments[i] = Argument.of(argument.getType(), argument.getName(), wrappedArgumentAnnotationMetadata, argument.getTypeParameters());
                    }
                }
            }
            return new MethodArguments(arguments, foundExpressions);
        }
    }

    /**
     * The default return type implementation.
     *
     * @param returnArgument     The return argument
     * @param annotationMetadata The annotation metadata
     * @param isSuspend          Is suspended
     * @param <R>                The return type
     */
    private record DefaultReturnType<R>(Argument<R> returnArgument,
                                        AnnotationMetadata annotationMetadata,
                                        boolean isSuspend) implements ReturnType<R> {

        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return annotationMetadata;
        }

        @Override
        public Class<R> getType() {
            return returnArgument.getType();
        }

        @Override
        public boolean isSuspended() {
            return isSuspend;
        }

        @Override
        public Argument<?>[] getTypeParameters() {
            return returnArgument.getTypeParameters();
        }

        @Override
        public Map<String, Argument<?>> getTypeVariables() {
            return returnArgument.getTypeVariables();
        }

        @Override
        public Argument<R> asArgument() {
            return returnArgument;
        }
    }

    private static final class MethodAnnotationMetadata extends AbstractEnvironmentAnnotationMetadata {

        private final Environment environment;

        MethodAnnotationMetadata(AnnotationMetadata targetMetadata, Environment environment) {
            super(targetMetadata);
            this.environment = environment;
        }

        @Nullable
        @Override
        protected Environment getEnvironment() {
            return environment;
        }
    }

    /**
     * A value kept by {@code getCachedAnnotationValue}, told apart from a value kept by {@code getCachedValue} under
     * the same annotation type.
     *
     * @param value The annotation value or {@code null} when the method doesn't have the annotation
     */
    private record KeptAnnotationValue(@Nullable AnnotationValue<?> value) {
    }


    /**
     * An annotation kept by {@code getCachedAnnotation}, told apart from a value kept by {@code getCachedValue} under
     * the same annotation type.
     *
     * @param annotation The annotation or {@code null} when the method doesn't have it
     */
    private record KeptAnnotation(@Nullable Annotation annotation) {
    }

}
