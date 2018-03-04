/*
 * Copyright 2018 original authors
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
package org.particleframework.retry.annotation;

import org.particleframework.aop.Around;
import org.particleframework.context.annotation.Type;
import org.particleframework.retry.intercept.RecoveryInterceptor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <p>AOP around advice that can be applied to any type or method that requires {@link Fallback} handling.</p>
 *
 * <p>When applied to a type if an exception occurs this advice will attempt to resolve an implementation of the class that is annotated with {@link Fallback}</p>
 *
 * @author graemerocher
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Around
@Type(RecoveryInterceptor.class)
public @interface Recoverable {
}
