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
package io.micronaut.management.endpoint.threads;

import io.micronaut.context.annotation.DefaultImplementation;
import io.micronaut.management.endpoint.threads.impl.DefaultThreadInfoMapper;
import org.reactivestreams.Publisher;

import java.lang.management.ThreadInfo;

/**
 * Responsible for converting the standard Java ThreadInfo class
 * into another object for serialization.
 *
 * @param <T> The type to be mapped into
 * @author James Kleeh
 * @since 1.2.0
 */
@DefaultImplementation(DefaultThreadInfoMapper.class)
public interface ThreadInfoMapper<T> {

    /**
     * Converts the given {@link ThreadInfo} objects into any other
     * object to be used for serialization.
     *
     * @param threads The thread publisher
     * @return A publisher of objects to be serialized.
     */
    Publisher<T> mapThreadInfo(Publisher<ThreadInfo> threads);
}
