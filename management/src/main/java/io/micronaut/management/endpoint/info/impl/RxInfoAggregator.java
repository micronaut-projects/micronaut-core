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
package io.micronaut.management.endpoint.info.impl;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.EmptyPropertySource;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.env.PropertySourcePropertyResolver;
import io.micronaut.core.convert.format.MapFormat;
import io.micronaut.core.naming.conventions.StringConvention;
import io.micronaut.management.endpoint.info.InfoAggregator;
import io.micronaut.management.endpoint.info.InfoEndpoint;
import io.micronaut.management.endpoint.info.InfoSource;
import io.reactivex.Flowable;
import io.reactivex.Single;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * <p>Default implementation of {@link InfoAggregator}.
 *
 * @author James Kleeh
 * @author Zachary Klein
 * @since 1.0
 */
@Singleton
@Requires(beans = InfoEndpoint.class)
public class RxInfoAggregator implements InfoAggregator<Map<String, Object>> {

    @Override
    public Publisher<Map<String, Object>> aggregate(InfoSource[] sources) {
        return aggregateResults(sources).toList().map((List<Map.Entry<Integer, PropertySource>> list) -> {
            PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver();
            list.stream()
                .sorted((e1, e2) -> Integer.compare(e2.getKey(), e1.getKey()))
                .forEach(entry -> resolver.addPropertySource(entry.getValue()));
            return resolver.getAllProperties(StringConvention.RAW, MapFormat.MapTransformation.NESTED);
        }).toFlowable();
    }

    /**
     * Create a {@link Flowable} of ordered {@link PropertySource} from an array of {@link InfoSource}.
     *
     * @param sources Array of {@link InfoSource}
     * @return An {@link Flowable} of {@link java.util.Map.Entry}, where the key is an {@link Integer} and value is the
     * {@link PropertySource} returned by the {@link InfoSource}
     */
    protected Flowable<Map.Entry<Integer, PropertySource>> aggregateResults(InfoSource[] sources) {
        List<Publisher<Map.Entry<Integer, PropertySource>>> publishers = new ArrayList<>(sources.length);
        for (int i = 0; i < sources.length; i++) {
            int index = i;
            Single<Map.Entry<Integer, PropertySource>> single = Flowable
                .fromPublisher(sources[i].getSource())
                .first(new EmptyPropertySource())
                .map(source -> new AbstractMap.SimpleEntry<>(index, source));
            publishers.add(single.toFlowable());
        }
        return Flowable.merge(publishers);
    }
}
