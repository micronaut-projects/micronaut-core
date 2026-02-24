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
package io.micronaut.jackson;

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.Version;
import tools.jackson.databind.AnnotationIntrospector;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.annotation.JsonPOJOBuilder;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.cfg.MapperBuilder;
import tools.jackson.databind.cfg.MapperConfig;
import tools.jackson.databind.introspect.Annotated;
import tools.jackson.databind.introspect.AnnotatedClass;
import tools.jackson.databind.introspect.AnnotationIntrospectorPair;
import tools.jackson.databind.util.ClassUtil;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Support for Jackson 2.x databind annotations. Used by micronaut-oracle-cloud.
 *
 * @since 5.0.0
 * @author Jonas Konrad
 */
@Experimental
public final class Jackson2AnnotationSupport {
    private static final boolean JACKSON2_AVAILABLE;

    static {
        boolean available;
        try {
            Class.forName(com.fasterxml.jackson.databind.annotation.JsonDeserialize.class.getName());
            available = true;
        } catch (LinkageError | ClassNotFoundException e) {
            available = false;
        }
        JACKSON2_AVAILABLE = available;
    }

    /**
     * Adapt an object mapper builder to support some jackson-databind 2.x annotations.
     *
     * @param builder The builder
     */
    public static void installJackson2Introspector(MapperBuilder<?, ?> builder) {
        if (!JACKSON2_AVAILABLE) {
            return;
        }
        AnnotationIntrospector prev = builder.annotationIntrospector();
        builder.annotationIntrospector(new AnnotationIntrospectorPair(
            prev,
            new Jackson2CompatibleAnnotationIntrospector(prev)
        ));
    }

    private static class Jackson2CompatibleAnnotationIntrospector extends AnnotationIntrospector {
        final AnnotationIntrospector delegate;

        Jackson2CompatibleAnnotationIntrospector(AnnotationIntrospector delegate) {
            this.delegate = delegate;
        }

        @Override
        public JavaType refineSerializationType(MapperConfig<?> config, Annotated a, JavaType baseType) {
            return delegate.refineSerializationType(config, mapAnnotations(a), baseType);
        }

        // add nullable annotation
        @Override
        @Nullable
        protected <A extends Annotation> A _findAnnotation(Annotated ann, Class<A> annoClass) {
            return super._findAnnotation(ann, annoClass);
        }

        @Override
        @Nullable
        public Class<?> findPOJOBuilder(MapperConfig<?> config, AnnotatedClass ac) {
            com.fasterxml.jackson.databind.annotation.JsonDeserialize ann = _findAnnotation(ac, com.fasterxml.jackson.databind.annotation.JsonDeserialize.class);
            return (ann == null) ? null : classIfExplicit(ann.builder());
        }

        @Override
        public JsonPOJOBuilder.@Nullable Value findPOJOBuilderConfig(MapperConfig<?> config, AnnotatedClass ac) {
            com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder ann = _findAnnotation(ac, com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder.class);
            return (ann == null) ? null : new JsonPOJOBuilder.Value((JsonPOJOBuilder) map(com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder.class, JsonPOJOBuilder.class, ann));
        }

        @Nullable
        private Class<?> classIfExplicit(@Nullable Class<?> cls) {
            if (cls == null || ClassUtil.isBogusClass(cls)) {
                return null;
            }
            return cls;
        }

        @Override
        public JsonSerialize.Typing findSerializationTyping(MapperConfig<?> config, Annotated a) {
            return delegate.findSerializationTyping(config, mapAnnotations(a));
        }

        @Override
        public JavaType refineDeserializationType(MapperConfig<?> config, Annotated a, JavaType baseType) {
            return delegate.refineDeserializationType(config, mapAnnotations(a), baseType);
        }

        @Override
        public Version version() {
            return Version.unknownVersion();
        }

        private Annotated mapAnnotations(Annotated ann) {
            ann = mapIfPresent(ann, com.fasterxml.jackson.databind.annotation.JsonDeserialize.class, tools.jackson.databind.annotation.JsonDeserialize.class);
            ann = mapIfPresent(ann, com.fasterxml.jackson.databind.annotation.JsonSerialize.class, tools.jackson.databind.annotation.JsonSerialize.class);
            ann = mapIfPresent(ann, com.fasterxml.jackson.databind.annotation.JsonAppend.class, tools.jackson.databind.annotation.JsonAppend.class);
            ann = mapIfPresent(ann, com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder.class, tools.jackson.databind.annotation.JsonPOJOBuilder.class);
            return ann;
        }

        private static <F extends Annotation, T extends Annotation> Annotated mapIfPresent(Annotated original, Class<F> from, Class<T> to) {
            F ann = original.<@Nullable F>getAnnotation(from);
            if (ann == null) {
                return original;
            }
            T mapped = to.cast(map(from, to, ann));
            return new AmendedAnnotated<>(original, to, mapped);
        }

        @SuppressWarnings("unchecked")
        private static Object map(Class<? extends Annotation> from, Class<? extends Annotation> to, Object annotation) {
            return Proxy.newProxyInstance(Jackson2AnnotationSupport.class.getClassLoader(), new Class[]{to}, (proxy, method, args) -> {
                Method fromMethod = from.getMethod(method.getName());
                Object fromValue = fromMethod.invoke(annotation);
                if (Objects.equals(fromMethod.getDefaultValue(), fromValue)) {
                    return method.getDefaultValue();
                }
                Class<?> fromMemberType = fromMethod.getReturnType();
                Class<?> toMemberType = method.getReturnType();
                if (fromMemberType == toMemberType) {
                    return fromValue;
                } else if (fromMemberType.isEnum() && toMemberType.isEnum()) {
                    return ((Optional<Object>) Stream.of(toMemberType.getEnumConstants())
                        .filter(c -> ((Enum<?>) c).name().equals(((Enum<?>) fromValue).name()))
                        .findAny()).orElse(method.getDefaultValue());
                } else if (fromMemberType.isAnnotation() && toMemberType.isAnnotation()) {
                    return map(fromMemberType.asSubclass(Annotation.class), toMemberType.asSubclass(Annotation.class), fromValue);
                }
                return method.getDefaultValue();
            });
        }
    }

    private static final class AmendedAnnotated<A extends Annotation> extends DelegateAnnotated {
        private final Class<A> type;
        private final A annotation;

        private AmendedAnnotated(Annotated delegate, Class<A> type, A annotation) {
            super(delegate);
            this.type = type;
            this.annotation = annotation;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <B extends Annotation> B getAnnotation(Class<B> acls) {
            if (acls == this.type) {
                return (B) annotation;
            }
            return super.getAnnotation(acls);
        }

        @Override
        public Stream<Annotation> annotations() {
            return Stream.concat(super.annotations().filter(a -> !type.isInstance(a)), Stream.of(annotation));
        }

        @Override
        public boolean hasAnnotation(Class<? extends Annotation> acls) {
            return super.hasAnnotation(acls) || acls == type;
        }

        @Override
        public boolean hasOneOf(Class<? extends Annotation>[] annoClasses) {
            return super.hasOneOf(annoClasses) || Arrays.asList(annoClasses).contains(type);
        }

        @Override
        public boolean equals(Object o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int hashCode() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString() {
            return "Amended[" + delegate + " + " + annotation + "]";
        }
    }

    private abstract static class DelegateAnnotated extends Annotated {
        final Annotated delegate;

        private DelegateAnnotated(Annotated delegate) {
            this.delegate = delegate;
        }

        @Override
        public <A extends Annotation> A getAnnotation(Class<A> acls) {
            return delegate.getAnnotation(acls);
        }

        @Override
        public Stream<Annotation> annotations() {
            return delegate.annotations();
        }

        @Override
        public boolean hasAnnotation(Class<? extends Annotation> acls) {
            return delegate.hasAnnotation(acls);
        }

        @Override
        public boolean hasOneOf(Class<? extends Annotation>[] annoClasses) {
            return delegate.hasOneOf(annoClasses);
        }

        @Override
        public AnnotatedElement getAnnotated() {
            return delegate.getAnnotated();
        }

        @Override
        protected int getModifiers() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isPublic() {
            return delegate.isPublic();
        }

        @Override
        public boolean isStatic() {
            return delegate.isStatic();
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public JavaType getType() {
            return delegate.getType();
        }

        @Override
        public Class<?> getRawType() {
            return delegate.getRawType();
        }
    }
}
