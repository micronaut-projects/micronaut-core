/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.runtime.context.env;

import io.micronaut.aop.Introduction;
import io.micronaut.context.annotation.Type;
import io.micronaut.core.annotation.Internal;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Internal annotation that allows the definition on {@link io.micronaut.context.annotation.ConfigurationProperties}
 * on interfaces.
 *
 * @author graemerocher
 * @since 1.3.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Introduction
@Type(ConfigurationIntroductionAdvice.class)
@Internal
@interface ConfigurationAdvice {
    /**
     * @return Is the method call a bean lookup
     */
    boolean bean() default false;

    /**
     * @return The property to lookup
     */
    String value() default "";
}
