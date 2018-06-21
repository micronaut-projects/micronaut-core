/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.configuration.kafka.annotation;

import io.micronaut.aop.Introduction;
import io.micronaut.configuration.kafka.intercept.KafkaClientIntroductionAdvice;
import io.micronaut.context.annotation.AliasFor;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Type;
import io.micronaut.retry.annotation.Recoverable;

import javax.inject.Scope;
import javax.inject.Singleton;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.time.Duration;

import static java.lang.annotation.RetentionPolicy.RUNTIME;


/**
 * An introduction advice that automatically implements interfaces and abstract classes and creates {@link org.apache.kafka.clients.producer.KafkaProducer} instances.
 *
 * @author Graeme Rocher
 * @since 1.0
 * @see KafkaClientIntroductionAdvice
 */
@Documented
@Retention(RUNTIME)
@Scope
@Introduction
@Type(KafkaClientIntroductionAdvice.class)
@Recoverable
@Singleton
public @interface KafkaClient {

    /**
     * Same as {@link #id()}.
     *
     * @return The id of the client
     */
    @AliasFor(member = "name")
    String value() default "";

    /**
     * @return The id of the client
     */
    @AliasFor(member = "value")
    String id() default "";

    /**
     * The default timeout for synchronous send operations.
     *
     * @return The timeout
     */
    String sendTimeout() default "10s";

    /**
     * Whether to include timestamps in outgoing messages.
     *
     * @return True if time stamps should be included. Defaults to false.
     */
    boolean timestamp() default false;

    /**
     * Additional properties to configure with for Consumer.
     *
     * @return The properties
     */
    Property[] properties() default {};
}
