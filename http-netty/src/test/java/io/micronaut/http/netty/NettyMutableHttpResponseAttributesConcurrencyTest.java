package io.micronaut.http.netty;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.RouteMetadataHolder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The route metadata of a {@link NettyMutableHttpResponse} is kept in typed fields that the attribute map reads
 * and writes through. Readers on other threads, through the typed accessors or
 * {@code getAttribute(name)}, must never observe the metadata as missing while the map is first
 * created, and only one map may ever be published. A typed setter racing with that first call
 * must not have its write lost, and once the setter has returned no reader may see the old value.
 */
@SuppressWarnings("removal")
class NettyMutableHttpResponseAttributesConcurrencyTest {
    private static final int READERS = 3;
    private static final int ROUNDS = 20_000;
    private static final int READS_PER_ROUND = 50;
    private static final int WRITE_ROUNDS = 50_000;

    @Test
    void concurrentReadersNeverLoseMetadataDuringMaterialisation() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(READERS + 1);
        try {
            AtomicInteger lostReads = new AtomicInteger();
            for (int round = 0; round < ROUNDS; round++) {
                NettyMutableHttpResponse<Object> response = new NettyMutableHttpResponse<>(ConversionService.SHARED);
                Object routeMatch = new Object();
                Object routeInfo = new Object();
                response.setRouteMatchMetadata(routeMatch);
                response.setRouteInfoMetadata(routeInfo);
                response.setUriTemplateMetadata("/foo/{id}");

                CyclicBarrier barrier = new CyclicBarrier(READERS + 1);
                List<Future<MutableConvertibleValues<Object>>> maps = new ArrayList<>();
                maps.add(pool.submit((Callable<MutableConvertibleValues<Object>>) () -> {
                    barrier.await();
                    return response.getAttributes();
                }));
                for (int r = 0; r < READERS; r++) {
                    maps.add(pool.submit(() -> {
                        barrier.await();
                        for (int i = 0; i < READS_PER_ROUND; i++) {
                            if (!read(response, routeMatch, routeInfo)) {
                                lostReads.incrementAndGet();
                            }
                        }
                        return response.getAttributes();
                    }));
                }
                MutableConvertibleValues<Object> published = maps.get(0).get();
                for (Future<MutableConvertibleValues<Object>> map : maps) {
                    assertSame(published, map.get(), "more than one attribute map was published");
                }
                assertSame(routeMatch, published.getValue(HttpAttributes.ROUTE_MATCH.toString()));
                assertSame(routeInfo, published.getValue(HttpAttributes.ROUTE_INFO.toString()));
                assertEquals("/foo/{id}", published.getValue(HttpAttributes.URI_TEMPLATE.toString()));
            }
            assertEquals(0, lostReads.get(), "reads that observed missing route metadata");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentWritesAreNotLostToMaterialisation() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            int lostWrites = 0;
            for (int round = 0; round < WRITE_ROUNDS; round++) {
                NettyMutableHttpResponse<Object> response = new NettyMutableHttpResponse<>(ConversionService.SHARED);
                response.setRouteMatchMetadata(new Object());
                response.setRouteInfoMetadata(new Object());
                response.setUriTemplateMetadata("/old");
                Object routeMatch = new Object();
                Object routeInfo = new Object();

                CyclicBarrier barrier = new CyclicBarrier(2);
                Future<?> materialiser = pool.submit(() -> {
                    barrier.await();
                    return response.getAttributes();
                });
                Future<?> writer = pool.submit(() -> {
                    barrier.await();
                    response.setRouteMatchMetadata(routeMatch);
                    response.setRouteInfoMetadata(routeInfo);
                    response.setUriTemplateMetadata("/new");
                    return null;
                });
                materialiser.get();
                writer.get();
                RouteMetadataHolder holder = response;
                if (holder.getRouteMatchMetadata() != routeMatch
                    || holder.getRouteInfoMetadata() != routeInfo
                    || !"/new".equals(holder.getUriTemplateMetadata())
                    || response.getAttributes().getValue(HttpAttributes.ROUTE_MATCH.toString()) != routeMatch
                    || response.getAttributes().getValue(HttpAttributes.ROUTE_INFO.toString()) != routeInfo
                    || !"/new".equals(response.getAttributes().getValue(HttpAttributes.URI_TEMPLATE.toString()))) {
                    lostWrites++;
                }
            }
            assertEquals(0, lostWrites, "typed metadata writes lost to a concurrent materialisation");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void readsAfterASetterReturnedNeverSeeTheOldValue() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            AtomicInteger staleReads = new AtomicInteger();
            for (int round = 0; round < WRITE_ROUNDS; round++) {
                NettyMutableHttpResponse<Object> response = new NettyMutableHttpResponse<>(ConversionService.SHARED);
                response.setRouteMatchMetadata(new Object());
                response.setRouteInfoMetadata(new Object());
                response.setUriTemplateMetadata("/old");
                Object routeMatch = new Object();
                Object routeInfo = new Object();
                AtomicBoolean written = new AtomicBoolean();

                CyclicBarrier barrier = new CyclicBarrier(3);
                Future<?> materialiser = pool.submit(() -> {
                    barrier.await();
                    return response.getAttributes();
                });
                Future<?> writer = pool.submit(() -> {
                    barrier.await();
                    response.setRouteMatchMetadata(routeMatch);
                    response.setRouteInfoMetadata(routeInfo);
                    response.setUriTemplateMetadata("/new");
                    written.set(true);
                    return null;
                });
                Future<?> reader = pool.submit(() -> {
                    barrier.await();
                    while (!written.get()) {
                        Thread.onSpinWait();
                    }
                    // the setters have returned: no read may return the old values any more
                    RouteMetadataHolder holder = response;
                    if (holder.getRouteMatchMetadata() != routeMatch
                        || response.getAttribute(HttpAttributes.ROUTE_MATCH).orElse(null) != routeMatch
                        || response.getAttributes().getValue(HttpAttributes.ROUTE_MATCH.toString()) != routeMatch
                        || holder.getRouteInfoMetadata() != routeInfo
                        || response.getAttribute(HttpAttributes.ROUTE_INFO).orElse(null) != routeInfo
                        || !"/new".equals(holder.getUriTemplateMetadata())
                        || !"/new".equals(response.getAttribute(HttpAttributes.URI_TEMPLATE).orElse(null))) {
                        staleReads.incrementAndGet();
                    }
                    return null;
                });
                materialiser.get();
                writer.get();
                reader.get();
            }
            assertEquals(0, staleReads.get(), "reads that saw route metadata older than a completed write");
        } finally {
            pool.shutdownNow();
        }
    }

    private static boolean read(NettyMutableHttpResponse<Object> response, Object routeMatch, Object routeInfo) {
        RouteMetadataHolder holder = response;
        return holder.getRouteMatchMetadata() == routeMatch
            && holder.getRouteInfoMetadata() == routeInfo
            && "/foo/{id}".equals(holder.getUriTemplateMetadata())
            && response.getAttribute(HttpAttributes.ROUTE_MATCH).orElse(null) == routeMatch
            && response.getAttribute(HttpAttributes.ROUTE_INFO).orElse(null) == routeInfo
            && "/foo/{id}".equals(response.getAttribute(HttpAttributes.URI_TEMPLATE).orElse(null));
    }
}
