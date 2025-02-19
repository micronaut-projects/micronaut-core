/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.http.annotation;

import io.micronaut.context.annotation.AnnotationExpressionContext;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.expression.RequestConditionContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Allows defining a condition for this route to match using an expression.
 *
 * <p>Within the scope of the expression a {@code request} variable is available that references the {@link io.micronaut.http.HttpRequest}.</p>
 *
 * <p>When added to a method the condition will be evaluated during route matching and if the condition
 * does not evaluate to {@code true} the route will not be matched resulting in a {@link io.micronaut.http.HttpStatus#NOT_FOUND} response.  </p>
 *
 * <p>Note that this annotation only applies to the server and is ignored when placed on declarative HTTP client routes.</p>
 *
 * @see io.micronaut.http.expression.RequestConditionContext
 * @since 4.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@AnnotationExpressionContext(RequestConditionContext.class)
@Experimental
public @interface RouteCondition {
    /**
     * An expression that evaluates to {@code true} or {@code false}.
     * @return The expression
     * @since 4.0.0
     */
    String value();
}
