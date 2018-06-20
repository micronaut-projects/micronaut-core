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

/**
 * Sets the value of the {@code auto.offset.reset} property for a Kafka consumer.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public enum OffsetReset {
    /**
     * Earliest means that a consumer will start reading the earliest available records for a topic.
     */
    EARLIEST,
    /**
     * Latest means that a consumer will start reading the latest consumer records.
     */
    LATEST
}
