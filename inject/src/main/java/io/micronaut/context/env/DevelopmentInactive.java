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
package io.micronaut.context.env;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.util.StringUtils;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Requires that {@link DevelopmentMode development mode} is not active: the annotated bean is only loaded while
 * {@link DevelopmentMode#PROPERTY} is not {@code true}, in any case, or not set. Use it on beans that must not run
 * under a development launcher, such as ones that exit the JVM.
 *
 * <p>A meta-annotation over a single property {@link Requires}, so that the requirement is visible where conditions
 * are evaluated from annotation metadata, unlike a custom {@link io.micronaut.context.condition.Condition}.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Requires(property = DevelopmentMode.PROPERTY, pattern = "(?i)(?!true$).*", defaultValue = StringUtils.FALSE)
@Experimental
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PACKAGE, ElementType.TYPE, ElementType.ANNOTATION_TYPE, ElementType.METHOD})
public @interface DevelopmentInactive {
}
