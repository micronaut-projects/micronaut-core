package io.micronaut.configuration.metrics.management.endpoint;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micronaut.context.annotation.Requires;
import io.micronaut.management.endpoint.Endpoint;
import io.micronaut.management.endpoint.Read;
import io.reactivex.Single;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.METRICS_ENABLED;

/**
 * Provides a metrics endpoint to visualize metrics.
 */
@Endpoint(value = MetricsEndpoint.NAME, defaultSensitive = MetricsEndpoint.DEFAULT_SENSITIVE)
@Requires(beans = MeterRegistry.class)
@Requires(property = METRICS_ENABLED, value = "true", defaultValue = "true")
public class MetricsEndpoint {

    /**
     * If the endpoint is sensitive if no configuration is provided.
     */
    static final boolean DEFAULT_SENSITIVE = false;

    /**
     * Constant for metrics.
     */
    static final String NAME = "metrics";

    private final MeterRegistry meterRegistry;

    /**
     * Constructor for metrics endpoint.
     *
     * @param meterRegistry Meter Registry
     */
    public MetricsEndpoint(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Read operation to list metric names.  To get the details
     * the method getMetric below will be invoked.
     *
     * @return list of metric names
     */
    @Read
    Single<ListNamesResponse> listNames() {
        Set<String> names = new LinkedHashSet<>();
        collectNames(names, this.meterRegistry);
        return Single.just(new ListNamesResponse(names));
    }

    /**
     * Method to read individual metric data.
     * <p>
     * After calling the /metrics endpoint, you can pass the name in
     * like /metrics/foo.bar and the details for the metrics and tags
     * will be returned.
     *
     * @return Optional with metric response
     */
    @Read
    Single<Optional<MetricResponse>> getMetric(String name) {
        List<Tag> tags = Collections.emptyList();
        List<Meter> meters = new ArrayList<>();
        collectMeters(meters, this.meterRegistry, name, tags);
        if (meters.isEmpty()) {
            //Perhaps return something else here?
            return Single.just(Optional.empty());
        }
        Map<Statistic, Double> samples = getSamples(meters);
        Map<String, Set<String>> availableTags = getAvailableTags(meters);
        tags.forEach((t) -> availableTags.remove(t.getKey()));
        return Single.just(Optional.of(
                new MetricResponse(name,
                        asList(samples, Sample::new),
                        asList(availableTags, AvailableTag::new))));
    }

    private void collectMeters(List<Meter> meters, MeterRegistry registry, String name,
                               Iterable<Tag> tags) {
        if (registry instanceof CompositeMeterRegistry) {
            ((CompositeMeterRegistry) registry).getRegistries()
                    .forEach((member) -> collectMeters(meters, member, name, tags));
        } else {
            meters.addAll(registry.find(name).tags(tags).meters());
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
     * @param names    set of names
     * @param registry meter registry
     */
    private void collectNames(Set<String> names, MeterRegistry registry) {
        if (registry instanceof CompositeMeterRegistry) {
            ((CompositeMeterRegistry) registry).getRegistries()
                    .forEach((member) -> collectNames(names, member));
        } else {
            registry.getMeters().stream().map(this::getName).forEach(names::add);
        }
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
    public static final class MetricResponse {

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
        MetricResponse(String name, List<Sample> measurements,
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

