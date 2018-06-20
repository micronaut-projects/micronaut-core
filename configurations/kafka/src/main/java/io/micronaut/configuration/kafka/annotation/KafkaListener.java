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

import io.micronaut.context.annotation.*;

import javax.inject.Singleton;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <p>Annotation applied at the class level to indicate that a bean is a Kafka {@link org.apache.kafka.clients.consumer.Consumer}.</p>
 *
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Bean
@Executable(processOnStartup = true)
@DefaultScope(Singleton.class)
public @interface KafkaListener {

    /**
     * Sets the consumer group id of the Kafka consumer. If not specified the group id is configured
     * to be the value of {@link io.micronaut.runtime.ApplicationConfiguration#getName()} otherwise
     * the name of the class is used.
     *
     * @return The group id
     */
    @AliasFor(member = "groupId")
    String value() default "";

    /**
     * The same as {@link #value()}.
     *
     * @return The group id
     */
    @AliasFor(member = "value")
    String groupId() default "";

    /**
     * Sets the client id of the Kafka consumer. If not specified the client id is configured
     * to be the value of {@link io.micronaut.runtime.ApplicationConfiguration#getName()}.
     *
     * @return The client id
     */
    String clientId() default "";

    /**
     * The {@link OffsetStrategy} to use for the consumer.
     *
     * @return The {@link OffsetStrategy}
     */
    OffsetStrategy offsetStrategy() default OffsetStrategy.AUTO;

    /**
     * @return The strategy to use to start consuming records
     */
    OffsetReset offsetReset() default OffsetReset.LATEST;

    /**
     * Kafka consumers are by default single threaded. If you wish to increase the number of threads
     * for a consumer you can alter this setting. Note that this means that multiple partitions will
     * be allocated to a single application.
     *
     * <p>NOTE: When using this setting your bean should be declared as {@link Prototype}</p>
     *
     * @return The number of threads
     */
    int threads() default 1;

    /**
     * The timeout to use for calls to {@link org.apache.kafka.clients.consumer.Consumer#poll(long)}
     *
     * @return The timeout. Defaults to 100ms
     */
    String pollTimeout() default "100ms";

    /**
     * The session timeout for a consumer. See {@code session.timeout.ms}.
     *
     * @return The session timeout as a duration.
     * @see org.apache.kafka.clients.consumer.ConsumerConfig#SESSION_TIMEOUT_MS_DOC
     */
    String sessionTimeout() default "";

    /**
     * The session timeout for a consumer. See {@code session.timeout.ms}.
     *
     * @return The session timeout as a duration.
     * @see org.apache.kafka.clients.consumer.ConsumerConfig#HEARTBEAT_INTERVAL_MS_DOC
     */
    String heartbeatInterval() default "";

    /**
     * Additional properties to configure with for Consumer.
     *
     * @return The properties
     */
    Property[] properties() default {};
}
