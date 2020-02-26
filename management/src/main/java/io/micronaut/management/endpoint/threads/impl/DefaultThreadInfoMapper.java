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
package io.micronaut.management.endpoint.threads.impl;

import io.micronaut.management.endpoint.threads.ThreadInfoMapper;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;
import java.lang.management.ThreadInfo;

/**
 * Default implementation of {@link ThreadInfoMapper} that returns the
 * {@link ThreadInfo} objects as is.
 *
 * @author James Kleeh
 * @since 1.2.0
 */
@Singleton
public class DefaultThreadInfoMapper implements ThreadInfoMapper<ThreadInfo> {

    @Override
    public Publisher<ThreadInfo> mapThreadInfo(Publisher<ThreadInfo> threads) {
        return threads;
    }
}
