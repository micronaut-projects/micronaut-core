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
package io.micronaut.docs.aop.around;

// tag::imports[]
import io.micronaut.context.annotation.Type;
import io.micronaut.aop.Around;
import java.lang.annotation.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
// end::imports[]

// tag::annotation[]
@Documented
@Retention(RUNTIME) // <1>
@Target({ElementType.TYPE, ElementType.METHOD}) // <2>
@Around // <3>
@Type(NotNullInterceptor.class) // <4>
public @interface NotNull {
}
// end::annotation[]
