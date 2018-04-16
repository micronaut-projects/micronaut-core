/*
 * Copyright 2017-2018 original authors
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
package io.micronaut.context;

import io.micronaut.context.exceptions.CircularDependencyException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ConstructorInjectionPoint;
import io.micronaut.inject.FieldInjectionPoint;
import io.micronaut.inject.MethodInjectionPoint;
import io.micronaut.inject.ProxyBeanDefinition;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Optional;

/**
 * Default implementation of the {@link BeanResolutionContext} interface
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class DefaultBeanResolutionContext extends LinkedHashMap<String, Object> implements BeanResolutionContext {

    private final BeanContext context;
    private final BeanDefinition rootDefinition;
    private final Path path;

    @Internal
    public DefaultBeanResolutionContext(BeanContext context, BeanDefinition rootDefinition) {
        this.context = context;
        this.rootDefinition = rootDefinition;
        this.path = new DefaultPath();
    }

    @Override
    public BeanContext getContext() {
        return context;
    }

    @Override
    public BeanDefinition getRootDefinition() {
        return rootDefinition;
    }

    @Override
    public Path getPath() {
        return path;
    }

    @Override
    public <T> Optional<T> get(CharSequence name, ArgumentConversionContext<T> conversionContext) {
        Object value = get(name);
        if (value != null && conversionContext.getArgument().getType().isInstance(value)) {
            return Optional.of((T) value);
        }
        return Optional.empty();
    }

    class DefaultPath extends LinkedList<Segment> implements Path {

        public static final String RIGHT_ARROW = " --> ";

        DefaultPath() {
        }

        @Override
        public String toString() {
            Iterator<Segment> i = descendingIterator();
            StringBuilder path = new StringBuilder();
            while (i.hasNext()) {
                path.append(i.next().toString());
                if (i.hasNext()) {
                    path.append(" --> ");
                }
            }
            return path.toString();
        }

        @Override
        public String toCircularString() {
            Iterator<Segment> i = descendingIterator();
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
        public Optional<Segment> currentSegment() {
            return Optional.ofNullable(peek());
        }

        @Override
        public Path pushConstructorResolve(BeanDefinition declaringType, Argument argument) {
            ConstructorInjectionPoint constructor = declaringType.getConstructor();
            if (constructor instanceof MethodConstructorInjectionPoint) {
                MethodSegment methodSegment = new MethodSegment(declaringType, (MethodInjectionPoint) constructor, argument);
                if (contains(methodSegment)) {
                    throw new CircularDependencyException(DefaultBeanResolutionContext.this, argument, "Circular dependency detected");
                }
                else {
                    path.push(methodSegment);
                }
            } else {
                ConstructorSegment constructorSegment = new ConstructorSegment(declaringType, argument);
                detectCircularDependency(declaringType, argument, constructorSegment);
            }
            return this;
        }

        @Override
        public Path pushMethodArgumentResolve(BeanDefinition declaringType, MethodInjectionPoint methodInjectionPoint, Argument argument) {
            MethodSegment methodSegment = new MethodSegment(declaringType, methodInjectionPoint, argument);
            if (contains(methodSegment)) {
                throw new CircularDependencyException(DefaultBeanResolutionContext.this, methodInjectionPoint, argument, "Circular dependency detected");
            } else {
                push(methodSegment);
            }

            return this;
        }

        @Override
        public Path pushFieldResolve(BeanDefinition declaringType, FieldInjectionPoint fieldInjectionPoint) {
            FieldSegment fieldSegment = new FieldSegment(declaringType, fieldInjectionPoint);
            if (contains(fieldSegment)) {
                throw new CircularDependencyException(DefaultBeanResolutionContext.this, fieldInjectionPoint, "Circular dependency detected");
            } else {
                push(fieldSegment);
            }
            return this;
        }

        private void detectCircularDependency(BeanDefinition declaringType, Argument argument, Segment constructorSegment) {
            if (contains(constructorSegment)) {
                Segment last = peek();
                if(last != null) {

                    BeanDefinition declaringBean = last.getDeclaringType();
                    // if the currently injected segment is a constructor argument and the type to be constructed is the
                    // same as the candidate, then filter out the candidate to avoid a circular injection problem
                    if (!declaringBean.equals(declaringType)) {
                        if (declaringType instanceof ProxyBeanDefinition) {
                            // take into account proxies
                            if (!((ProxyBeanDefinition) declaringType).getTargetDefinitionType().equals(declaringBean.getClass())) {
                                throw new CircularDependencyException(DefaultBeanResolutionContext.this, argument, "Circular dependency detected");
                            } else {
                                push(constructorSegment);
                            }
                        } else if (declaringBean instanceof ProxyBeanDefinition) {
                            // take into account proxies
                            if (!((ProxyBeanDefinition) declaringBean).getTargetDefinitionType().equals(declaringType.getClass())) {
                                throw new CircularDependencyException(DefaultBeanResolutionContext.this, argument, "Circular dependency detected");
                            } else {
                                push(constructorSegment);
                            }
                        } else {
                            throw new CircularDependencyException(DefaultBeanResolutionContext.this, argument, "Circular dependency detected");
                        }
                    } else {
                        push(constructorSegment);
                    }
                }
                else {
                    throw new CircularDependencyException(DefaultBeanResolutionContext.this, argument, "Circular dependency detected");
                }
            } else {
                push(constructorSegment);
            }
        }
    }

    /**
     * A segment that represents a constructor
     */
    static class ConstructorSegment extends AbstractSegment {
        ConstructorSegment(BeanDefinition declaringClass, Argument argument) {
            super(declaringClass, declaringClass.getBeanType().getName(), argument);
        }

        @Override
        public String toString() {
            StringBuilder baseString = new StringBuilder("new ");
            BeanDefinition declaringType = getDeclaringType();
            baseString.append(declaringType.getBeanType().getSimpleName());
            outputArguments(declaringType, baseString);
            return baseString.toString();
        }
    }

    /**
     * A segment that represents a method
     */
    static class MethodSegment extends AbstractSegment {

        private final MethodInjectionPoint methodInjectionPoint;

        MethodSegment(BeanDefinition declaringType, MethodInjectionPoint methodInjectionPoint, Argument argument) {
            super(declaringType, methodInjectionPoint.getName(), argument);
            this.methodInjectionPoint = methodInjectionPoint;
        }

        @Override
        public String toString() {
            StringBuilder baseString = new StringBuilder(methodInjectionPoint.getMethod().getDeclaringClass().getSimpleName()).append('.');
            baseString.append(getName());
            outputArguments(baseString, methodInjectionPoint.getArguments());
            return baseString.toString();
        }
    }

    /**
     * A segment that represents a field
     */
    static class FieldSegment extends AbstractSegment {
        FieldSegment(BeanDefinition declaringClass, FieldInjectionPoint fieldInjectionPoint) {
            super(declaringClass,
                fieldInjectionPoint.getName(),
                Argument.of(fieldInjectionPoint.getField()));
        }

        @Override
        public String toString() {
            return getDeclaringType().getBeanType().getSimpleName() + "." + getName();
        }
    }

    static abstract class AbstractSegment implements Segment {
        private final BeanDefinition declaringComponent;
        private final String name;
        private final Argument argument;

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
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

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

        void outputArguments(BeanDefinition declaringType, StringBuilder baseString) {
            Argument[] arguments = declaringType.getConstructor().getArguments();
            outputArguments(baseString, arguments);
        }

        void outputArguments(StringBuilder baseString, Argument[] arguments) {
            baseString.append('(');
            for (int i = 0; i < arguments.length; i++) {
                Argument argument = arguments[i];
                boolean isInjectedArgument = argument.equals(getArgument());
                if (isInjectedArgument) {
                    baseString.append('[');
                }
                baseString.append(argument.toString());
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
