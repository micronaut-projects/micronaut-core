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
    @AliasFor(member = "id")
    String value() default "";

    /**
     * @return The id of the client
     */
    @AliasFor(member = "value")
    String id() default "";

    /**
     * The maximum duration to block synchronous send operations.
     *
     * @return The timeout
     */
    String maxBlock() default "";

    /**
     * Whether to include timestamps in outgoing messages.
     *
     * @return True if time stamps should be included. Defaults to false.
     */
    boolean timestamp() default false;

    /**
     * By default when specifying an array or List the object will be serializes to a JSON array. By specifying {@code true} this will instead
     * send each record in the the array or list as an individual {@link org.apache.kafka.clients.producer.ProducerRecord}.
     *
     * @return Whether to receive a batch of records or not
     */
    boolean batch() default false;

    /**
     * Additional properties to configure with for Consumer.
     *
     * @return The properties
     */
    Property[] properties() default {};

    /**
     * @return The {@code ack} setting for the client, which impacts message delivery durability.
     *
     * @see org.apache.kafka.clients.producer.ProducerConfig#ACKS_DOC
     * @see Acknowledge
     */
    int acks() default Acknowledge.DEFAULT;

    /**
     * Constants for the {@code ack} setting for the client, which impacts message delivery durability.
     *
     * @see org.apache.kafka.clients.producer.ProducerConfig#ACKS_DOC
     */
    @SuppressWarnings("WeakerAccess")
    class Acknowledge {
        /**
         * Relay on the default behaviour.
         */
        public static final int DEFAULT = Integer.MIN_VALUE;

        /**
         * Don't wait for the server to acknowledge receipt.
         */
        public static final int NONE = 0;

        /**
         * Wait for the leader to acknowledge.
         */
        public static final int ONE = 1;

        /**
         * Wait for a full set of in-sync replicas to acknowledge.
         */
        public static final int ALL = -1;
    }
}
