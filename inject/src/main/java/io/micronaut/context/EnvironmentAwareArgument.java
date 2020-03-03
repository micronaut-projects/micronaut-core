/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.DefaultArgument;
import io.micronaut.inject.annotation.AbstractEnvironmentAnnotationMetadata;
import io.micronaut.inject.annotation.DefaultAnnotationMetadata;

import javax.annotation.Nullable;

/**
 * An argument that is aware of the environment to resolve property placeholders
 * in the annotation metadata.
 *
 * @author James Kleeh
 * @since 1.0.4
 * @param <T> The argument type
 */
@Internal
class EnvironmentAwareArgument<T> extends DefaultArgument<T> implements EnvironmentConfigurable {

    private final AnnotationMetadata annotationMetadata;
    private Environment environment;

    /**
     * Default constructor.
     * @param argument The wrapped argument
     */
    EnvironmentAwareArgument(DefaultArgument<T> argument) {
        super(argument.getType(), argument.getName(), argument.getAnnotationMetadata(), argument.getTypeVariables(), argument.getTypeParameters());
        this.annotationMetadata = initAnnotationMetadata(argument.getAnnotationMetadata());
    }

    @Override
    public void configure(Environment environment) {
        this.environment = environment;
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata;
    }

    private AnnotationMetadata initAnnotationMetadata(@Nullable AnnotationMetadata annotationMetadata) {
        if (annotationMetadata instanceof DefaultAnnotationMetadata) {
            return new ArgumentAnnotationMetadata((DefaultAnnotationMetadata) annotationMetadata);
        } else if (annotationMetadata != null) {
            return annotationMetadata;
        }
        return AnnotationMetadata.EMPTY_METADATA;
    }

    /**
     * Internal environment aware annotation metadata delegate.
     */
    private final class ArgumentAnnotationMetadata extends AbstractEnvironmentAnnotationMetadata {
        ArgumentAnnotationMetadata(DefaultAnnotationMetadata targetMetadata) {
            super(targetMetadata);
        }

        @Nullable
        @Override
        protected Environment getEnvironment() {
            return environment;
        }
    }
}
