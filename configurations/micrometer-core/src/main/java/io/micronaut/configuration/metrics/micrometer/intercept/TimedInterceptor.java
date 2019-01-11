/*
 * Copyright 2017-2019 original authors
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

package io.micronaut.configuration.metrics.micrometer.intercept;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.util.StringUtils;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.functions.Action;

import javax.inject.Singleton;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

/**
 * Implements support for {@link io.micrometer.core.annotation.Timed} as AOP advice.
 *
 * @author graemerocher
 * @since 1.1.0
 */
@Singleton
@RequiresMetrics
public class TimedInterceptor implements MethodInterceptor<Object, Object> {

    /**
     * Default metric name.
     *
     * @since 1.1.0
     */
    public static final String DEFAULT_METRIC_NAME = "method.timed";

    /**
     * Tag key for an exception.
     *
     * @since 1.1.0
     */
    public static final String EXCEPTION_TAG = "exception";

    private final MeterRegistry meterRegistry;

    /**
     * Default constructor.
     * @param meterRegistry The meter registry
     */
    protected TimedInterceptor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        final Class<Object> javaReturnType = context.getReturnType().getType();
        final AnnotationMetadata metadata = context.getAnnotationMetadata();
        final String metricName = metadata.getValue(Timed.class, String.class).orElse(DEFAULT_METRIC_NAME);
        if (StringUtils.isNotEmpty(metricName)) {
            if (Publishers.isConvertibleToPublisher(javaReturnType)) {

                // handle publisher
                final Object result = context.proceed();
                if (result == null) {
                    return result;
                } else {
                    AtomicReference<Timer.Sample> sample = new AtomicReference<>();
                    AtomicReference<String> exceptionClass = new AtomicReference<>("none");
                    if (Publishers.isSingle(result.getClass())) {
                        Single<?> single = Publishers.convertPublisher(result, Single.class);
                        single = single.doOnSubscribe(d -> sample.set(Timer.start(meterRegistry)))
                                       .doOnError(throwable -> stopTimed(
                                               metricName,
                                               sample.get(),
                                               throwable.getClass().getSimpleName(),
                                               metadata
                                       ))
                                       .doOnSuccess(o -> stopTimed(
                                               metricName,
                                               sample.get(),
                                               "none",
                                               metadata
                                       ));

                        return Publishers.convertPublisher(single, javaReturnType);
                    } else {
                        Flowable<?> flowable = Publishers.convertPublisher(result, Flowable.class);
                        flowable = flowable.doOnRequest(n -> sample.set(Timer.start(meterRegistry)))
                                .doOnError(throwable -> exceptionClass.set(throwable.getClass().getSimpleName()))
                                .doOnComplete((Action) () -> {
                                    final Timer.Sample s = sample.get();
                                    if (s != null) {
                                        stopTimed(
                                                metricName,
                                                s,
                                                exceptionClass.get(),
                                                metadata
                                        );
                                    }
                                });
                        return Publishers.convertPublisher(flowable, javaReturnType);
                    }
                }


            } else {
                // blocking case
                Timer.Sample sample = Timer.start(meterRegistry);
                String exceptionClass = "none";
                boolean stop = true;
                try {
                    final Object result = context.proceed();
                    if (result instanceof CompletionStage) {
                        stop = false;
                        CompletionStage<?> cs = (CompletionStage<?>) result;
                        return cs.whenComplete((BiConsumer<Object, Throwable>) (o, throwable) -> stopTimed(
                                metricName,
                                sample,
                                throwable != null ? throwable.getClass().getSimpleName() : "none",
                                metadata));
                    } else {
                        return result;
                    }
                } catch (Exception e) {
                    exceptionClass = e.getClass().getSimpleName();
                    throw e;
                } finally {
                    if (stop) {

                        stopTimed(metricName, sample, exceptionClass, metadata);
                    }
                }
            }
        }
        return context.proceed();
    }

    private void stopTimed(String metricName, Timer.Sample sample, String exceptionClass, AnnotationMetadata metadata) {
        try {
            final String description = metadata.getValue(Timed.class, "description", String.class).orElse(null);
            final String[] tags = metadata.getValue(Timed.class, "extraTags", String[].class).orElse(StringUtils.EMPTY_STRING_ARRAY);
            final double[] percentiles = metadata.getValue(Timed.class, "percentiles", double[].class).orElse(null);
            final boolean histogram = metadata.getValue(Timed.class, "histogram", boolean.class).orElse(false);
            final Timer timer = Timer.builder(metricName)
                    .description(description)
                    .tags(tags)
                    .tags(EXCEPTION_TAG, exceptionClass)
                    .publishPercentileHistogram(histogram)
                    .publishPercentiles(percentiles)
                    .register(meterRegistry);
            sample.stop(timer);
        } catch (Exception e) {
            // ignoring on purpose
        }
    }
}
