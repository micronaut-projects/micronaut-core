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
package io.micronaut.context.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.inject.Scope;

/**
 * <p>An annotation that can be declared on a constructor or method parameter that indicates
 * that the injected bean should be destroyed after injection completes.</p>
 *
 * <p>More specifically after a constructor or method which is annotated with {@link jakarta.inject.Inject} completes execution then any parameters annotated with {@link io.micronaut.context.annotation.InjectScope} which do not declare a specific scope such as {@link jakarta.inject.Singleton} will be destroyed resulting in the execution of {@link jakarta.annotation.PreDestroy} handlers on the bean and any dependent beans.</p>
 *
 * @author graemerocher
 * @since 3.1.0
 *
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Scope
public @interface InjectScope {
}
