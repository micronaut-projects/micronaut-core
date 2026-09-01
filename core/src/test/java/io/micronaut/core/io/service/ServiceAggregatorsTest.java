package io.micronaut.core.io.service;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceAggregatorsTest {

    private static final String AGGREGATOR_RESOURCE =
        "META-INF/services/" + ServiceAggregator.SERVICE_NAME;
    private static final String ROOT_RESOURCE =
        "META-INF/services/" + ServiceAggregatorRoot.SERVICE_NAME;

    private static final String SERVICE_A = "test.ServiceA";
    private static final String SERVICE_B = "test.ServiceB";

    @Test
    void collectsEveryChunkOfTheRequestedServiceInOrder() {
        ClassLoader classLoader = loaderFor(Map.of(AGGREGATOR_RESOURCE, Aggregator.class.getName()));

        List<Object> collected = new ArrayList<>();
        assertTrue(ServiceAggregators.collect(classLoader, SERVICE_A, null, collected::add));

        assertEquals(List.of("a0", "a1", "a2", "a3"), collected);
    }

    @Test
    void doesNotTouchServicesThatWereNotAskedFor() {
        ClassLoader classLoader = loaderFor(Map.of(AGGREGATOR_RESOURCE, Aggregator.class.getName()));

        List<Object> collected = new ArrayList<>();
        ServiceAggregators.collect(classLoader, SERVICE_B, null, collected::add);

        assertEquals(List.of("b0"), collected);
    }

    @Test
    void reportsNothingWhenNoAggregatorProvidesTheService() {
        ClassLoader classLoader = loaderFor(Map.of(AGGREGATOR_RESOURCE, Aggregator.class.getName()));

        List<Object> collected = new ArrayList<>();
        assertFalse(ServiceAggregators.collect(classLoader, "test.Unknown", null, collected::add));

        assertTrue(collected.isEmpty());
    }

    @Test
    void appliesThePredicateBeforeTheConsumerSeesTheValue() {
        ClassLoader classLoader = loaderFor(Map.of(AGGREGATOR_RESOURCE, Aggregator.class.getName()));

        List<Object> collected = new ArrayList<>();
        ServiceAggregators.collect(classLoader, SERVICE_A, v -> !"a2".equals(v), collected::add);

        assertEquals(List.of("a0", "a1", "a3"), collected);
    }

    @Test
    void prefersTheRootOverThePerModuleEntries() {
        // the root names a different aggregator, so the result shows which one was consulted
        ClassLoader classLoader = loaderFor(Map.of(
            AGGREGATOR_RESOURCE, Aggregator.class.getName(),
            ROOT_RESOURCE, Root.class.getName()
        ));

        List<Object> collected = new ArrayList<>();
        ServiceAggregators.collect(classLoader, SERVICE_A, null, collected::add);

        assertEquals(List.of("root"), collected);
    }

    /**
     * A class loader that serves the given resources and nothing else, so each test sees an
     * isolated classpath. Resource lookups fall back to the real loader for everything else, which
     * is what loading the aggregator classes themselves needs.
     */
    private static ClassLoader loaderFor(Map<String, String> resources) {
        return new ClassLoader(ServiceAggregatorsTest.class.getClassLoader()) {
            @Override
            public Enumeration<URL> getResources(String name) throws IOException {
                String content = resources.get(name);
                if (content == null) {
                    return super.getResources(name);
                }
                return Collections.enumeration(List.of(inMemoryUrl(name, content)));
            }
        };
    }

    private static URL inMemoryUrl(String name, String content) throws IOException {
        return new URL(null, "mem:/" + name, new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL u) {
                return new URLConnection(u) {
                    @Override
                    public void connect() {
                        // nothing to connect to
                    }

                    @Override
                    public InputStream getInputStream() {
                        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
                    }
                };
            }
        });
    }

    public static final class Aggregator implements ServiceAggregator {

        @Override
        public Set<String> getServiceNames() {
            return Set.of(SERVICE_A, SERVICE_B);
        }

        @Override
        public int getChunkCount(String serviceName) {
            if (SERVICE_A.equals(serviceName)) {
                return 2;
            }
            return SERVICE_B.equals(serviceName) ? 1 : 0;
        }

        @Override
        public void collect(String serviceName, int chunk, Consumer<Object> consumer) {
            if (SERVICE_A.equals(serviceName)) {
                if (chunk == 0) {
                    consumer.accept("a0");
                    consumer.accept("a1");
                } else if (chunk == 1) {
                    consumer.accept("a2");
                    consumer.accept("a3");
                }
            } else if (SERVICE_B.equals(serviceName) && chunk == 0) {
                consumer.accept("b0");
            }
        }
    }

    public static final class RootAggregator implements ServiceAggregator {

        @Override
        public Set<String> getServiceNames() {
            return Set.of(SERVICE_A);
        }

        @Override
        public int getChunkCount(String serviceName) {
            return SERVICE_A.equals(serviceName) ? 1 : 0;
        }

        @Override
        public void collect(String serviceName, int chunk, Consumer<Object> consumer) {
            if (SERVICE_A.equals(serviceName) && chunk == 0) {
                consumer.accept("root");
            }
        }
    }

    public static final class Root implements ServiceAggregatorRoot {

        @Override
        public List<ServiceAggregator> getAggregators() {
            return List.of(new RootAggregator());
        }
    }
}
