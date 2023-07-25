/*
 * Copyright 2017-2022 original authors
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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.DefaultArgument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.annotation.EvaluatedAnnotationMetadata;

/**
 * An argument that is aware of evaluated expressions which can be used to resolve
 * expression placeholders in the annotation metadata.
 *
 * @param <T> The argument type
 *
 * @author Sergey Gavrilov
 * @since 4.0
 */
@Internal
final class ExpressionsAwareArgument<T> extends DefaultArgument<T> implements BeanContextConfigurable,
                                                                        BeanDefinitionAware {

    private final EvaluatedAnnotationMetadata annotationMetadata;

    private ExpressionsAwareArgument(Argument<T> argument,
                                     EvaluatedAnnotationMetadata annotationMetadata) {
        super(argument.getType(), argument.getName(), argument.getAnnotationMetadata(),
            argument.getTypeVariables(), argument.getTypeParameters(), argument.isTypeVariable());
        this.annotationMetadata = annotationMetadata;
    }

    public static <T> Argument<T> wrapIfNecessary(Argument<T> argument) {
        return wrapIfNecessary(argument, null, null);
    }

    public static <T> Argument<T> wrapIfNecessary(Argument<T> argument,
                                                  @Nullable BeanContext beanContext,
                                                  @Nullable BeanDefinition<?> owningBean) {
        if (!(argument instanceof DefaultArgument<T>)) {
            return null;
        }

        AnnotationMetadata argumentAnnotationMetadata = argument.getAnnotationMetadata();
        if (argumentAnnotationMetadata.hasPropertyExpressions()) {
            if (!(argument instanceof EnvironmentAwareArgument<T>) && beanContext instanceof ApplicationContext ac) {
                EnvironmentAwareArgument<T> environmentAwareArgument = new EnvironmentAwareArgument<>((DefaultArgument<T>) argument);
                environmentAwareArgument.configure(ac.getEnvironment());
                return environmentAwareArgument;
            } else {
                return argument;
            }
        } else if (argumentAnnotationMetadata.hasEvaluatedExpressions()) {
            AnnotationMetadata annotationMetadata =
                EvaluatedAnnotationMetadata.wrapIfNecessary(argumentAnnotationMetadata);
            if (annotationMetadata instanceof EvaluatedAnnotationMetadata evaluatedAnnotationMetadata) {
                if (beanContext != null) {
                    evaluatedAnnotationMetadata.configure(beanContext);
                }

                if (owningBean != null) {
                    evaluatedAnnotationMetadata.setBeanDefinition(owningBean);
                }

                return new ExpressionsAwareArgument<>(argument, evaluatedAnnotationMetadata);
            }
        }
        return argument;
    }

    @Override
    public void setBeanDefinition(BeanDefinition<?> beanDefinition) {
        annotationMetadata.setBeanDefinition(beanDefinition);
    }

    @Override
    public void configure(BeanContext context) {
        annotationMetadata.configure(context);
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata;
    }
}
