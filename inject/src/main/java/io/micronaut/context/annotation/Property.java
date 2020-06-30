/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.core.bind.annotation.Bindable;

import java.lang.annotation.Documented;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A property that can be contained within a {@link PropertySource} or used generally throughout the system.
 *
 * @author graemerocher
 * @since 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repeatable(PropertySource.class)
public @interface Property {

    /**
     * The name of the property in kebab case. Example: my-app.bar.
     *
     * @return The name of the property
     */
    String name();

    /**
     * @return The value of the property
     */
    String value() default "";

    /**
     * @return The default value if none is specified
     */
    @AliasFor(annotation = Bindable.class, member = "defaultValue")
    String defaultValue() default "";
}
