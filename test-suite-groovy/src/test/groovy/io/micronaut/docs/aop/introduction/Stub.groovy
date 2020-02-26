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
package io.micronaut.docs.aop.introduction

// tag::imports[]

import io.micronaut.aop.Introduction
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Type

import java.lang.annotation.Documented
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.Target

import static java.lang.annotation.RetentionPolicy.RUNTIME
// end::imports[]

// tag::class[]
@Introduction // <1>
@Type(StubIntroduction.class) // <2>
@Bean // <3>
@Documented
@Retention(RUNTIME)
@Target([ElementType.TYPE, ElementType.ANNOTATION_TYPE, ElementType.METHOD])
@interface Stub {
    String value() default ""
}
// end::class[]