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
package io.micronaut.inject.visitor;

import java.lang.annotation.Annotation;
import java.util.List;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.reflect.GenericTypeUtils;
import io.micronaut.core.util.Toggleable;
import io.micronaut.inject.ast.beans.BeanElement;

/**
 * Allows visiting a bean to perform any validation prior to when bean definitions are written out.
 *
 * @author graemerocher
 * @since 3.0.0
 * @param <A> An annotation type to limit visitation to a subset of beans
 */
public interface BeanElementVisitor<A extends Annotation> extends Ordered, Toggleable {
    /**
     * The available visitors.
     */
    List<BeanElementVisitor<?>> VISITORS = BeanElementVisitorLoader.load();

    /**
     * Visits a {@link io.micronaut.inject.ast.beans.BeanElement} before it is finalized and written to disk,
     * allowing mutation of any annotation metadata before writing the bean definition.
     *
     * @param beanElement The bean element
     * @param visitorContext The visitor context
     * @return The bean element or {@code null} if the bean should not be written
     */
    @Nullable BeanElement visitBeanElement(@NonNull BeanElement beanElement, @NonNull VisitorContext visitorContext);

    /**
     * Called once when visitor processing starts.
     *
     * @param visitorContext The visitor context
     */
    default void start(VisitorContext visitorContext) {
        // no-op
    }

    /**
     * Called once when visitor processing finishes.
     *
     * @param visitorContext The visitor context
     */
    default void finish(VisitorContext visitorContext) {
        // no-op
    }

    /**
     * Returns whether this visitor supports visiting the specified element.
     * @param beanElement The bean element
     * @return True if it does
     */
    default boolean supports(@NonNull BeanElement beanElement) {
        //noinspection ConstantConditions
        if (beanElement == null) {
            return false;
        }
        final Class<?> t = GenericTypeUtils.resolveInterfaceTypeArgument(getClass(), BeanElementVisitor.class)
                .orElse(Annotation.class);
        if (t == Annotation.class) {
            return true;
        } else {
            return beanElement.hasAnnotation(t.getName());
        }
    }

}