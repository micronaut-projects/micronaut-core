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
package io.micronaut.function.client;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import io.micronaut.aop.Introduction;
import io.micronaut.context.annotation.Type;
import io.micronaut.function.client.aop.FunctionClientAdvice;
import io.micronaut.retry.annotation.Recoverable;

import javax.inject.Singleton;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

/**
 * The {@link FunctionClient} annotation allows applying introduction advise to an interface such that methods
 * defined by the interface become invokers of remote or local functions configured by the application.
 *
 * @author graemerocher
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Singleton
@Introduction
@Recoverable
@Type(FunctionClientAdvice.class)
public @interface FunctionClient {
}
