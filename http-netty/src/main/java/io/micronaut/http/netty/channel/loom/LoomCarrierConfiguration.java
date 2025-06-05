/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.http.netty.channel.loom;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.bind.annotation.Bindable;

import java.time.Duration;

/**
 * Configuration properties for the netty-based virtual thread scheduler. These properties are
 * experimental and subject to change as the scheduler evolves, even in patch releases.
 *
 * @param timeSliceLatency        Time slice size in latency mode.
 * @param timeSliceThroughput     Time slice size in throughput mode.
 * @param fifoSwitchTime          Number of nanoseconds between switching between continuation FILO and FIFO modes.
 * @param taskFifoThreshold       Oldest enqueued continuation must be this old before execution can switch to FIFO mode.
 * @param blockTime               Maximum blocking wait time.
 * @param throughputModeThreshold Maximum number of queued tasks before entering throughput mode.
 * @param workSpillThreshold      Maximum number of threads per event loop before work spilling should kick in.
 * @param normalWarmupTasks       Number of tasks that should run on the normal FJP to initialize e.g. the Poller before
 *                                switching to the netty scheduler
 * @since 4.9.0
 * @author Jonas Konrad
 */
@Experimental
@ConfigurationProperties("micronaut.netty.loom-carrier")
public record LoomCarrierConfiguration(
    @Bindable(defaultValue = "500us")
    Duration timeSliceLatency,
    @Bindable(defaultValue = "5ms")
    Duration timeSliceThroughput,
    @Bindable(defaultValue = "1ms")
    Duration fifoSwitchTime,
    @Bindable(defaultValue = "5ms")
    Duration taskFifoThreshold,
    @Bindable(defaultValue = "1s")
    Duration blockTime,
    @Bindable(defaultValue = "10")
    int throughputModeThreshold,
    @Bindable(defaultValue = "2")
    int workSpillThreshold,
    @Bindable(defaultValue = "100")
    int normalWarmupTasks
) {
}
