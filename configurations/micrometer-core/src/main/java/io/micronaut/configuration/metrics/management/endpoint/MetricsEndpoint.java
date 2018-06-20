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

package io.micronaut.configuration.metrics.management.endpoint;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.management.endpoint.Endpoint;
import io.micronaut.management.endpoint.Read;
import io.reactivex.Single;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED;

/**
 * Provides a metrics endpoint to visualize metrics.
 *
 * @author Christian Oestreich
 * @since 1.0
 */
@Endpoint(value = MetricsEndpoint.NAME, defaultSensitive = MetricsEndpoint.DEFAULT_SENSITIVE)
@Requires(beans = MeterRegistry.class)
@Requires(property = MICRONAUT_METRICS_ENABLED, value = "true", defaultValue = "true")
public class MetricsEndpoint {

    /**
     * If the endpoint is sensitive if no configuration is provided.
     */
    static final boolean DEFAULT_SENSITIVE = false;

    /**
     * Constant for metrics.
     */
    static final String NAME = "metrics";

    private final Collection<MeterRegistry> meterRegistries;

    /**
     * Constructor for metrics endpoint.
     *
     * @param meterRegistries Meter Registries
     */
    public MetricsEndpoint(Collection<MeterRegistry> meterRegistries) {
        this.meterRegistries = meterRegistries;
    }

    /**
     * Read operation to list metric names.  To get the details
     * the method getMetricDetails(name) should be invoked.
     *
     * @return single of http response with list of metric names
     */
    @Read
    Single<HttpResponse<ListNamesResponse>> listNames() {
        return Single.just(getListNamesResponse());
    }

    /**
     * Method to read individual metric data.
     * <p>
     * After calling the /metrics endpoint, you can pass the name in
     * like /metrics/foo.bar and the details for the metrics and tags
     * will be returned.
     * <p>
     * Will return a 404 if the metric is not found.
     *
     * @param name the name of the metric to get the details for
     * @return single with metric details response
     */
    @Read
    Single<HttpResponse<MetricDetailsResponse>> getMetricDetails(String name) {
        return Single.just(getMetricDetailsResponse(name));
    }

    /**
     * Read operation to list metric names.  To get the details
     * the method getMetric will be invoked after this one.
     *
     * @return http response with list of metric names
     */
    private HttpResponse<ListNamesResponse> getListNamesResponse() {
        Set<String> names = new LinkedHashSet<>();
        collectNames(names, this.meterRegistries);
        return HttpResponse.ok(new ListNamesResponse(names));
    }

    /**
     * Method to read individual metric data.
     * <p>
     * After calling the /metrics endpoint, you can pass the name in
     * like /metrics/foo.bar and the details for the metrics and tags
     * will be returned.
     * <p>
     * Will return a 404 if the metric is not found.
     *
     * @param name the name of the meter to get the details for.
     * @return single with metric details response
     */
    private HttpResponse<MetricDetailsResponse> getMetricDetailsResponse(String name) {
        List<Tag> tags = Collections.emptyList();
        List<Meter> meters = new ArrayList<>();
        collectMeters(meters, this.meterRegistries, name, tags, new HashSet<>());
        if (meters.isEmpty()) {
            return HttpResponse.notFound();
        }
        Map<Statistic, Double> samples = getSamples(meters);
        Map<String, Set<String>> availableTags = getAvailableTags(meters);
        tags.forEach((t) -> availableTags.remove(t.getKey()));
        return HttpResponse.ok(new MetricDetailsResponse(name,
                asList(samples, Sample::new),
                asList(availableTags, AvailableTag::new)));
    }

    private void collectMeters(List<Meter> meters, Collection<MeterRegistry> meterRegistries, String name,
                               Iterable<Tag> tags, Set<String> meterNames) {
        meterRegistries.forEach(meterRegistry -> {
            if (meterRegistry instanceof CompositeMeterRegistry) {
                ((CompositeMeterRegistry) meterRegistry).getRegistries()
                        .forEach((member) -> collectMeters(meters, member, name, tags, meterNames));
            }
        });
    }

    private void collectMeters(List<Meter> meters, MeterRegistry meterRegistry, String name,
                               Iterable<Tag> tags, Set<String> meterNames) {
        if (!meterNames.contains(name)) {
            meters.addAll(meterRegistry.find(name).tags(tags).meters());
            meterNames.add(name);
        }
    }

    private Map<Statistic, Double> getSamples(List<Meter> meters) {
        Map<Statistic, Double> samples = new LinkedHashMap<>();
        meters.forEach((meter) -> mergeMeasurements(samples, meter));
        return samples;
    }

    private void mergeMeasurements(Map<Statistic, Double> samples, Meter meter) {
        meter.measure().forEach((measurement) -> samples.merge(measurement.getStatistic(),
                measurement.getValue(), mergeFunction(measurement.getStatistic())));
    }

    private BiFunction<Double, Double, Double> mergeFunction(Statistic statistic) {
        return (Statistic.MAX.equals(statistic) ? Double::max : Double::sum);
    }

    /**
     * Get all the available tags.
     *
     * @param meters meters to iterate
     * @return map of the tags
     */
    private Map<String, Set<String>> getAvailableTags(List<Meter> meters) {
        Map<String, Set<String>> availableTags = new HashMap<>();
        meters.forEach((meter) -> mergeAvailableTags(availableTags, meter));
        return availableTags;
    }

    /**
     * Merge the tags from across all the meters.
     *
     * @param availableTags all the tags
     * @param meter         the meter to get tags from
     */
    private void mergeAvailableTags(Map<String, Set<String>> availableTags, Meter meter) {
        meter.getId().getTags().forEach((tag) -> {
            Set<String> value = Collections.singleton(tag.getValue());
            availableTags.merge(tag.getKey(), value, this::merge);
        });
    }

    /**
     * Merge two sets.
     *
     * @param set1 first set
     * @param set2 second set
     * @param <T>  Type
     * @return merged set
     */
    private <T> Set<T> merge(Set<T> set1, Set<T> set2) {
        Set<T> result = new HashSet<>(set1.size() + set2.size());
        result.addAll(set1);
        result.addAll(set2);
        return result;
    }

    private <K, V, T> List<T> asList(Map<K, V> map, BiFunction<K, V, T> mapper) {
        return map.entrySet().stream()
                .map((entry) -> mapper.apply(entry.getKey(), entry.getValue()))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Collect the names from the registries.
     *
     * @param names           set of names
     * @param meterRegistries meter registries
     */
    private void collectNames(Set<String> names, Collection<MeterRegistry> meterRegistries) {
        meterRegistries.forEach(meterRegistry -> {
            if (meterRegistry instanceof CompositeMeterRegistry) {
                ((CompositeMeterRegistry) meterRegistry).getRegistries()
                        .forEach((member) -> collectNames(names, member));
            } else {
                meterRegistry.getMeters().stream().map(this::getName).forEach(names::add);
            }
        });
    }

    private void collectNames(Set<String> names, MeterRegistry meterRegistry) {
        meterRegistry.getMeters().stream().map(this::getName).forEach(names::add);
    }

    /**
     * Get the meter name.
     *
     * @param meter Meter
     * @return name of the meter
     */
    private String getName(Meter meter) {
        return meter.getId().getName();
    }

    /**
     * Response payload for a metric name listing.
     */
    public static final class ListNamesResponse {

        private final Set<String> names;

        /**
         * Object to hold metric names.
         *
         * @param names list of names
         */
        ListNamesResponse(Set<String> names) {
            this.names = names;
        }

        /**
         * Get the names.
         *
         * @return set of names
         */
        public Set<String> getNames() {
            return this.names;
        }
    }

    /**
     * Response payload for a metric name selector.
     */
    public static final class MetricDetailsResponse {

        private final String name;

        private final List<Sample> measurements;

        private final List<AvailableTag> availableTags;

        /**
         * Object to hold metric response for name, value and tags.
         *
         * @param name          the name
         * @param measurements  numerical values
         * @param availableTags tags
         */
        MetricDetailsResponse(String name, List<Sample> measurements,
                              List<AvailableTag> availableTags) {
            this.name = name;
            this.measurements = measurements;
            this.availableTags = availableTags;
        }

        /**
         * Get the name.
         *
         * @return name
         */
        public String getName() {
            return this.name;
        }

        /**
         * Get measurement.
         *
         * @return list of measurements
         */
        public List<Sample> getMeasurements() {
            return this.measurements;
        }

        /**
         * Get tags.
         *
         * @return list of tags
         */
        public List<AvailableTag> getAvailableTags() {
            return this.availableTags;
        }

    }

    /**
     * A set of tags for further dimensional drilldown and their potential values.
     */
    public static final class AvailableTag {

        private final String tag;

        private final Set<String> values;

        /**
         * Available tags for a metric.
         *
         * @param tag    tag name
         * @param values tag values
         */
        AvailableTag(String tag, Set<String> values) {
            this.tag = tag;
            this.values = values;
        }

        /**
         * Get tag name.
         *
         * @return tag name
         */
        public String getTag() {
            return this.tag;
        }

        /**
         * Get tag values.
         *
         * @return list of tag values
         */
        public Set<String> getValues() {
            return this.values;
        }

    }

    /**
     * A measurement sample combining a {@link Statistic statistic} and a value.
     */
    public static final class Sample {

        private final Statistic statistic;

        private final Double value;

        /**
         * Numerical sample of the metrics.
         *
         * @param statistic measurement name
         * @param value     measurement value
         */
        Sample(Statistic statistic, Double value) {
            this.statistic = statistic;
            this.value = value;
        }

        /**
         * Measurement name.
         *
         * @return measurement name
         */
        public Statistic getStatistic() {
            return this.statistic;
        }

        /**
         * Measurement value.
         *
         * @return measurement value
         */
        public Double getValue() {
            return this.value;
        }

        @Override
        public String toString() {
            return "MeasurementSample{" + "statistic=" + this.statistic + ", value="
                    + this.value + '}';
        }

    }
}

