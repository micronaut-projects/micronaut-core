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
package io.micronaut.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * An annotation that can be used on types where there may be many implementations of a
 * particular interface. This triggers building of an index internal to the bean context that speeds up bean lookups by type.
 *
 * <p>A bean is enumerable through {@code BeanContext.getBeanDefinitions(Class)} by every type it is indexed by,
 * whether the annotation is declared on the bean, inherited from a stereotype annotation or added at compile time,
 * even if the bean does not implement that type. Injecting or resolving a bean instance
 * (for example via {@code BeanContext.getBean(Class)}) by a type the bean does not implement remains unsupported.</p>
 *
 * @since 1.1
 * @author graemerocher
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Repeatable(value = Indexes.class)
@Inherited
public @interface Indexed {
    /**
     * @return The indexed type
     */
    Class<?> value();
}
