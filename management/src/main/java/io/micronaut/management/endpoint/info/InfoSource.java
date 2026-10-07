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
package io.micronaut.management.endpoint.info;

import io.micronaut.context.env.PropertySource;
import io.micronaut.core.async.publisher.CompletionStagePublishers;
import io.micronaut.core.order.Ordered;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;

import java.util.concurrent.CompletionStage;

/**
 * <p>Describes an source of info that will be retrieved by the {@link InfoEndpoint}.</p>
 *
 * @author Zachary Klein
 * @since 1.0
 */
public interface InfoSource extends Ordered {

    /**
     * @return A publisher that returns a {@link PropertySource} containing data to be added to the endpoint response.
     */
    Publisher<PropertySource> getSource();

    /**
     * The {@link CompletionStage} counterpart of {@link #getSource()}, which the
     * {@link InfoAggregator} calls. By default, it adapts the first {@link PropertySource} emitted
     * by {@link #getSource()}, and completes with {@code null} when the publisher completes
     * without one, which the aggregator handles as an empty property source. Cancelling the
     * stage cancels the subscription. A source that provides its property source without a
     * publisher overrides this method. The built-in source classes do not, so that a subclass
     * that overrides {@link #getSource()} is called through it.
     *
     * @return A {@link CompletionStage} completed with the {@link PropertySource} containing data to be added to the endpoint response, or with {@code null} when there is none
     * @since 5.3.0
     */
    default CompletionStage<@Nullable PropertySource> getSourceAsync() {
        return CompletionStagePublishers.first(getSource(), null);
    }

}
