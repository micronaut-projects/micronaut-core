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
package io.micronaut.context;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.env.CachedEnvironment;
import io.micronaut.context.annotation.InjectScope;
import io.micronaut.context.env.ConfigurationPath;
import io.micronaut.context.exceptions.CircularDependencyException;
import io.micronaut.context.exceptions.DependencyInjectionException;
import io.micronaut.context.scope.CustomScope;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.naming.Named;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ArgumentCoercible;
import io.micronaut.core.type.TypeInformation;
import io.micronaut.core.type.TypeInformation.TypeFormat;
import io.micronaut.core.util.AnsiColour;
import io.micronaut.core.util.ObjectUtils;
import io.micronaut.core.value.PropertyResolver;
import io.micronaut.inject.*;

import io.micronaut.inject.proxy.InterceptedBean;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Default implementation of the {@link BeanResolutionContext} interface.
 *
 * @author Graeme Rocher
 * @since 1.2.3
 */
@Internal
public abstract class AbstractBeanResolutionContext implements BeanResolutionContext {

    private static final String CONSTRUCTOR_METHOD_NAME = "<init>";
    protected final DefaultBeanContext context;
    protected final BeanDefinition<?> rootDefinition;
    protected final Path path;
    @Nullable
    private final BeanResolutionTracer tracer;
    private Map<CharSequence, Object> attributes;
    private Qualifier<?> qualifier;
    private List<BeanRegistration<?>> dependentBeans;
    private BeanRegistration<?> dependentFactory;
    private final PropertyResolver propertyResolver;

    private ConfigurationPath configurationPath;

    /**
     * @param context        The bean context
     * @param rootDefinition The bean root definition
     */
    @Internal
    protected AbstractBeanResolutionContext(DefaultBeanContext context, BeanDefinition<?> rootDefinition) {
        this.context = context;
        this.rootDefinition = rootDefinition;
        this.path = new DefaultPath();
        if (context.traceMode != BeanResolutionTraceMode.NONE && rootDefinition != null && isTraceEnabled(rootDefinition.getBeanType().getTypeName(), context.tracePatterns)) {
            this.tracer = context.traceMode.getTracer().orElse(null);
        } else {
            this.tracer = null;
        }
        this.propertyResolver = context instanceof DefaultApplicationContext applicationContext ?  unwrap(applicationContext) : null;
    }

    private static PropertyResolver unwrap(PropertyResolver propertyResolver) {
        if (propertyResolver instanceof PropertyResolverDelegate delegate) {
            return unwrap(delegate.delegate());
        }
        return propertyResolver;
    }

    @Override
    public ConversionService getConversionService() {
        return context.getConversionService();
    }

    @Override
    public PropertyResolver getPropertyResolver() {
        return propertyResolver;
    }

    @Override
    public ConfigurationPath getConfigurationPath() {
        if (configurationPath != null) {
            return configurationPath;
        } else {
            this.configurationPath = ConfigurationPath.newPath();
            return configurationPath;
        }
    }

    @Override
    public ConfigurationPath setConfigurationPath(ConfigurationPath configurationPath) {
        ConfigurationPath old = this.configurationPath;
        this.configurationPath = configurationPath;
        return old;
    }

    @Override
    public void valueResolved(Argument<?> argument, Qualifier<?> qualifier, String property, Object value) {
        if (tracer != null) {
            tracer.traceValueResolved(
                this,
                (Argument<Object>) argument,
                property,
                value
            );
        }
    }

    private boolean isTraceEnabled(@NonNull String typeName, @NonNull Set<String> tracePatterns) {
        return (tracePatterns.isEmpty() || tracePatterns.stream().anyMatch(typeName::matches));
    }

    @Override
    public Object resolvePropertyValue(Argument<?> argument, String stringValue, String cliProperty, boolean isPlaceholder) {
        ApplicationContext applicationContext = (ApplicationContext) context;

        Argument<?> argumentType = argument;
        Class<?> wrapperType = null;
        Class<?> type = argument.getType();
        if (type == Optional.class) {
            wrapperType = Optional.class;
            argumentType = argument.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
        } else if (type == OptionalInt.class) {
            wrapperType = OptionalInt.class;
            argumentType = Argument.INT;
        } else if (type == OptionalLong.class) {
            wrapperType = OptionalLong.class;
            argumentType = Argument.LONG;
        } else if (type == OptionalDouble.class) {
            wrapperType = OptionalDouble.class;
            argumentType = Argument.DOUBLE;
        }

        ArgumentConversionContext<?> conversionContext = wrapperType != null ? ConversionContext.of(argumentType) : ConversionContext.of(argument);

        Optional<?> value;
        if (isPlaceholder) {
            value = applicationContext.resolvePlaceholders(stringValue).flatMap(v -> applicationContext.getConversionService().convert(v, conversionContext));
        } else {
            stringValue = substituteWildCards(stringValue);
            value = applicationContext.getProperty(stringValue, conversionContext);
            if (value.isEmpty() && cliProperty != null) {
                value = applicationContext.getProperty(cliProperty, conversionContext);
            }
        }

        if (tracer != null) {
            tracer.traceValueResolved(
                this,
                (Argument<? super Object>) argument,
                stringValue,
                value.orElse(null)
            );
        }

        if (argument.isOptional()) {
            if (value.isEmpty()) {
                return value;
            } else {
                Object convertedOptional = value.get();
                if (convertedOptional instanceof Optional) {
                    return convertedOptional;
                } else {
                    return value;
                }
            }
        } else {
            if (wrapperType != null) {
                final Object v = value.orElse(null);
                if (OptionalInt.class == wrapperType) {
                    return v instanceof Integer i ? OptionalInt.of(i) : OptionalInt.empty();
                } else if (OptionalLong.class == wrapperType) {
                    return v instanceof Long l ? OptionalLong.of(l) : OptionalLong.empty();
                } else if (OptionalDouble.class == wrapperType) {
                    return v instanceof Double d ? OptionalDouble.of(d) : OptionalDouble.empty();
                }
            }
            if (value.isPresent()) {
                return value.get();
            } else {
                if (argument.isDeclaredNullable()) {
                    return null;
                }
                String finalStringValue = stringValue;
                return argument.getAnnotationMetadata().getValue(Bindable.class, "defaultValue", argument)
                    .orElseThrow(() -> DependencyInjectionException.missingProperty(this, conversionContext, finalStringValue));
            }
        }
    }

    private String substituteWildCards(String valString) {
        ConfigurationPath configurationPath = getConfigurationPath();
        if (configurationPath.isNotEmpty()) {
            return configurationPath.resolveValue(valString);
        }
        return valString;
    }

    @NonNull
    @Override
    public <T> T getBean(@NonNull Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        T bean = context.getBean(this, beanType, qualifier);
        if (tracer != null) {
            tracer.traceBeanResolved(
                this,
                beanType,
                qualifier,
                bean
            );
            String disabledBeanMessage = context.resolveDisabledBeanMessage(
                this,
                beanType,
                qualifier
            );
            if (disabledBeanMessage != null) {
                tracer.traceBeanDisabled(
                    AbstractBeanResolutionContext.this,
                    beanType,
                    qualifier,
                    disabledBeanMessage
                );
            }
        }
        return bean;
    }

    @Override
    public <T> T getBean(Class<T> beanType, Qualifier<T> qualifier) {
        return getBean(Argument.of(beanType), qualifier);
    }

    @Override
    public <T> T getBean(BeanDefinition<T> definition) {
        return context.getBean(definition);
    }

    @NonNull
    @Override
    public <T> Collection<T> getBeansOfType(@NonNull Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        Collection<T> beans = context.getBeansOfType(this, beanType, qualifier);
        if (tracer != null) {
            traceBeanCollection(beanType, qualifier, beans);
        }
        return beans;
    }

    @Override
    public <T> Collection<T> getBeansOfType(Class<T> beanType) {
        return getBeansOfType(Argument.of(beanType));
    }

    @Override
    public <T> Collection<T> getBeansOfType(Class<T> beanType, Qualifier<T> qualifier) {
        return getBeansOfType(Argument.of(beanType), qualifier);
    }

    private <T> void traceBeanCollection(Argument<T> beanType, Qualifier<T> qualifier, Collection<T> beans) {
        for (T bean : beans) {
            tracer.traceBeanResolved(this, beanType, qualifier, bean);
        }
        String disabledBeanMessage = context.resolveDisabledBeanMessage(
            this,
            beanType,
            qualifier
        );
        if (disabledBeanMessage != null) {
            tracer.traceBeanDisabled(
                AbstractBeanResolutionContext.this,
                beanType,
                qualifier,
                disabledBeanMessage
            );
        }
    }

    @NonNull
    @Override
    public <T> Stream<T> streamOfType(@NonNull Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        return context.streamOfType(this, beanType, qualifier);
    }

    @Override
    public <T> Stream<T> streamOfType(Class<T> beanType, Qualifier<T> qualifier) {
        return streamOfType(Argument.of(beanType), qualifier);
    }

    @Override
    public <V> Map<String, V> mapOfType(Argument<V> beanType, Qualifier<V> qualifier) {
        Map<String, V> beanMap = context.mapOfType(this, beanType, qualifier);
        if (tracer != null) {
            traceBeanCollection(beanType, qualifier, beanMap.values());
        }
        return beanMap;
    }

    @NonNull
    @Override
    public <T> Optional<T> findBean(@NonNull Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        Optional<T> resolved = context.findBean(this, beanType, qualifier);
        if (tracer != null) {
            tracer.traceBeanResolved(
                this,
                beanType,
                qualifier,
                resolved.orElse(null)
            );
            String disabledBeanMessage = context.resolveDisabledBeanMessage(
                this,
                beanType,
                qualifier
            );
            if (disabledBeanMessage != null) {
                tracer.traceBeanDisabled(
                    AbstractBeanResolutionContext.this,
                    beanType,
                    qualifier,
                    disabledBeanMessage
                );
            }
        }
        return resolved;
    }

    @Override
    public <T> Optional<T> findBean(Class<T> beanType, Qualifier<T> qualifier) {
        return findBean(Argument.of(beanType), qualifier);
    }

    @Override
    public <T> Collection<T> getBeansOfType(Argument<T> beanType) {
        return context.getBeansOfType(this, beanType);
    }

    @Override
    public <T> T getProxyTargetBean(Class<T> beanType, Qualifier<T> qualifier) {
        return context.getProxyTargetBean(this, Argument.of(beanType), qualifier);
    }

    @NonNull
    @Override
    public <T> Collection<BeanRegistration<T>> getBeanRegistrations(@NonNull Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        Collection<BeanRegistration<T>> registrations = context.getBeanRegistrations(this, beanType, qualifier);
        if (tracer != null) {
            traceBeanCollection(
                beanType,
                qualifier,
                registrations.stream().map(BeanRegistration::getBean).collect(Collectors.toList())
            );
        }
        return registrations;
    }

    /**
     * Copy the state from a previous resolution context.
     *
     * @param context The previous context
     */
    public void copyStateFrom(@NonNull AbstractBeanResolutionContext context) {
        path.addAll(context.path);
        qualifier = context.qualifier;
        if (context.attributes != null) {
            getAttributesOrCreate().putAll(context.attributes);
        }
    }

    @Override
    public <T> void addDependentBean(BeanRegistration<T> beanRegistration) {
        if (beanRegistration.getBeanDefinition() == rootDefinition) {
            // Don't add self
            return;
        }
        if (dependentBeans == null) {
            dependentBeans = new ArrayList<>(3);
        }
        dependentBeans.add(beanRegistration);
    }

    @Override
    public void destroyInjectScopedBeans() {
        final CustomScope<?> injectScope = context.getCustomScopeRegistry()
            .findScope(InjectScope.class.getName())
            .orElse(null);
        if (injectScope instanceof LifeCycle<?> cycle) {
            cycle.stop();
        }
    }

    @NonNull
    @Override
    public List<BeanRegistration<?>> getAndResetDependentBeans() {
        if (dependentBeans == null) {
            return Collections.emptyList();
        }
        final List<BeanRegistration<?>> registrations = Collections.unmodifiableList(dependentBeans);
        dependentBeans = null;
        return registrations;
    }

    @Override
    public void markDependentAsFactory() {
        if (dependentBeans != null) {
            if (dependentBeans.isEmpty()) {
                return;
            }
            if (dependentBeans.size() != 1) {
                throw new IllegalStateException("Expected only one bean dependent!");
            }
            dependentFactory = dependentBeans.remove(0);
        }
    }

    @Override
    public BeanRegistration<?> getAndResetDependentFactoryBean() {
        BeanRegistration<?> result = this.dependentFactory;
        this.dependentFactory = null;
        return result;
    }

    @Override
    public List<BeanRegistration<?>> popDependentBeans() {
        List<BeanRegistration<?>> result = this.dependentBeans;
        this.dependentBeans = null;
        return result;
    }

    @Override
    public void pushDependentBeans(List<BeanRegistration<?>> dependentBeans) {
        if (this.dependentBeans != null && !this.dependentBeans.isEmpty()) {
            throw new IllegalStateException("Found existing dependent beans!");
        }
        this.dependentBeans = dependentBeans;
    }

    @Override
    public final BeanContext getContext() {
        return context;
    }

    @Override
    public final BeanDefinition getRootDefinition() {
        return rootDefinition;
    }

    @Override
    public final Path getPath() {
        return path;
    }

    @Override
    public final Object setAttribute(CharSequence key, Object value) {
        return getAttributesOrCreate().put(key, value);
    }

    /**
     * @param key The key
     * @return The attribute value
     */
    @Override
    public final Object getAttribute(CharSequence key) {
        if (attributes == null) {
            return null;
        }
        return attributes.get(key);
    }

    @Override
    public final Object removeAttribute(CharSequence key) {
        if (attributes != null && key != null) {
            return attributes.remove(key);
        }
        return null;
    }

    @Override
    public Map<CharSequence, Object> getAttributes() {
        return attributes;
    }

    @Override
    public void setAttributes(Map<CharSequence, Object> attributes) {
        this.attributes = attributes;
    }

    @Nullable
    @Override
    public Qualifier<?> getCurrentQualifier() {
        return qualifier;
    }

    @Override
    public void setCurrentQualifier(@Nullable Qualifier<?> qualifier) {
        this.qualifier = qualifier;
    }

    @Override
    public <T> Optional<T> get(CharSequence name, ArgumentConversionContext<T> conversionContext) {
        if (attributes == null) {
            return Optional.empty();
        }
        Object value = attributes.get(name);
        if (value != null && conversionContext.getArgument().getType().isInstance(value)) {
            return Optional.of((T) value);
        }
        return Optional.empty();
    }

    @Override
    public <T> Optional<T> get(CharSequence name, Class<T> requiredType) {
        if (attributes == null) {
            return Optional.empty();
        }
        Object value = attributes.get(name);
        if (requiredType.isInstance(value)) {
            return Optional.of((T) value);
        }
        return Optional.empty();
    }

    @NonNull
    private Map<CharSequence, Object> getAttributesOrCreate() {
        if (attributes == null) {
            attributes = new LinkedHashMap<>(2);
        }
        return attributes;
    }

    public final <T> Collection<BeanDefinition<T>> findBeanDefinitions(@NonNull Argument<T> argument, BeanDefinition<?> bd) {
        return context.findBeanCandidates(this, argument, bd);
    }

    public final <T> BeanRegistration<T> getBeanRegistration(Argument<T> argument, Qualifier<T> qualifier) {
        return context.getBeanRegistration(this, argument, qualifier);
    }

    @Override
    public <T> T getProxyTargetBean(BeanDefinition<T> definition, Argument<T> beanType, Qualifier<T> qualifier) {
        return context.getProxyTargetBean(this, definition, beanType, qualifier);
    }

    /**
     * Class that represents a default path.
     */
    class DefaultPath extends LinkedList<Segment<?, ?>> implements Path {

        public static final String RIGHT_ARROW = "\\---> ";
        public static final String RIGHT_ARROW_EMOJI = " ↪️  ";
        private static final String CIRCULAR_ERROR_MSG = "Circular dependency detected";

        DefaultPath() {
        }

        @Override
        public String toConsoleString(boolean ansiSupported) {
            Iterator<Segment<?, ?>> i = descendingIterator();
            String ls = CachedEnvironment.getProperty("line.separator");
            StringBuilder pathString = new StringBuilder().append(ls);

            String spaces = "";
            while (i.hasNext()) {
                pathString.append(i.next().toString());
                if (i.hasNext()) {
                    pathString
                        .append(ls)
                        .append(spaces)
                        .append(ansiSupported ? RIGHT_ARROW_EMOJI : RIGHT_ARROW);
                    spaces += "      ";
                }
            }
            return pathString.toString();
        }

        @Override
        public String toString() {
            return toConsoleString(false);
        }

        @Override
        public String toCircularString() {
            return toConsoleCircularString(false);
        }

        @SuppressWarnings("MagicNumber")
        @Override
        public String toConsoleCircularString(boolean ansiSupported) {
            Iterator<Segment<?, ?>> i = descendingIterator();
            StringBuilder pathString = new StringBuilder();
            String ls = CachedEnvironment.getProperty("line.separator");

            // Try finding an actual cycle, cycleI is index where the cycle starts
            int cycleIndex = lastIndexOf(iterator().next());
            if (cycleIndex > 0) {
                cycleIndex = size() - cycleIndex;
            } else {
                cycleIndex = 0;
            }

            String spaces = "";
            int index = 0;
            // The last element ends the cycle and is repeated in the path, so we skip it
            // and point to an already present element instead
            while (i.hasNext() && index < size() - 1) {
                String segmentString = i.next().toString();
                if (index == cycleIndex) {
                    pathString.append(ls).append(spaces).append("^").append("  ")
                        .append(ansiSupported ? RIGHT_ARROW_EMOJI : RIGHT_ARROW);
                    spaces = spaces + "|  ";
                } else if (index != 0) {
                    pathString
                        .append(ls)
                        .append(spaces)
                        .append(ansiSupported ? RIGHT_ARROW_EMOJI : RIGHT_ARROW);
                }
                pathString.append(segmentString);
                spaces = spaces + "      ";
                ++index;
            }

            String dashes = String.join("", Collections.nCopies(spaces.length() - spaces.indexOf("|") - 1, "-"));
            pathString
                .append(ls).append(spaces).append("|")
                .append(ls).append(spaces, 0, spaces.indexOf("|"))
                .append("+").append(dashes).append("+");

            return pathString.toString();
        }

        @Override
        public Optional<Segment<?, ?>> currentSegment() {
            return Optional.ofNullable(peek());
        }

        @Override
        public Path pushConstructorResolve(BeanDefinition declaringType, Argument argument) {
            ConstructorInjectionPoint<?> constructor = declaringType.getConstructor();
            if (constructor instanceof MethodInjectionPoint<?, ?> methodInjectionPoint) {
                return pushConstructorResolve(declaringType, methodInjectionPoint.getName(), argument, constructor.getArguments());
            }
            return pushConstructorResolve(declaringType, CONSTRUCTOR_METHOD_NAME, argument, constructor.getArguments());
        }

        @Override
        public Path pushConstructorResolve(BeanDefinition declaringType, String methodName, Argument argument, Argument[] arguments) {
            try {
                if (CONSTRUCTOR_METHOD_NAME.equals(methodName)) {
                    ConstructorSegment constructorSegment = new ConstructorArgumentSegment(declaringType, (Qualifier<Object>) getCurrentQualifier(), methodName, argument, arguments);
                    detectCircularDependency(declaringType, argument, constructorSegment);
                } else {
                    Segment<?, ?> previous = peek();
                    MethodSegment<?, ?> methodSegment = new MethodArgumentSegment(declaringType, (Qualifier<Object>) getCurrentQualifier(), methodName, argument, arguments, previous instanceof MethodSegment ms ? ms : null);
                    if (contains(methodSegment)) {
                        push(methodSegment);
                        throw new CircularDependencyException(AbstractBeanResolutionContext.this, argument, CIRCULAR_ERROR_MSG);
                    } else {
                        push(methodSegment);
                    }
                }
            } finally {
                traceResolution();
            }
            return this;
        }

        @Override
        public Path pushBeanCreate(BeanDefinition<?> declaringType, Argument<?> beanType) {
            if (tracer != null) {
                tracer.traceBeanCreation(
                    AbstractBeanResolutionContext.this,
                    declaringType,
                    beanType
                );
            }
            return pushConstructorResolve(declaringType, beanType);
        }

        private void traceResolution() {
            if (tracer != null) {
                getPath().currentSegment().ifPresent(segment -> tracer.traceInjectBean(
                    AbstractBeanResolutionContext.this,
                    segment
                ));
            }
        }

        @Override
        public Path pushMethodArgumentResolve(BeanDefinition declaringType, MethodInjectionPoint methodInjectionPoint, Argument argument) {
            try {
                Segment<?, ?> previous = peek();
                MethodSegment<?, ?> methodSegment = new MethodArgumentSegment(declaringType, (Qualifier<Object>) getCurrentQualifier(), methodInjectionPoint.getName(), argument,
                    methodInjectionPoint.getArguments(), previous instanceof MethodSegment ms ? ms : null);
                if (contains(methodSegment)) {
                    push(methodSegment);
                    throw new CircularDependencyException(AbstractBeanResolutionContext.this, methodInjectionPoint, argument, CIRCULAR_ERROR_MSG);
                } else {
                    push(methodSegment);
                }
            } finally {
                traceResolution();
            }

            return this;
        }

        @Override
        public Path pushMethodArgumentResolve(BeanDefinition declaringType, String methodName, Argument argument, Argument[] arguments) {
            try {
                Segment<?, ?> previous = peek();
                MethodSegment<?, ?> methodSegment = new MethodArgumentSegment(declaringType, (Qualifier<Object>) getCurrentQualifier(), methodName, argument, arguments, previous instanceof MethodSegment ms ? ms : null);
                if (contains(methodSegment)) {
                    push(methodSegment);
                    throw new CircularDependencyException(AbstractBeanResolutionContext.this, declaringType, methodName, argument, CIRCULAR_ERROR_MSG);
                } else {
                    push(methodSegment);
                }
            } finally {
                traceResolution();
            }

            return this;
        }

        @Override
        public Path pushEventListenerResolve(BeanDefinition<?> declaringType, Argument<?> eventType) {
            try {
                EventListenerSegment<?, ?> segment = new EventListenerSegment<>(
                    declaringType,
                    eventType
                );
                if (contains(segment)) {
                    push(segment);
                    throw new CircularDependencyException(
                        AbstractBeanResolutionContext.this,
                        eventType,
                        CIRCULAR_ERROR_MSG
                    );
                } else {
                    push(segment);
                }
            } finally {
                traceResolution();
            }

            return this;
        }

        @Override
        public Path pushFieldResolve(BeanDefinition declaringType, FieldInjectionPoint fieldInjectionPoint) {
            try {
                FieldSegment<?, ?> fieldSegment = new FieldSegment<>(declaringType, getCurrentQualifier(), fieldInjectionPoint.asArgument());
                if (contains(fieldSegment)) {
                    push(fieldSegment);
                    throw new CircularDependencyException(AbstractBeanResolutionContext.this, fieldInjectionPoint, CIRCULAR_ERROR_MSG);
                } else {
                    push(fieldSegment);
                }
            } finally {
                traceResolution();
            }
            return this;
        }

        @Override
        public Path pushFieldResolve(BeanDefinition declaringType, Argument fieldAsArgument) {
            try {
                FieldSegment<?, ?> fieldSegment = new FieldSegment<>(declaringType, getCurrentQualifier(), fieldAsArgument);
                if (contains(fieldSegment)) {
                    push(fieldSegment);
                    throw new CircularDependencyException(AbstractBeanResolutionContext.this, declaringType, fieldAsArgument.getName(), CIRCULAR_ERROR_MSG);
                } else {
                    push(fieldSegment);
                }
            } finally {
                traceResolution();
            }
            return this;
        }

        @Override
        public Path pushAnnotationResolve(BeanDefinition beanDefinition, Argument annotationMemberBeanAsArgument) {
            try {
                AnnotationSegment annotationSegment = new AnnotationSegment(beanDefinition, getCurrentQualifier(), annotationMemberBeanAsArgument);
                if (contains(annotationSegment)) {
                    push(annotationSegment);
                    throw new CircularDependencyException(AbstractBeanResolutionContext.this, beanDefinition, annotationMemberBeanAsArgument.getName(), CIRCULAR_ERROR_MSG);
                } else {
                    push(annotationSegment);
                }
            } finally {
                traceResolution();
            }
            return this;
        }

        private void detectCircularDependency(BeanDefinition declaringType, Argument argument, Segment constructorSegment) {
            if (contains(constructorSegment)) {
                Segment last = peek();
                if (last != null) {

                    BeanDefinition declaringBean = last.getDeclaringType();
                    // if the currently injected segment is a constructor argument and the type to be constructed is the
                    // same as the candidate, then filter out the candidate to avoid a circular injection problem
                    if (!declaringBean.equals(declaringType)) {
                        if (declaringType instanceof ProxyBeanDefinition<?> proxyBeanDefinition) {
                            // take into account proxies
                            if (!proxyBeanDefinition.getTargetDefinitionType().equals(declaringBean.getClass())) {
                                push(constructorSegment);
                                throw new CircularDependencyException(AbstractBeanResolutionContext.this, argument, CIRCULAR_ERROR_MSG);
                            } else {
                                push(constructorSegment);
                            }
                        } else if (declaringBean instanceof ProxyBeanDefinition<?> proxyBeanDefinition) {
                            // take into account proxies
                            if (!proxyBeanDefinition.getTargetDefinitionType().equals(declaringType.getClass())) {
                                push(constructorSegment);
                                throw new CircularDependencyException(AbstractBeanResolutionContext.this, argument, CIRCULAR_ERROR_MSG);
                            } else {
                                push(constructorSegment);
                            }
                        } else {
                            push(constructorSegment);
                            throw new CircularDependencyException(AbstractBeanResolutionContext.this, argument, CIRCULAR_ERROR_MSG);
                        }
                    } else {
                        push(constructorSegment);
                    }
                } else {
                    push(constructorSegment);
                    throw new CircularDependencyException(AbstractBeanResolutionContext.this, argument, CIRCULAR_ERROR_MSG);
                }
            } else {
                push(constructorSegment);
            }
        }

        @Override
        public void close() {
            if (tracer != null) {
                if (isEmpty()) {
                    tracer.traceBeanCreated(
                        AbstractBeanResolutionContext.this,
                        rootDefinition
                    );
                } else {
                    Segment<?, ?> segment = peek();
                    if (segment != null) {
                        tracer.traceInjectComplete(
                            AbstractBeanResolutionContext.this,
                            segment
                        );
                    }
                }
            }
            Path.super.close();
        }
    }

    /**
     * A segment that represents a method argument.
     */
    public static final class ConstructorArgumentSegment extends ConstructorSegment implements ArgumentInjectionPoint<Object, Object> {
        public ConstructorArgumentSegment(BeanDefinition<Object> declaringType, Qualifier<Object> qualifier, String methodName, Argument<Object> argument, Argument<Object>[] arguments) {
            super(declaringType, qualifier, methodName, argument, arguments);
        }

        @Override
        public BeanDefinition<Object> getDeclaringBean() {
            return getDeclaringType();
        }

        @Override
        public Qualifier<Object> getDeclaringBeanQualifier() {
            return getDeclaringTypeQualifier();
        }

    }

    /**
     * A segment that represents a constructor.
     */
    public static class ConstructorSegment extends AbstractSegment<Object, Object> implements ArgumentInjectionPoint<Object, Object> {

        private static final String ANN_ADAPTER = "io.micronaut.aop.Adapter";
        private final String methodName;
        private final Argument<Object>[] arguments;

        /**
         * @param declaringBeanDefinition The declaring class
         * @param qualifier               The qualifier
         * @param methodName              The methodName
         * @param argument                The argument
         * @param arguments               The arguments
         */
        ConstructorSegment(BeanDefinition<Object> declaringBeanDefinition, Qualifier<Object> qualifier, String methodName, Argument<Object> argument, Argument<Object>[] arguments) {
            super(declaringBeanDefinition, qualifier, declaringBeanDefinition.getBeanType().getName(), argument);
            this.methodName = methodName;
            this.arguments = arguments;
        }

        @Override
        public String toString() {
            return toConsoleString(false);
        }

        @NonNull
        public String toConsoleString(boolean ansiSupported) {
            StringBuilder baseString;
            BeanDefinition<Object> declaringType = getDeclaringType();
            TypeInformation<Object> typeInformation = declaringType.getTypeInformation();
            if (declaringType.hasDeclaredStereotype(ANN_ADAPTER)) {
                ExecutableMethod<Object, ?> method = declaringType.getExecutableMethods().iterator().next();
                // Not great, but to produce accurate debug output we have to reach into AOP internals
                Class<?> beanType = method.classValue(ANN_ADAPTER, "adaptedBean").orElse(declaringType.getBeanType());
                String beanMethod = method.stringValue(ANN_ADAPTER, "adaptedMethod").orElse("unknown");
                baseString = new StringBuilder(TypeFormat.getBeanTypeString(
                    ansiSupported ? TypeFormat.ANSI_SHORTENED : TypeFormat.SHORTENED,
                    beanType,
                    declaringType.asArgument().getTypeVariables(),
                    declaringType.getAnnotationMetadata()
                )).append(MEMBER_SEPARATOR);
                baseString.append(beanMethod);
            } else if (CONSTRUCTOR_METHOD_NAME.equals(methodName)) {
                baseString = new StringBuilder(
                    ansiSupported ? AnsiColour.magentaBold("new ") : "new "
                );
                baseString.append(typeInformation.getBeanTypeString(
                    ansiSupported ? TypeFormat.ANSI_SHORTENED : TypeFormat.SHORTENED
                ));
            } else {
                baseString = new StringBuilder(typeInformation.getBeanTypeString(
                    ansiSupported ? TypeFormat.ANSI_SHORTENED : TypeFormat.SHORTENED
                )).append(MEMBER_SEPARATOR);
                baseString.append(methodName);
            }
            outputArguments(baseString, arguments, ansiSupported);
            return baseString.toString();
        }

        @Override
        public InjectionPoint<Object> getInjectionPoint() {
            return this;
        }

        @NonNull
        @Override
        public CallableInjectionPoint<Object> getOuterInjectionPoint() {
            return getDeclaringType().getConstructor();
        }

        @Override
        public BeanDefinition<Object> getDeclaringBean() {
            return ConstructorSegment.this.getDeclaringType();
        }

        @Override
        public Qualifier<Object> getDeclaringBeanQualifier() {
            return ConstructorSegment.this.getDeclaringTypeQualifier();
        }

        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return getArgument().getAnnotationMetadata();
        }

    }

    /**
     * A segment that represents a method argument.
     */
    public static final class MethodArgumentSegment extends MethodSegment<Object, Object> implements ArgumentInjectionPoint<Object, Object> {
        private final MethodSegment<Object, Object> outer;

        public MethodArgumentSegment(BeanDefinition<Object> declaringType,
                                     Qualifier<Object> qualifier,
                                     String methodName,
                                     Argument<Object> argument,
                                     Argument<Object>[] arguments,
                                     MethodSegment<Object, Object> outer) {
            super(declaringType, qualifier, methodName, argument, arguments);
            this.outer = outer;
        }

        @Override
        public CallableInjectionPoint<Object> getOuterInjectionPoint() {
            if (outer == null) {
                throw new IllegalStateException("Outer argument inaccessible");
            }
            return outer;
        }

        @Override
        public String toString() {
            return toConsoleString(false);
        }

        @Override
        public String toConsoleString(boolean ansiSupported) {
            BeanDefinition<?> declaringBean = getDeclaringBean();
            if (declaringBean.hasAnnotation(Factory.class)) {
                String beanDescription = declaringBean.getBeanDescription(
                    ansiSupported ? TypeFormat.ANSI_SHORTENED : TypeFormat.SHORTENED,
                    false
                );

                var baseString = new StringBuilder(beanDescription);
                String methodName = getName();
                if (!CONSTRUCTOR_METHOD_NAME.equals(methodName)) {
                    String memberSeparator = ansiSupported ? AnsiColour.CYAN_BOLD + MEMBER_SEPARATOR + AnsiColour.RESET : MEMBER_SEPARATOR;
                    baseString.append(memberSeparator);
                    baseString.append(methodName);
                }

                outputArguments(baseString, getArguments(), ansiSupported);
                return baseString.toString();
            } else {
                return super.toConsoleString(ansiSupported);
            }
        }
    }

    /**
     * Represents a segment that is an event listener.
     *
     * @param <B> The bean type
     * @param <T> The event type
     */
    public static class EventListenerSegment<B, T> extends AbstractSegment<B, T> implements CallableInjectionPoint<B> {
        /**
         * @param declaringClass The declaring class
         * @param eventType      The argument
         */
        EventListenerSegment(
            BeanDefinition<B> declaringClass,
            Argument<T> eventType) {
            super(declaringClass, null, eventType.getName(), eventType);
        }

        @Override
        public String toConsoleString(boolean ansiSupported) {
            if (ansiSupported) {
                String event = getArgument().getTypeString(TypeFormat.ANSI_SIMPLE);
                return event + " ➡️  " +
                    getDeclaringBean().getBeanDescription(TypeFormat.ANSI_SHORTENED);
            } else {
                String event = getArgument().getTypeString(TypeFormat.SIMPLE);
                return event + " -> " +
                    getDeclaringBean().getBeanDescription(TypeFormat.SHORTENED);
            }
        }

        @Override
        public InjectionPoint<B> getInjectionPoint() {
            return this;
        }

        @Override
        public Argument<?>[] getArguments() {
            return new Argument[]{getArgument()};
        }

        @Override
        public BeanDefinition<B> getDeclaringBean() {
            return getDeclaringType();
        }
    }

    /**
     * A segment that represents a method.
     */
    public static class MethodSegment<B, T> extends AbstractSegment<B, T> implements CallableInjectionPoint<B> {

        private final Argument<Object>[] arguments;

        /**
         * @param declaringType The declaring type
         * @param qualifier     The qualifier
         * @param methodName    The method name
         * @param argument      The argument
         * @param arguments     The arguments
         */
        MethodSegment(BeanDefinition<B> declaringType, Qualifier<B> qualifier, String methodName, Argument<T> argument, Argument<Object>[] arguments) {
            super(declaringType, qualifier, methodName, argument);
            this.arguments = arguments;
        }

        @Override
        public String toString() {
            return toConsoleString(false);
        }

        @Override
        public String toConsoleString(boolean ansiSupported) {
            StringBuilder baseString = new StringBuilder(
                getDeclaringType().getTypeInformation().getBeanTypeString(
                    ansiSupported ? TypeFormat.ANSI_SHORTENED : TypeFormat.SHORTENED
                )
            );
            String memberSeparator = ansiSupported ? AnsiColour.CYAN_BOLD + MEMBER_SEPARATOR + AnsiColour.RESET : MEMBER_SEPARATOR;
            baseString.append(memberSeparator);
            baseString.append(getName());
            outputArguments(baseString, arguments, ansiSupported);
            return baseString.toString();
        }

        @Override
        public InjectionPoint<B> getInjectionPoint() {
            return this;
        }

        @Override
        public BeanDefinition<B> getDeclaringBean() {
            return getDeclaringType();
        }

        @Override
        public Argument<?>[] getArguments() {
            return arguments;
        }

        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return getArgument().getAnnotationMetadata();
        }

        @Override
        public Qualifier<B> getDeclaringBeanQualifier() {
            return getDeclaringTypeQualifier();
        }
    }

    /**
     * A segment that represents a field.
     */
    public static final class FieldSegment<B, T> extends AbstractSegment<B, T> implements InjectionPoint<B>, ArgumentCoercible<T>, ArgumentInjectionPoint<B, T> {

        /**
         * @param declaringClass The declaring class
         * @param qualifier      The qualifier
         * @param argument       The argument
         */
        FieldSegment(BeanDefinition<B> declaringClass, Qualifier<B> qualifier, Argument<T> argument) {
            super(declaringClass, qualifier, argument.getName(), argument);
        }

        @Override
        public String toString() {
            return toConsoleString(false);
        }

        @Override
        public String toConsoleString(boolean ansiSupported) {
            String beanDescription = getDeclaringType().getBeanDescription(
                ansiSupported ? TypeFormat.ANSI_SHORTENED : TypeFormat.SHORTENED,
                false
            );
            StringBuilder baseString = new StringBuilder(beanDescription);
            String memberSeparator = ansiSupported ? AnsiColour.CYAN_BOLD + MEMBER_SEPARATOR + AnsiColour.RESET : MEMBER_SEPARATOR;
            baseString.append(memberSeparator);
            baseString.append(getName());

            return baseString.toString();
        }

        @Override
        public InjectionPoint<B> getInjectionPoint() {
            return this;
        }

        @Override
        public BeanDefinition<B> getDeclaringBean() {
            return getDeclaringType();
        }

        @Override
        public CallableInjectionPoint<B> getOuterInjectionPoint() {
            throw new UnsupportedOperationException("Outer injection point not retrievable from here");
        }

        @Override
        public Argument<T> asArgument() {
            return getArgument();
        }

        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return getArgument().getAnnotationMetadata();
        }

        @Override
        public Qualifier<B> getDeclaringBeanQualifier() {
            return getDeclaringTypeQualifier();
        }
    }

    /**
     * A segment that represents annotation.
     *
     * @since 3.3.0
     */
    public static final class AnnotationSegment<B> extends AbstractSegment<B, B> implements InjectionPoint<B> {

        /**
         * @param beanDefinition The bean definition
         * @param qualifier      The qualifier
         * @param argument       The argument
         */
        AnnotationSegment(BeanDefinition<B> beanDefinition, Qualifier<B> qualifier, Argument<B> argument) {
            super(beanDefinition, qualifier, argument.getName(), argument);
        }

        @Override
        public String toString() {
            return getName();
        }

        @Override
        public InjectionPoint<B> getInjectionPoint() {
            return this;
        }

        @Override
        public BeanDefinition<B> getDeclaringBean() {
            return getDeclaringType();
        }

        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return getArgument().getAnnotationMetadata();
        }

        @Override
        public Qualifier<B> getDeclaringBeanQualifier() {
            return getDeclaringTypeQualifier();
        }
    }

    /**
     * Abstract class for a Segment.
     */
    protected abstract static class AbstractSegment<B, T> implements Segment<B, T>, Named {

        /**
         * The separator between a type and its member when printing to user.
         */
        protected static final String MEMBER_SEPARATOR = "#";

        private final BeanDefinition<B> declaringComponent;
        @Nullable
        private final Qualifier<B> qualifier;
        private final String name;
        private final Argument<T> argument;

        /**
         * @param declaringClass The declaring class
         * @param qualifier      The qualifier
         * @param name           The name
         * @param argument       The argument
         */
        AbstractSegment(BeanDefinition<B> declaringClass, Qualifier<B> qualifier, String name, Argument<T> argument) {
            this.declaringComponent = declaringClass;
            this.qualifier = qualifier;
            this.name = name;
            this.argument = argument;
        }

        /**
         * A common method for retrieving a name for type. The default behavior is to use the shortened type name.
         *
         * @param type The type
         * @return The name to be shown to user
         */
        protected String getTypeName(Class<?> type) {
            if (InterceptedBean.class.isAssignableFrom(type)) {
                Class<?>[] interfaces = type.getInterfaces();
                Set<String> interfaceNames = Arrays.stream(interfaces)
                    .map(Class::getName)
                    .collect(Collectors.toSet());
                if (type.isInterface() && interfaceNames.contains("io.micronaut.aop.Introduced")) {
                    return NameUtils.getShortenedName(
                        interfaces[0].getTypeName()
                    );
                } else {
                    return NameUtils.getShortenedName(
                        type.getSuperclass().getTypeName()
                    );
                }
            } else {
                return NameUtils.getShortenedName(type.getTypeName());
            }
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public BeanDefinition<B> getDeclaringType() {
            return declaringComponent;
        }

        @Override
        public Qualifier<B> getDeclaringTypeQualifier() {
            return qualifier == null ? declaringComponent.getDeclaredQualifier() : qualifier;
        }

        @Override
        public Argument<T> getArgument() {
            return argument;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            AbstractSegment that = (AbstractSegment) o;

            return declaringComponent.equals(that.declaringComponent) && name.equals(that.name) && argument.equals(that.argument);
        }

        @Override
        public int hashCode() {
            return ObjectUtils.hash(declaringComponent, name, argument);
        }

        /**
         * @param baseString    The base string
         * @param arguments     The arguments
         * @param ansiSupported Whether ANSI colour is supported
         */
        void outputArguments(StringBuilder baseString, Argument[] arguments, boolean ansiSupported) {
            baseString.append(ansiSupported ? AnsiColour.brightCyan("(") : "(");
            for (int i = 0; i < arguments.length; i++) {
                Argument<?> argument = arguments[i];
                boolean isInjectedArgument = argument.equals(getArgument());
                if (isInjectedArgument) {
                    if (ansiSupported) {
                        baseString.append(AnsiColour.BLUE_UNDERLINED);
                    }
                    baseString.append('[');
                }
                String beanTypeString = argument.getBeanTypeString(
                    ansiSupported && !isInjectedArgument ? TypeFormat.ANSI_SIMPLE : TypeFormat.SIMPLE
                );
                baseString.append(beanTypeString)
                    .append(' ')
                    .append(ansiSupported && !isInjectedArgument ? AnsiColour.brightBlue(argument.getName()) : argument.getName());
                if (isInjectedArgument) {
                    baseString.append(']');
                    if (ansiSupported) {
                        baseString.append(AnsiColour.RESET);
                    }
                }

                if (i != arguments.length - 1) {
                    Argument<?> next = arguments[i + 1];
                    if (getDeclaringType().getBeanType().isSynthetic() &&
                        next.getName().startsWith("$")) {
                        // skip synthetic arguments
                        break;
                    }
                    baseString.append(", ");
                }
            }
            baseString.append(ansiSupported ? AnsiColour.brightCyan(")") : ")");

        }
    }
}
