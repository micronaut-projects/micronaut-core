/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.inject.autowired;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.core.annotation.AnnotationUtil;
import jakarta.inject.Inject;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation is a synonym for {@link Inject} which in general should be preferred.
 *
 * <p>The primary difference with this annotation is to allow optional injection via {@link #required()}.</p>
 *
 * <p>Note that unlike {@link Inject} this annotation cannot be applied to a constructor where optional injection is not possible.</p>
 *
 * @since 4.5.0
 * @see #required()
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.ANNOTATION_TYPE, ElementType.FIELD, ElementType.METHOD})
@Documented
public @interface Autowired {

    /**
     * Specify whether the injection point is required.
     *
     * <p>Setting to {@code false} results in the injection point being optional
     * and therefore will silently proceed without failure if there are missing
     * beans that don't satisfy the requirements of the injection point.</p>
     *
     * <p>Setting to {@code true} (the default behaviour) will result in a {@link io.micronaut.context.exceptions.NoSuchBeanException}
     * being thrown when the bean is first retrieved.</p>
     *
     * @return True if it is required.
     * @see io.micronaut.context.exceptions.NoSuchBeanException
     */
    @AliasFor(
        annotation = Inject.class,
        member = AnnotationUtil.MEMBER_REQUIRED
    )
    boolean required() default true;
}
