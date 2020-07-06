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
package io.micronaut.management.endpoint.threads;

import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.annotation.Read;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;

import java.lang.management.ManagementFactory;

/**
 * <p>Exposes an {@link Endpoint} to display application threads.</p>
 *
 * @author James Kleeh
 * @since 1.2.0
 */
@Endpoint("threaddump")
public class ThreadDumpEndpoint {

    private final ThreadInfoMapper<?> threadInfoMapper;

    /**
     * Constructor.
     *
     * @param threadInfoMapper The mapper
     */
    ThreadDumpEndpoint(ThreadInfoMapper<?> threadInfoMapper) {
        this.threadInfoMapper = threadInfoMapper;
    }

    /**
     * @return A publisher containing the thread information
     */
    @Read
    Publisher getThreadDump() {
        return threadInfoMapper.mapThreadInfo(
                Flowable.fromArray(ManagementFactory.getThreadMXBean().dumpAllThreads(true, true)));
    }
}
