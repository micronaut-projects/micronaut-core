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

import io.micronaut.context.exceptions.CircularDependencyException;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.naming.Named;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ArgumentCoercible;
import io.micronaut.inject.*;

import java.util.*;

/**
 * Default implementation of the {@link BeanResolutionContext} interface.
 *
 * @author Graeme Rocher
 * @since 1.2.3
 */
@Internal
public abstract class AbstractBeanResolutionContext implements BeanResolutionContext {

    protected final BeanContext context;
    protected final BeanDefinition rootDefinition;
    private final Path path;
    private Map<CharSequence, Object> attributes;
    private Qualifier<?> qualifier;
    private List<BeanRegistration<?>> dependentBeans;

    /**
     * @param context        The bean context
     * @param rootDefinition The bean root definition
     */
    @Internal
    public AbstractBeanResolutionContext(BeanContext context, BeanDefinition rootDefinition) {
        this.context = context;
        this.rootDefinition = rootDefinition;
        this.path = new DefaultPath();
    }

    /**
     * Copy the state from a previous resolution context.
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
    public <T> void addDependentBean(BeanIdentifier identifier, BeanDefinition<T> definition, T bean) {
        if (dependentBeans == null) {
            dependentBeans = new ArrayList<>(3);
        }
        dependentBeans.add(new BeanRegistration<>(identifier, definition, bean));
    }

    @NonNull
    @Override
    public List<BeanRegistration<?>> getAndResetDependentBeans() {
        if (dependentBeans == null) {
            return Collections.emptyList();
        }
        final List<BeanRegistration<?>> registrations = Collections.unmodifiableList(new ArrayList<>(dependentBeans));
        dependentBeans.clear();
        return registrations;
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

    /**
     * Class that represents a default path.
     */
    class DefaultPath extends LinkedList<Segment<?>> implements Path {

        public static final String RIGHT_ARROW = " --> ";

        /**
         * Default constructor.
         */
        DefaultPath() {
        }

        @Override
        public String toString() {
            Iterator<Segment<?>> i = descendingIterator();
            StringBuilder path = new StringBuilder();
            while (i.hasNext()) {
                path.append(i.next().toString());
                if (i.hasNext()) {
                    path.append(RIGHT_ARROW);
                }
            }
            return path.toString();
        }

        @SuppressWarnings("MagicNumber")
        @Override
        public String toCircularString() {
            Iterator<Segment<?>> i = descendingIterator();
            StringBuilder path = new StringBuilder();
            String ls = System.getProperty("line.separator");
            while (i.hasNext()) {
                String segmentString = i.next().toString();
                path.append(segmentString);
                if (i.hasNext()) {
                    path.append(RIGHT_ARROW);
                } else {
                    int totalLength = path.length() - 3;
                    String spaces = String.join("", Collections.nCopies(totalLength, " "));
                    path.append(ls)
                        .append("^")
                        .append(spaces)
                        .append("|")
                        .append(ls)
                        .append("|")
                        .append(spaces)
                        .append("|").append(ls)
                        .append("|")
                        .append(spaces)
                        .append("|").append(ls).append('+');
                    path.append(String.join("", Collections.nCopies(totalLength, "-"))).append('+');
                }
            }
            return path.toString();
        }

        @Override
        public Optional<Segment<?>> currentSegment() {
            return Optional.ofNullable(peek());
        }

        @Override
        public Path pushConstructorResolve(BeanDefinition declaringType, Argument argument) {
            ConstructorInjectionPoint constructor = declaringType.getConstructor();
            if (constructor instanceof MethodInjectionPoint) {
                MethodInjectionPoint<?, ?> methodInjectionPoint = (MethodInjectionPoint<?, ?>) constructor;
                return pushConstructorResolve(declaringType, methodInjectionPoint.getName(), argument, constructor.getArguments(), constructor.requiresReflection());
            }
            return pushConstructorResolve(declaringType, "<init>", argument, constructor.getArguments(), constructor.requiresReflection());
        }

        @Override
        public Path pushConstructorResolve(BeanDefinition declaringType, String methodName, Argument argument, Argument[] arguments, boolean requiresReflection) {
            if ("<init>".equals(methodName)) {
                ConstructorSegment constructorSegment = new ConstructorArgumentSegment(declaringType, methodName, argument, arguments);
                detectCircularDependency(declaringType, argument, constructorSegment);
            } else {
                Segment<?> previous = peek();
                MethodSegment methodSegment = new MethodArgumentSegment(declaringType, methodName, argument, arguments, requiresReflection, previous instanceof MethodSegment ? (MethodSegment) previous : null);
                if (contains(methodSegment)) {
                    throw new CircularDependencyException(AbstractBeanResolutionContext.this, argument, "Circular dependency detected");
                } else {
                    path.push(methodSegment);
                }
            }
            return this;
        }

        @Override
        public Path pushBeanCreate(BeanDefinition<?> declaringType, Argument<?> beanType) {
            return pushConstructorResolve(declaringType, beanType);
        }

        @Override
        public Path pushMethodArgumentResolve(BeanDefinition declaringType, MethodInjectionPoint methodInjectionPoint, Argument argument) {
            Segment<?> previous = peek();
            MethodSegment methodSegment = new MethodArgumentSegment(declaringType, methodInjectionPoint.getName(), argument,
                    methodInjectionPoint.getArguments(), methodInjectionPoint.requiresReflection(), previous instanceof MethodSegment ? (MethodSegment) previous : null);
            if (contains(methodSegment)) {
                throw new CircularDependencyException(AbstractBeanResolutionContext.this, methodInjectionPoint, argument, "Circular dependency detected");
            } else {
                push(methodSegment);
            }

            return this;
        }

        @Override
        public Path pushMethodArgumentResolve(BeanDefinition declaringType, String methodName, Argument argument, Argument[] arguments, boolean requiresReflection) {
            Segment<?> previous = peek();
            MethodSegment methodSegment = new MethodArgumentSegment(declaringType, methodName, argument, arguments, requiresReflection, previous instanceof MethodSegment ? (MethodSegment) previous : null);
            if (contains(methodSegment)) {
                throw new CircularDependencyException(AbstractBeanResolutionContext.this, declaringType, methodName, argument, "Circular dependency detected");
            } else {
                push(methodSegment);
            }

            return this;
        }

        @Override
        public Path pushFieldResolve(BeanDefinition declaringType, FieldInjectionPoint fieldInjectionPoint) {
            FieldSegment fieldSegment = new FieldSegment(declaringType, fieldInjectionPoint.asArgument(), fieldInjectionPoint.requiresReflection());
            if (contains(fieldSegment)) {
                throw new CircularDependencyException(AbstractBeanResolutionContext.this, fieldInjectionPoint, "Circular dependency detected");
            } else {
                push(fieldSegment);
            }
            return this;
        }

        @Override
        public Path pushFieldResolve(BeanDefinition declaringType, Argument fieldAsArgument, boolean requiresReflection) {
            FieldSegment fieldSegment = new FieldSegment(declaringType, fieldAsArgument, requiresReflection);
            if (contains(fieldSegment)) {
                throw new CircularDependencyException(AbstractBeanResolutionContext.this, declaringType, fieldAsArgument.getName(), "Circular dependency detected");
            } else {
                push(fieldSegment);
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
                        if (declaringType instanceof ProxyBeanDefinition) {
                            // take into account proxies
                            if (!((ProxyBeanDefinition) declaringType).getTargetDefinitionType().equals(declaringBean.getClass())) {
                                throw new CircularDependencyException(AbstractBeanResolutionContext.this, argument, "Circular dependency detected");
                            } else {
                                push(constructorSegment);
                            }
                        } else if (declaringBean instanceof ProxyBeanDefinition) {
                            // take into account proxies
                            if (!((ProxyBeanDefinition) declaringBean).getTargetDefinitionType().equals(declaringType.getClass())) {
                                throw new CircularDependencyException(AbstractBeanResolutionContext.this, argument, "Circular dependency detected");
                            } else {
                                push(constructorSegment);
                            }
                        } else {
                            throw new CircularDependencyException(AbstractBeanResolutionContext.this, argument, "Circular dependency detected");
                        }
                    } else {
                        push(constructorSegment);
                    }
                } else {
                    throw new CircularDependencyException(AbstractBeanResolutionContext.this, argument, "Circular dependency detected");
                }
            } else {
                push(constructorSegment);
            }
        }
    }

    /**
     * A segment that represents a method argument.
     */
    public static class ConstructorArgumentSegment extends ConstructorSegment implements ArgumentInjectionPoint {
        public ConstructorArgumentSegment(BeanDefinition declaringType, String methodName, Argument argument, Argument[] arguments) {
            super(declaringType, methodName, argument, arguments);
        }

        @Override
        public CallableInjectionPoint getOuterInjectionPoint() {
            throw new UnsupportedOperationException("Outer injection point inaccessible from here");
        }

        @Override
        public BeanDefinition getDeclaringBean() {
            return getDeclaringType();
        }

        @Override
        public boolean requiresReflection() {
            return false;
        }
    }

    /**
     * A segment that represents a constructor.
     */
    public static class ConstructorSegment extends AbstractSegment {

        private final String methodName;
        private final Argument[] arguments;
        private final BeanDefinition declaringClass;

        /**
         * @param declaringClass The declaring class
         * @param methodName     The methodName
         * @param argument       The argument
         * @param arguments      The arguments
         */
        ConstructorSegment(BeanDefinition declaringClass, String methodName, Argument argument, Argument[] arguments) {
            super(declaringClass, declaringClass.getBeanType().getName(), argument);
            this.methodName = methodName;
            this.arguments = arguments;
            this.declaringClass = declaringClass;
        }

        @Override
        public String toString() {
            StringBuilder baseString;
            if ("<init>".equals(methodName)) {
                baseString = new StringBuilder("new ");
                baseString.append(getDeclaringType().getBeanType().getSimpleName());
            } else {
                baseString = new StringBuilder(getDeclaringType().getBeanType().getSimpleName()).append('.');
                baseString.append(methodName);
            }
            outputArguments(baseString, arguments);
            return baseString.toString();
        }

        @Override
        public InjectionPoint getInjectionPoint() {
            ConstructorInjectionPoint constructorInjectionPoint = getDeclaringType().getConstructor();
            return new ArgumentInjectionPoint() {
                @NonNull
                @Override
                public CallableInjectionPoint getOuterInjectionPoint() {
                    return constructorInjectionPoint;
                }

                @NonNull
                @Override
                public Argument getArgument() {
                    return ConstructorSegment.this.getArgument();
                }

                @Override
                public BeanDefinition getDeclaringBean() {
                    return constructorInjectionPoint.getDeclaringBean();
                }

                @Override
                public boolean requiresReflection() {
                    return constructorInjectionPoint.requiresReflection();
                }

                @Override
                public AnnotationMetadata getAnnotationMetadata() {
                    return getArgument().getAnnotationMetadata();
                }
            };
        }

    }

    /**
     * A segment that represents a method argument.
     */
    public static class MethodArgumentSegment extends MethodSegment implements ArgumentInjectionPoint {
        private final MethodSegment outer;

        public MethodArgumentSegment(BeanDefinition declaringType, String methodName, Argument argument, Argument[] arguments, boolean requiresReflection, MethodSegment outer) {
            super(declaringType, methodName, argument, arguments, requiresReflection);
            this.outer = outer;
        }

        @Override
        public CallableInjectionPoint getOuterInjectionPoint() {
            if (outer == null) {
                throw new IllegalStateException("Outer argument inaccessible");
            }
            return outer;
        }
    }

    /**
     * A segment that represents a method.
     */
    public static class MethodSegment extends AbstractSegment implements CallableInjectionPoint {

        private final Argument[] arguments;
        private final boolean requiresReflection;

        /**
         * @param declaringType        The declaring type
         * @param methodName           The method name
         * @param argument             The argument
         * @param arguments            The arguments
         * @param requiresReflection   Is requires reflection
         */
        MethodSegment(BeanDefinition declaringType, String methodName, Argument argument, Argument[] arguments, boolean requiresReflection) {
            super(declaringType, methodName, argument);
            this.arguments = arguments;
            this.requiresReflection = requiresReflection;
        }

        @Override
        public String toString() {
            StringBuilder baseString = new StringBuilder(getDeclaringType().getBeanType().getSimpleName()).append('.');
            baseString.append(getName());
            outputArguments(baseString, arguments);
            return baseString.toString();
        }

        @Override
        public InjectionPoint getInjectionPoint() {
            return this;
        }

        @Override
        public BeanDefinition getDeclaringBean() {
            return getDeclaringType();
        }

        @Override
        public boolean requiresReflection() {
            return requiresReflection;
        }

        @Override
        public Argument<?>[] getArguments() {
            return arguments;
        }

        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return getArgument().getAnnotationMetadata();
        }
    }

    /**
     * A segment that represents a field.
     */
    public static class FieldSegment extends AbstractSegment implements InjectionPoint, ArgumentCoercible, ArgumentInjectionPoint {

        private final boolean requiresReflection;

        /**
         * @param declaringClass      The declaring class
         * @param argument            The argument
         * @param requiresReflection  Is requires reflection
         */
        FieldSegment(BeanDefinition declaringClass, Argument argument, boolean requiresReflection) {
            super(declaringClass, argument.getName(), argument);
            this.requiresReflection = requiresReflection;
        }

        @Override
        public String toString() {
            return getDeclaringType().getBeanType().getSimpleName() + "." + getName();
        }

        @Override
        public InjectionPoint getInjectionPoint() {
            return this;
        }

        @Override
        public BeanDefinition getDeclaringBean() {
            return getDeclaringType();
        }

        @Override
        public boolean requiresReflection() {
            return requiresReflection;
        }

        @Override
        public CallableInjectionPoint getOuterInjectionPoint() {
            throw new UnsupportedOperationException("Outer injection point not retrievable from here");
        }

        @Override
        public Argument asArgument() {
            return getArgument();
        }

        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return getArgument().getAnnotationMetadata();
        }
    }

    /**
     * Abstract class for a Segment.
     */
    abstract static class AbstractSegment implements Segment, Named {
        private final BeanDefinition declaringComponent;
        private final String name;
        private final Argument argument;

        /**
         * @param declaringClass The declaring class
         * @param name           The name
         * @param argument       The argument
         */
        AbstractSegment(BeanDefinition declaringClass, String name, Argument argument) {
            this.declaringComponent = declaringClass;
            this.name = name;
            this.argument = argument;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public BeanDefinition getDeclaringType() {
            return declaringComponent;
        }

        @Override
        public Argument getArgument() {
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
            int result = declaringComponent.hashCode();
            result = 31 * result + name.hashCode();
            result = 31 * result + argument.hashCode();
            return result;
        }

        /**
         * @param baseString The base string
         * @param arguments  The arguments
         */
        void outputArguments(StringBuilder baseString, Argument[] arguments) {
            baseString.append('(');
            for (int i = 0; i < arguments.length; i++) {
                Argument argument = arguments[i];
                boolean isInjectedArgument = argument.equals(getArgument());
                if (isInjectedArgument) {
                    baseString.append('[');
                }
                baseString.append(argument);
                if (isInjectedArgument) {
                    baseString.append(']');
                }
                if (i != arguments.length - 1) {
                    baseString.append(',');
                }
            }
            baseString.append(')');
        }
    }
}
