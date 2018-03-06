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
package org.particleframework.configurations.hystrix.stream;

import com.netflix.hystrix.HystrixCollapserMetrics;
import com.netflix.hystrix.HystrixCommandMetrics;
import com.netflix.hystrix.HystrixThreadPoolMetrics;
import com.netflix.hystrix.serial.SerialHystrixDashboardData;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;
import org.particleframework.configurations.hystrix.HystrixConfiguration;
import org.particleframework.context.annotation.Requires;
import org.particleframework.context.annotation.Value;
import org.particleframework.http.MediaType;
import org.particleframework.http.annotation.Controller;
import org.particleframework.http.annotation.Get;
import org.particleframework.http.sse.Event;

import javax.inject.Inject;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * A controller that produces a hystrix event stream as Server Sent events
 *
 * @author graemerocher
 * @since 1.0
 */
@Controller("${hystrix.stream.path:/hystrix.stream}")
@Requires(classes = {SerialHystrixDashboardData.class, Flowable.class})
@Requires(property = HystrixConfiguration.HYSTRIX_STREAM_ENABLED, value = "true", defaultValue = "false")
public class HystrixStreamController {

    private final Duration interval;

    @Inject
    public HystrixStreamController(@Value("${hystrix.stream.interval:1s}") Duration interval) {
        this.interval = interval;
    }

    public HystrixStreamController() {
        this(Duration.ofSeconds(1));
    }

    @Get(uri = "/", produces = MediaType.TEXT_EVENT_STREAM)
    public Flowable<Event<String>> hystrixStream() {
        return Flowable.interval(interval.toMillis(), TimeUnit.MILLISECONDS)
                       .subscribeOn(Schedulers.io())
                       .flatMap( num -> Flowable.create(eventEmitter -> {
                           try {
                               for (HystrixCommandMetrics commandMetrics : HystrixCommandMetrics.getInstances()) {
                                   eventEmitter.onNext(Event.of(SerialHystrixDashboardData.toJsonString(commandMetrics)));
                               }
                               for (HystrixThreadPoolMetrics threadPoolMetrics : HystrixThreadPoolMetrics.getInstances()) {
                                   eventEmitter.onNext(Event.of(SerialHystrixDashboardData.toJsonString(threadPoolMetrics)));
                               }
                               for (HystrixCollapserMetrics collapserMetrics : HystrixCollapserMetrics.getInstances()) {
                                   eventEmitter.onNext(Event.of(SerialHystrixDashboardData.toJsonString(collapserMetrics)));
                               }
                           } catch (Exception e) {
                               eventEmitter.onError(e);
                           }

                       }, BackpressureStrategy.BUFFER)) ;
    }
}
