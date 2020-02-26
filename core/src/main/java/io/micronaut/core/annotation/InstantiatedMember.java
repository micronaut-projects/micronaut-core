/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <p>An annotation that can be used on another annotation member that returns a class to indicate that
 * the value of the annotation should be populated as an instance of the specified class.</p>
 *
 * <p>This allows the computed annotation metadata to produce an instantiated instance without using dynamic classloading or reflective APIs.</p>
 *
 * <p>Note that the member should be a simple POJO with a public no argument constructor.</p>
 *
 * @author graemerocher
 * @since 1.1
 */
@Documented
@Retention(RUNTIME)
@Target(ElementType.METHOD)
public @interface InstantiatedMember {
}
