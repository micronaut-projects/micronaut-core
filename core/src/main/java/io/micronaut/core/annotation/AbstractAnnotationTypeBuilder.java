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
package io.micronaut.core.annotation;

import io.micronaut.core.type.Argument;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The base class of the generated {@link AnnotationBuilder}s that describe their annotation type: the generated
 * class is its own {@link AnnotationType}.
 *
 * @param <A> The annotation type
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
@UsedByGeneratedCode
public abstract class AbstractAnnotationTypeBuilder<A extends Annotation> extends AbstractAnnotationBuilder<A> implements AnnotationType<A> {

    private final AnnotationMetadata annotationMetadata;
    private final List<AnnotationTypeMember<?>> members;
    private final @Nullable String repeatableContainer;
    private final RetentionPolicy retentionPolicy;
    private final Set<ElementType> targets;
    private final boolean inherited;

    /**
     * @param type                The annotation type
     * @param defaults            The defaults by member name in the form the annotation metadata records them
     * @param annotationMetadata  The annotation metadata of the annotation type
     * @param members             The members, in declaration order
     * @param repeatableContainer The name of the repeatable container, or {@code null}
     * @param retentionPolicy     The retention
     * @param targets             The targets
     * @param inherited           Whether the annotation type is inherited
     */
    protected AbstractAnnotationTypeBuilder(Class<A> type,
                                            Map<CharSequence, Object> defaults,
                                            AnnotationMetadata annotationMetadata,
                                            AnnotationTypeMember<?>[] members,
                                            @Nullable String repeatableContainer,
                                            RetentionPolicy retentionPolicy,
                                            ElementType[] targets,
                                            boolean inherited) {
        super(type, defaults);
        this.annotationMetadata = annotationMetadata;
        this.members = List.of(members);
        this.repeatableContainer = repeatableContainer;
        this.retentionPolicy = retentionPolicy;
        this.targets = Set.of(targets);
        this.inherited = inherited;
    }

    @Override
    public final Optional<AnnotationType<A>> describe() {
        return Optional.of(this);
    }

    @Override
    public final Class<A> getType() {
        return annotationType();
    }

    @Override
    public final List<AnnotationTypeMember<?>> getMembers() {
        return members;
    }

    @Override
    public final Optional<String> getRepeatableContainer() {
        return Optional.ofNullable(repeatableContainer);
    }

    @Override
    public final RetentionPolicy getRetentionPolicy() {
        return retentionPolicy;
    }

    @Override
    public final Set<ElementType> getTargets() {
        return targets;
    }

    @Override
    public final boolean isInherited() {
        return inherited;
    }

    @Override
    public final AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata;
    }

    @Override
    public String toString() {
        return "AnnotationType[" + annotationType().getName() + "]";
    }

    /**
     * A member of a generated {@link AnnotationType}.
     *
     * @param <T> The type of the member
     */
    @Internal
    @UsedByGeneratedCode
    public static final class Member<T> implements AnnotationTypeMember<T> {

        private final Argument<T> argument;
        private final DefaultState defaultState;
        private final @Nullable Object defaultValue;

        /**
         * @param argument     The member as an argument, with its name, type and annotation metadata
         * @param defaultState The state of the default
         * @param defaultValue The default, in the form the annotation metadata records it, or {@code null}
         */
        public Member(Argument<T> argument, DefaultState defaultState, @Nullable Object defaultValue) {
            this.argument = argument;
            this.defaultState = defaultState;
            this.defaultValue = defaultValue;
        }

        @Override
        public String getName() {
            return argument.getName();
        }

        @Override
        public Argument<T> asArgument() {
            return argument;
        }

        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return argument.getAnnotationMetadata();
        }

        @Override
        public DefaultState getDefaultState() {
            return defaultState;
        }

        @Override
        public Optional<Object> getDefaultValue() {
            return Optional.ofNullable(copy(defaultValue));
        }

        @Override
        public String toString() {
            return argument.getTypeName() + " " + argument.getName();
        }

        /**
         * An array is copied, so that the caller cannot change the default.
         */
        private static @Nullable Object copy(@Nullable Object value) {
            return switch (value) {
                case Object[] array -> array.clone();
                case int[] array -> array.clone();
                case long[] array -> array.clone();
                case short[] array -> array.clone();
                case byte[] array -> array.clone();
                case char[] array -> array.clone();
                case boolean[] array -> array.clone();
                case float[] array -> array.clone();
                case double[] array -> array.clone();
                case null, default -> value;
            };
        }
    }
}
