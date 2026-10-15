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
package io.micronaut.context;

import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.Failure;
import io.micronaut.context.conditions.MatchesAbsenceOfClassesCondition;
import io.micronaut.context.conditions.MatchesPresenceOfClassesCondition;
import io.micronaut.context.conditions.MatchesPresenceOfEntitiesCondition;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ExceptionUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The reference of a bean definition whose {@code META-INF/micronaut} entry carries a descriptor, which a context
 * creates when lazy bean definitions are switched on ({@code micronaut.bean-definitions.lazy=true}). It answers the
 * flags, the names, the order, the presence of annotations, the declared qualifier and the conditions that are
 * checked before a definition is loaded from the descriptor, and loads the definition for anything else.
 *
 * <p>Unlike the reference of a compiled definition, it is not a {@link BeanDefinition}, and it is equal only to
 * itself. {@link #isEnabled(BeanContext, BeanResolutionContext)} always checks the conditions that can be checked
 * before the definition is loaded, also once it is loaded: the others are checked on the definition. When the
 * definition cannot be linked, the reference is not present and {@link #load()} returns {@code null}. Any other
 * failure to load it is kept and thrown again by every later load.</p>
 *
 * @param <T> The bean type
 * @author Álvaro Sánchez-Mariscal
 * @since 5.3.0
 */
@Internal
public final class LazyBeanDefinitionReference<T> implements BeanDefinitionReference<T> {

    private static final Logger LOG = LoggerFactory.getLogger(LazyBeanDefinitionReference.class);
    private static final MethodHandles.Lookup PUBLIC_LOOKUP = MethodHandles.publicLookup();
    private static final MethodType NO_ARGUMENTS = MethodType.methodType(void.class);
    private static final VarHandle STATE_HANDLE;
    private static final String COULD_NOT_INITIALIZE = "Could not initialize class ";
    /**
     * The state of a reference whose definition cannot be linked, or whose bean type does not load.
     */
    private static final Object ABSENT = new Object();
    /**
     * The declared qualifier of a bean that declares none.
     */
    private static final Object NO_QUALIFIER = new Object();
    private static final String GET_BEAN_NAME = "getBeanName";
    private static final String GET_DECLARED_QUALIFIER = "getDeclaredQualifier";

    static {
        try {
            STATE_HANDLE = MethodHandles.lookup().findVarHandle(LazyBeanDefinitionReference.class, "state", Object.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    final BeanDefinitionDescriptorData data;
    final LazyBeanDefinitions definitions;
    /**
     * The answers given before the definition was loaded, kept for the verification only.
     */
    @Nullable
    final Map<String, LazyBeanDefinitionVerifier.Answer> answers;
    private final ClassLoader classLoader;
    private final DescriptorAnnotationMetadata metadata;
    /**
     * {@code null} until the definition is loaded, then the instance of the class the entry names, {@link #ABSENT},
     * or the {@link Failed failure} met loading it.
     */
    @SuppressWarnings({"unused", "java:S3077"}) // written through STATE_HANDLE
    private volatile @Nullable Object state;
    @SuppressWarnings("java:S3077") // a class loader is thread-safe
    @Nullable
    private volatile ClassLoader owner;
    @SuppressWarnings("java:S3077") // immutable once published
    private volatile Condition @Nullable [] conditions;
    @SuppressWarnings("java:S3077") // a class is immutable
    @Nullable
    private volatile Class<T> beanType;
    @SuppressWarnings("java:S3077") // immutable once published
    @Nullable
    private volatile Set<Class<?>> exposedTypes;
    @SuppressWarnings("java:S3077") // immutable once published
    private volatile Class<?> @Nullable [] indexes;
    /**
     * {@code null} until the declared qualifier is read from the descriptor, then the qualifier or {@link #NO_QUALIFIER}.
     */
    @SuppressWarnings("java:S3077") // a qualifier is immutable once published
    @Nullable
    private volatile Object declaredQualifier;
    /**
     * With the verification on, the difference found between the descriptor and the definition when it was loaded,
     * which every lookup that reaches the reference fails with, whichever thread loaded it.
     */
    @Nullable
    private volatile String difference;

    LazyBeanDefinitionReference(BeanDefinitionDescriptorData data, ClassLoader classLoader, LazyBeanDefinitions definitions) {
        this.data = data;
        this.classLoader = classLoader;
        this.definitions = definitions;
        this.answers = definitions.verify ? new ConcurrentHashMap<>() : null;
        this.metadata = new DescriptorAnnotationMetadata(this);
    }

    @Override
    public String getBeanDefinitionName() {
        return data.definitionName;
    }

    @Override
    public String getName() {
        return data.beanTypeName;
    }

    @Override
    public int getOrder() {
        return data.order;
    }

    @Override
    public boolean isContextScope() {
        return data.is(BeanDefinitionDescriptorFormat.FLAG_CONTEXT_SCOPE);
    }

    @Override
    public boolean isParallel() {
        return data.is(BeanDefinitionDescriptorFormat.FLAG_PARALLEL);
    }

    @Override
    public boolean isProxiedBean() {
        return data.is(BeanDefinitionDescriptorFormat.FLAG_PROXIED_BEAN);
    }

    @Override
    public boolean isProxyTarget() {
        return data.is(BeanDefinitionDescriptorFormat.FLAG_PROXY_TARGET);
    }

    @Override
    public boolean isSingleton() {
        return data.is(BeanDefinitionDescriptorFormat.FLAG_SINGLETON);
    }

    @Override
    public boolean isPrimary() {
        return data.is(BeanDefinitionDescriptorFormat.FLAG_PRIMARY);
    }

    @Override
    public boolean isConfigurationProperties() {
        return data.is(BeanDefinitionDescriptorFormat.FLAG_CONFIGURATION_PROPERTIES);
    }

    @Override
    public boolean isContainerType() {
        return data.is(BeanDefinitionDescriptorFormat.FLAG_CONTAINER_TYPE);
    }

    @Override
    public boolean requiresMethodProcessing() {
        return data.is(BeanDefinitionDescriptorFormat.FLAG_REQUIRES_METHOD_PROCESSING);
    }

    @Override
    public boolean isPresent() {
        return state != ABSENT;
    }

    /**
     * Checks the conditions that can be checked before the definition is loaded, which are all this answers for,
     * before and after the definition is loaded.
     *
     * @param context The context
     * @return Whether they hold
     */
    @Override
    public boolean isEnabled(BeanContext context) {
        return isPreLoadEnabled(context, null);
    }

    /**
     * Checks the conditions that can be checked before the definition is loaded, which are all this answers for,
     * before and after the definition is loaded.
     *
     * @param context           The context
     * @param resolutionContext The resolution context
     * @return Whether they hold
     */
    @Override
    public boolean isEnabled(BeanContext context, @Nullable BeanResolutionContext resolutionContext) {
        return isPreLoadEnabled(context, resolutionContext);
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return metadata;
    }

    // the methods of AnnotationMetadata that AnnotationMetadataDelegate leaves to their defaults, answered by the
    // metadata of the descriptor as every other one is

    @Override
    public boolean isAnnotationPresent(String annotationName) {
        return metadata.isAnnotationPresent(annotationName);
    }

    @Override
    public boolean isDeclaredAnnotationPresent(String annotationName) {
        return metadata.isDeclaredAnnotationPresent(annotationName);
    }

    @Override
    public boolean hasDeclaredStereotype(@Nullable String... annotations) {
        return metadata.hasDeclaredStereotype(annotations);
    }

    @Override
    public boolean hasStereotypeNonRepeating(Class<? extends Annotation> annotation) {
        return metadata.hasStereotypeNonRepeating(annotation);
    }

    @Override
    public Map<CharSequence, Object> getValues(String annotation) {
        return metadata.getValues(annotation);
    }

    @Override
    public <E extends Enum<E>> EnumSet<E> enumValuesSet(String annotation, String member, Class<E> enumType) {
        return metadata.enumValuesSet(annotation, member, enumType);
    }

    @Override
    public <E extends Enum<E>> EnumSet<E> enumValuesSet(Class<? extends Annotation> annotation, String member, Class<E> enumType) {
        return metadata.enumValuesSet(annotation, member, enumType);
    }

    @Override
    public Class<T> getBeanType() {
        Class<T> type = beanType;
        if (type == null) {
            @SuppressWarnings("unchecked")
            Class<T> loaded = (Class<T>) loadBeanType();
            if (loaded == null) {
                // as the constructor of the definition would have failed on it
                STATE_HANDLE.compareAndSet(this, null, ABSENT);
                throw new NoClassDefFoundError(data.beanTypeName);
            }
            type = loaded;
            beanType = type;
        }
        return type;
    }

    @Override
    public Set<Class<?>> getExposedTypes() {
        Set<Class<?>> types = exposedTypes;
        if (types == null) {
            Class<?>[] classes = loadClasses(data.exposedTypeNames);
            // as the generated definition answers when one of them is missing
            types = classes == null ? Collections.emptySet() : Set.of(classes);
            exposedTypes = types;
        }
        return types;
    }

    @Override
    public Class<?>[] getIndexes() {
        Class<?>[] types = indexes;
        if (types == null) {
            if (data.indexNames.length == 0) {
                types = new Class<?>[0];
            } else if (data.is(BeanDefinitionDescriptorFormat.FLAG_DECLARED_INDEXES)) {
                // all or none, as the generated definition answers
                types = loadClasses(data.indexNames);
                if (types == null) {
                    types = new Class<?>[0];
                }
            } else {
                // read from the annotation metadata, which keeps the ones that load when there are several
                types = loaded("getIndexes").getIndexes();
            }
            indexes = types;
        }
        return types.clone();
    }

    @Override
    public Argument<T> getGenericBeanType() {
        return loaded("getGenericBeanType").getGenericBeanType();
    }

    @Override
    public Argument<T> asArgument() {
        return loaded("asArgument").asArgument();
    }

    @Override
    public boolean isCandidateBean(@Nullable Argument<?> beanType) {
        return loaded("isCandidateBean").isCandidateBean(beanType);
    }

    @Override
    public Optional<String> getBeanName() {
        if (metadata.resolvesExpressions()) {
            // the name can be a property expression, which the loaded definition resolves
            return loaded(GET_BEAN_NAME).getBeanName();
        }
        int named = data.membership(AnnotationUtil.NAMED);
        if ((named & BeanDefinitionDescriptorFormat.MEMBERSHIP_DECLARED_ANNOTATION) == 0 && (named & BeanDefinitionDescriptorFormat.MEMBERSHIP_DECLARED_STEREOTYPE) == 0) {
            return record(GET_BEAN_NAME, Optional.empty(), BeanDefinitionReference::getBeanName);
        }
        AnnotationValue<?> qualifier = data.qualifier(AnnotationUtil.NAMED);
        Object name = qualifier == null ? null : qualifier.getValues().get(AnnotationMetadata.VALUE_MEMBER);
        if (name instanceof String value) {
            return record(GET_BEAN_NAME, Optional.of(value), BeanDefinitionReference::getBeanName);
        }
        return loaded(GET_BEAN_NAME).getBeanName();
    }

    @Override
    public @Nullable Qualifier<T> getDeclaredQualifier() {
        if (metadata.resolvesExpressions()) {
            // its values can be property expressions, which the loaded definition resolves
            return loaded(GET_DECLARED_QUALIFIER).getDeclaredQualifier();
        }
        Object qualifier = declaredQualifier;
        if (qualifier == null) {
            Qualifier<T> read = declaredQualifierOf();
            qualifier = read == null ? NO_QUALIFIER : read;
            declaredQualifier = qualifier;
        }
        @SuppressWarnings("unchecked")
        Qualifier<T> answered = qualifier == NO_QUALIFIER ? null : (Qualifier<T>) qualifier;
        if (answers != null) {
            LazyBeanDefinitionVerifier.record(this, GET_DECLARED_QUALIFIER, answered, BeanDefinitionReference::getDeclaredQualifier);
        }
        return answered;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private @Nullable Qualifier<T> declaredQualifierOf() {
        AnnotationValue<?>[] qualifiers = data.qualifiers;
        if (qualifiers.length == 0) {
            // the declared metadata can name the bean with a @Named that is not read as a qualifier annotation
            return data.has(AnnotationUtil.NAMED, BeanDefinitionDescriptorFormat.MEMBERSHIP_ANNOTATION | BeanDefinitionDescriptorFormat.MEMBERSHIP_STEREOTYPE)
                ? loaded(GET_DECLARED_QUALIFIER).getDeclaredQualifier()
                : null;
        }
        if (qualifiers.length == 1) {
            AnnotationValue qualifier = qualifiers[0];
            if (qualifier.getAnnotationName().equals(Qualifier.PRIMARY)) {
                return null;
            }
            return (Qualifier<T>) Qualifiers.byDeclaredAnnotation(metadata, qualifier);
        }
        Qualifier<T>[] declared = new Qualifier[qualifiers.length];
        for (int i = 0; i < qualifiers.length; i++) {
            declared[i] = (Qualifier<T>) Qualifiers.byDeclaredAnnotation(metadata, (AnnotationValue) qualifiers[i]);
        }
        return Qualifiers.byQualifiers(declared);
    }

    @Override
    @Deprecated(since = "5.0", forRemoval = true)
    @SuppressWarnings("removal")
    public @Nullable Qualifier<T> resolveDynamicQualifier() {
        return loaded("resolveDynamicQualifier").resolveDynamicQualifier();
    }

    /**
     * Loads the definition.
     *
     * @return The definition, or {@code null} if it cannot be linked
     */
    @Override
    @SuppressWarnings({"NullAway", "java:S2638"}) // null when the definition is not present, which an eager reference never is
    public @Nullable BeanDefinition<T> load() {
        BeanDefinitionReference<T> reference = instance("load");
        return reference == null ? null : reference.load();
    }

    /**
     * Loads the definition and configures it for the context.
     *
     * @param context The context
     * @return The definition, or {@code null} if it cannot be linked
     */
    @Override
    @SuppressWarnings({"NullAway", "java:S2638"}) // null when the definition is not present, which an eager reference never is
    public @Nullable BeanDefinition<T> load(BeanContext context) {
        BeanDefinitionReference<T> reference = instance("load");
        return reference == null ? null : reference.load(context);
    }

    @Override
    public String toString() {
        Object current = state;
        if (current instanceof BeanDefinitionReference<?> reference) {
            return reference.toString();
        }
        return "Definition: " + data.beanTypeName;
    }

    /**
     * @return Whether the definition was loaded
     */
    boolean isInstantiated() {
        return state instanceof BeanDefinitionReference<?>;
    }

    /**
     * @return The reference the entry names, if it was loaded, without failing on a difference the verification found
     */
    @Nullable
    BeanDefinitionReference<?> loadedInstance() {
        return state instanceof BeanDefinitionReference<?> reference ? reference : null;
    }

    /**
     * Keeps a difference the verification found once the definition was loaded, which every later lookup that reaches
     * the reference fails with.
     *
     * @param message The difference
     */
    void differs(String message) {
        if (difference == null) {
            difference = message;
        }
    }

    /**
     * With the verification on, asks a reference predicate of a lookup of the definition too, which eager mode asks,
     * and fails the lookup when the two answers differ: a descriptor that wrongly leaves a bean out is found this way.
     *
     * @param predicate The predicate
     * @param described What it answered for this reference
     */
    @SuppressWarnings({"unchecked", "rawtypes", "java:S1181"})
    public void verifyPredicate(Predicate predicate, boolean described) {
        if (!definitions.verify) {
            return;
        }
        BeanDefinitionReference<?> loaded;
        try {
            loaded = instance("the verification of a lookup", false);
        } catch (Throwable e) {
            // a definition that fails to load is not what this checks
            return;
        }
        String found = difference;
        if (found != null) {
            throw new IllegalStateException(found);
        }
        if (loaded != null) {
            LazyBeanDefinitionVerifier.comparePredicate(this, loaded, described, predicate.test(loaded));
        }
    }

    /**
     * Instantiates the reference the entry names, which for a compiled definition is the definition, without
     * configuring it for a context.
     *
     * @return The reference, or {@code null} if its class cannot be linked
     */
    public @Nullable BeanDefinitionReference<T> instantiate() {
        return instance("instantiate");
    }

    /**
     * The conditions that can be checked before the definition is loaded: all the ones this reference answers for.
     *
     * @param context           The context
     * @param resolutionContext The resolution context
     * @return Whether they hold
     */
    public boolean isPreLoadEnabled(BeanContext context, @Nullable BeanResolutionContext resolutionContext) {
        Condition[] preLoadConditions = resolvedConditions();
        if (preLoadConditions.length == 0) {
            return true;
        }
        DefaultConditionContext<LazyBeanDefinitionReference<T>> conditionContext = new DefaultConditionContext<>(context, this, resolutionContext);
        for (Condition condition : preLoadConditions) {
            if (!condition.matches(conditionContext)) {
                if (AbstractBeanContextConditional.ConditionLog.LOG.isDebugEnabled()) {
                    AbstractBeanContextConditional.ConditionLog.LOG.debug("Bean [{}] will not be loaded due to failing conditions:", this);
                    for (Failure failure : conditionContext.getFailures()) {
                        AbstractBeanContextConditional.ConditionLog.LOG.debug("* {}", failure.getMessage());
                    }
                }
                ((DefaultBeanContext) context).trackDisabledComponent(conditionContext);
                return false;
            }
        }
        return true;
    }

    /**
     * Whether the conditions that can be checked before the definition is loaded are only of classes, which need
     * no context, and hold: the definitions the warm-up loads.
     *
     * @return Whether they are and they do
     */
    public boolean isEnabledByClasses() {
        for (Condition condition : resolvedConditions()) {
            if (condition instanceof MatchesPresenceOfClassesCondition presence) {
                for (AnnotationClassValue<?> type : presence.classes()) {
                    if (type.getType().isEmpty()) {
                        return false;
                    }
                }
            } else if (condition instanceof MatchesAbsenceOfClassesCondition absence) {
                for (AnnotationClassValue<?> type : absence.classes()) {
                    if (type.getType().isPresent()) {
                        return false;
                    }
                }
            } else {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether the definition class loads and links, without running its static initializer: what the eager read
     * checks by instantiating it.
     *
     * @return Whether it does
     */
    public boolean links() {
        Object current = state;
        if (current != null) {
            return current != ABSENT;
        }
        try {
            PUBLIC_LOOKUP.findConstructor(Class.forName(data.definitionName, false, classLoader), NO_ARGUMENTS);
            return true;
        } catch (ClassNotFoundException | NoClassDefFoundError | NoSuchMethodException | IllegalAccessException | IllegalAccessError e) {
            // what the eager read drops
            STATE_HANDLE.compareAndSet(this, null, ABSENT);
            return false;
        } catch (LinkageError e) {
            // what the eager read fails on: kept, and met by the lookup that needs the definition
            STATE_HANDLE.compareAndSet(this, null, new Failed(e));
            return true;
        }
    }

    /**
     * Whether a lookup of a type finds the bean: whether the bean is exposed as the type, or indexed by it.
     *
     * @param typeName The name of the type
     * @return Whether it is
     */
    public boolean isFoundBy(String typeName) {
        return data.isFoundBy(typeName);
    }

    /**
     * @param typeName The name of a type
     * @return Whether the bean is exposed as the type
     */
    public boolean isExposedAs(String typeName) {
        return data.exposes(typeName);
    }

    /**
     * The type a disabled bean of this reference is listed with: the bean type, or, for a bean type with type
     * parameters, the generic type of the loaded definition.
     *
     * @return The type
     */
    public Argument<T> disabledBeanType() {
        Class<T> type = getBeanType();
        if (type.getTypeParameters().length > 0) {
            return getGenericBeanType();
        }
        return Argument.of(type);
    }

    /**
     * Logs, once a context has started, how many of its described definitions were loaded.
     *
     * @param provided   The references as the provider gave them
     * @param references The references of the context
     */
    public static void started(@Nullable List<?> provided, Collection<? extends BeanDefinitionReference<?>> references) {
        LazyBeanDefinitions definitions = provided == null ? null : LazyBeanDefinitions.of(provided);
        if (definitions != null && LOG.isInfoEnabled()) {
            LOG.info(definitions.summary(references));
        }
    }

    /**
     * With the verification on, loads every definition of a context that stops and that no lookup loaded, and
     * compares its descriptor with it.
     *
     * @param references The references of the context
     */
    public static void stopped(Collection<? extends BeanDefinitionReference<?>> references) {
        List<LazyBeanDefinitionReference<?>> lazy = LazyBeanDefinitions.lazy(references);
        if (!lazy.isEmpty() && lazy.get(0).definitions.verify) {
            LazyBeanDefinitionVerifier.verifyUnloaded(lazy);
        }
    }

    /**
     * @return The names of the types the bean is exposed as
     */
    public String[] exposedTypeNames() {
        return data.exposedTypeNames.clone();
    }

    /**
     * @return The names of the types the bean is indexed by
     */
    public String[] indexNames() {
        return data.indexNames.clone();
    }

    /**
     * @return The annotation metadata of the loaded definition
     */
    AnnotationMetadata loadedMetadata(String cause) {
        return loaded(cause).getAnnotationMetadata();
    }

    /**
     * @return The class loader that defines the definition class and resolves the classes the descriptor names
     */
    ClassLoader owner() {
        ClassLoader current = owner;
        if (current == null) {
            current = ownerOf(classLoader, BeanDefinitionDescriptorReader.ENTRIES + data.definitionName);
            owner = current;
        }
        return current;
    }

    /**
     * The loader that defines the class of an entry: the top-most ancestor that serves the very entry the provider's
     * loader finds, as under parent-first delegation. A child-first loader that holds its own copy of the entry, as a
     * web application loader can, defines the class itself. The class literals of a definition resolve through that
     * loader, so the names of the descriptor do too.
     */
    private static ClassLoader ownerOf(ClassLoader classLoader, String entry) {
        ClassLoader current = classLoader;
        ClassLoader parent = current.getParent();
        ClassLoader platform = ClassLoader.getPlatformClassLoader();
        if (parent == null || parent == platform) {
            return current;
        }
        URL found = classLoader.getResource(entry);
        // compared by their external forms, as URL#equals can resolve host names
        String own = found == null ? null : found.toExternalForm();
        while (parent != null && parent != platform) {
            URL theirs = parent.getResource(entry);
            if (theirs == null || !theirs.toExternalForm().equals(own)) {
                break;
            }
            current = parent;
            parent = current.getParent();
        }
        return current;
    }

    /**
     * Loads a class the descriptor names, without initializing it, as a class literal of the definition would.
     *
     * @param name The name, as {@link Class#getName()} gives it
     * @return The class, or {@code null} if it does not load
     */
    @Nullable
    Class<?> loadClass(String name) {
        Optional<Class<?>> primitive = ClassUtils.getPrimitiveType(name);
        if (primitive.isPresent()) {
            return primitive.get();
        }
        try {
            return Class.forName(name, false, owner());
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    /**
     * Loads the bean type, as the definition does: a class that is not found means the bean is not present, while
     * another linkage error, such as a class of a later version, is thrown.
     *
     * @return The bean type, or {@code null} if it is not found
     */
    private @Nullable Class<?> loadBeanType() {
        Optional<Class<?>> primitive = ClassUtils.getPrimitiveType(data.beanTypeName);
        if (primitive.isPresent()) {
            return primitive.get();
        }
        try {
            return Class.forName(data.beanTypeName, false, owner());
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            return null;
        }
    }

    private Class<?> @Nullable [] loadClasses(String[] names) {
        Class<?>[] classes = new Class<?>[names.length];
        for (int i = 0; i < names.length; i++) {
            Class<?> type = loadClass(names[i]);
            if (type == null) {
                return null;
            }
            classes[i] = type;
        }
        return classes;
    }

    /**
     * @return The conditions of the descriptor, with the classes they name resolved
     */
    Condition[] resolvedConditions() {
        Condition[] resolved = conditions;
        if (resolved == null) {
            Condition[] described = data.conditions;
            resolved = new Condition[described.length];
            for (int i = 0; i < described.length; i++) {
                resolved[i] = resolve(described[i]);
            }
            conditions = resolved;
        }
        return resolved;
    }

    /**
     * Resolves the classes a condition names, as the generated definition does with a class literal in a
     * {@code try} block: a class that does not load is kept by its name, which the condition reads as missing.
     */
    private Condition resolve(Condition condition) {
        if (condition instanceof MatchesPresenceOfClassesCondition presence) {
            return new MatchesPresenceOfClassesCondition(resolve(presence.classes()));
        } else if (condition instanceof MatchesAbsenceOfClassesCondition absence) {
            return new MatchesAbsenceOfClassesCondition(resolve(absence.classes()));
        } else if (condition instanceof MatchesPresenceOfEntitiesCondition entities) {
            return new MatchesPresenceOfEntitiesCondition(resolve(entities.classes()));
        }
        return condition;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private AnnotationClassValue<?>[] resolve(AnnotationClassValue<?>[] classes) {
        AnnotationClassValue<?>[] resolved = new AnnotationClassValue<?>[classes.length];
        for (int i = 0; i < classes.length; i++) {
            AnnotationClassValue<?> value = classes[i];
            Class<?> type = value.getType().isPresent() ? null : loadClass(value.getName());
            resolved[i] = type == null ? value : new AnnotationClassValue(type);
        }
        return resolved;
    }

    /**
     * Keeps an answer for the verification, when it is on.
     */
    @SuppressWarnings("unchecked")
    private <V> V record(String what, V answered, Function<BeanDefinitionReference<T>, V> replay) {
        if (answers != null) {
            LazyBeanDefinitionVerifier.record(this, what, answered, reference -> replay.apply((BeanDefinitionReference<T>) reference));
        }
        return answered;
    }

    /**
     * The loaded reference, for a question the descriptor does not answer.
     *
     * @param cause What asked, for the log
     * @return The reference the entry names
     */
    BeanDefinitionReference<T> loaded(String cause) {
        BeanDefinitionReference<T> reference = instance(cause);
        if (reference == null) {
            throw new NoClassDefFoundError("The bean definition [" + data.definitionName + "] is not present");
        }
        return reference;
    }

    @Nullable
    private BeanDefinitionReference<T> instance(String cause) {
        return instance(cause, true);
    }

    /**
     * Loads the definition, if it was not loaded, for the verification at the stop of a context, which reports a
     * difference without failing.
     */
    void instantiateForVerification() {
        instance("the verification", false);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private BeanDefinitionReference<T> instance(String cause, boolean failOnDifference) {
        Object current = state;
        if (current == null) {
            Object created = create();
            if (STATE_HANDLE.compareAndSet(this, null, created)) {
                current = created;
                loaded(current, cause);
            } else {
                // another thread won: there is one instance per reference
                current = state;
                if (created instanceof Failed mine && mine.cause() instanceof ExceptionInInitializerError
                    && current instanceof Failed kept && kept.cause() instanceof NoClassDefFoundError
                    && STATE_HANDLE.compareAndSet(this, current, created)) {
                    // the thread that ran the static initializer keeps its failure, not the one that waited for it
                    current = created;
                }
            }
        }
        if (current instanceof BeanDefinitionReference<?> reference) {
            String found = difference;
            if (failOnDifference && found != null) {
                throw new IllegalStateException(found);
            }
            return (BeanDefinitionReference<T>) reference;
        }
        if (current instanceof Failed failed) {
            return ExceptionUtils.sneakyThrow(failed.cause);
        }
        return null;
    }

    @SuppressWarnings("java:S1181") // a failure other than a link error is kept and thrown again
    private Object create() {
        try {
            Object created = PUBLIC_LOOKUP.findConstructor(Class.forName(data.definitionName, false, classLoader), NO_ARGUMENTS).invoke();
            if (created instanceof BeanDefinitionReference<?> reference && reference.isPresent()) {
                return reference;
            }
            return ABSENT;
        } catch (NoClassDefFoundError e) {
            String message = e.getMessage();
            if (message != null && message.startsWith(COULD_NOT_INITIALIZE) && message.substring(COULD_NOT_INITIALIZE.length()).equals(data.definitionName)) {
                // its static initializer failed on another thread, which keeps that failure
                return new Failed(e);
            }
            return ABSENT;
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | IllegalAccessError e) {
            return ABSENT;
        } catch (Throwable t) {
            return new Failed(t);
        }
    }

    private void loaded(Object current, String cause) {
        if (current instanceof BeanDefinitionReference<?> reference) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Loaded the bean definition [{}] for {}", data.definitionName, cause);
            }
            if (reference instanceof BeanDefinition<?> definition && !definition.getBeanType().getName().equals(data.beanTypeName)
                && definitions.relocationReported.compareAndSet(false, true)) {
                LOG.warn("The descriptor of the bean definition [{}] names the bean type [{}], but the definition has [{}]. "
                    + "Were its classes relocated without its META-INF/micronaut entry? Lazy bean definitions do not support that.",
                    data.definitionName, data.beanTypeName, definition.getBeanType().getName());
            }
            if (definitions.verify) {
                difference = LazyBeanDefinitionVerifier.verify(this, reference);
            }
        } else if (current == ABSENT && LOG.isDebugEnabled()) {
            LOG.debug("The bean definition [{}] cannot be linked and is not present", data.definitionName);
        }
    }

    /**
     * A failure met loading the definition, kept to be thrown again.
     *
     * @param cause The failure
     */
    private record Failed(Throwable cause) {
    }
}
