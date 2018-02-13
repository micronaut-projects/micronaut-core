/*
 * Copyright 2018 original authors
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
package org.particleframework.configuration.mongo.reactive;

/**
 * Common constants to for MongoDB settings
 *
 * @author graemerocher
 * @since 1.0
 */
public interface MongoSettings {
    /**
     * The prefix to use for all MongoDB settings
     */
    String PREFIX = "mongodb";
    /**
     * The MongoDB URI setting
     */
    String MONGODB_URI = PREFIX + ".uri";
    /**
     * The MongoDB URIs setting
     */
    String MONGODB_ADDRESSES = PREFIX + ".addresses";
    /**
     * The MongoDB servers settings
     */
    String MONGODB_SERVERS = PREFIX + ".servers";
}
