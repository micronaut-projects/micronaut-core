package io.micronaut.http.server.netty;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.RouteMetadataHolder;
import io.micronaut.http.netty.body.NettyByteBodyFactory;
import io.micronaut.http.server.HttpServerConfiguration;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
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
 * The route metadata of a {@link NettyHttpRequest} is kept in typed fields that the attribute map reads
 * and writes through. Readers on other threads, through the typed accessors or
 * {@code getAttribute(name)}, must never observe the metadata as missing while the map is first
 * created, and only one map may ever be published. A typed setter racing with that first call
 * must not have its write lost, and once the setter has returned no reader may see the old value.
 */
@SuppressWarnings("removal")
class NettyHttpRequestAttributesConcurrencyTest {
    private static final int READERS = 3;
    private static final int ROUNDS = 20_000;
    private static final int READS_PER_ROUND = 50;
    private static final int WRITE_ROUNDS = 50_000;

    @Test
    void concurrentReadersNeverLoseMetadataDuringMaterialisation() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        ChannelHandlerContext ctx = channel.pipeline().firstContext();
        HttpServerConfiguration configuration = new HttpServerConfiguration();
        ExecutorService pool = Executors.newFixedThreadPool(READERS + 1);
        try {
            AtomicInteger lostReads = new AtomicInteger();
            for (int round = 0; round < ROUNDS; round++) {
                NettyHttpRequest<Object> request = new NettyHttpRequest<>(
                    new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/foo/1"),
                    NettyByteBodyFactory.empty(),
                    ctx,
                    ConversionService.SHARED,
                    configuration
                );
                Object routeMatch = new Object();
                Object routeInfo = new Object();
                request.setRouteMatchMetadata(routeMatch);
                request.setRouteInfoMetadata(routeInfo);
                request.setUriTemplateMetadata("/foo/{id}");

                CyclicBarrier barrier = new CyclicBarrier(READERS + 1);
                List<Future<MutableConvertibleValues<Object>>> maps = new ArrayList<>();
                maps.add(pool.submit((Callable<MutableConvertibleValues<Object>>) () -> {
                    barrier.await();
                    return request.getAttributes();
                }));
                for (int r = 0; r < READERS; r++) {
                    maps.add(pool.submit(() -> {
                        barrier.await();
                        for (int i = 0; i < READS_PER_ROUND; i++) {
                            if (!read(request, routeMatch, routeInfo)) {
                                lostReads.incrementAndGet();
                            }
                        }
                        return request.getAttributes();
                    }));
                }
                MutableConvertibleValues<Object> published = maps.get(0).get();
                for (Future<MutableConvertibleValues<Object>> map : maps) {
                    assertSame(published, map.get(), "more than one attribute map was published");
                }
                assertSame(routeMatch, published.getValue(HttpAttributes.ROUTE_MATCH.toString()));
                assertSame(routeInfo, published.getValue(HttpAttributes.ROUTE_INFO.toString()));
                assertEquals("/foo/{id}", published.getValue(HttpAttributes.URI_TEMPLATE.toString()));
                request.release();
            }
            assertEquals(0, lostReads.get(), "reads that observed missing route metadata");
        } finally {
            pool.shutdownNow();
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void concurrentWritesAreNotLostToMaterialisation() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        ChannelHandlerContext ctx = channel.pipeline().firstContext();
        HttpServerConfiguration configuration = new HttpServerConfiguration();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            int lostWrites = 0;
            for (int round = 0; round < WRITE_ROUNDS; round++) {
                NettyHttpRequest<Object> request = new NettyHttpRequest<>(
                    new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/foo/1"),
                    NettyByteBodyFactory.empty(),
                    ctx,
                    ConversionService.SHARED,
                    configuration
                );
                request.setRouteMatchMetadata(new Object());
                request.setRouteInfoMetadata(new Object());
                request.setUriTemplateMetadata("/old");
                Object routeMatch = new Object();
                Object routeInfo = new Object();

                CyclicBarrier barrier = new CyclicBarrier(2);
                Future<?> materialiser = pool.submit(() -> {
                    barrier.await();
                    return request.getAttributes();
                });
                Future<?> writer = pool.submit(() -> {
                    barrier.await();
                    request.setRouteMatchMetadata(routeMatch);
                    request.setRouteInfoMetadata(routeInfo);
                    request.setUriTemplateMetadata("/new");
                    return null;
                });
                materialiser.get();
                writer.get();
                RouteMetadataHolder holder = request;
                if (holder.getRouteMatchMetadata() != routeMatch
                    || holder.getRouteInfoMetadata() != routeInfo
                    || !"/new".equals(holder.getUriTemplateMetadata())
                    || request.getAttributes().getValue(HttpAttributes.ROUTE_MATCH.toString()) != routeMatch
                    || request.getAttributes().getValue(HttpAttributes.ROUTE_INFO.toString()) != routeInfo
                    || !"/new".equals(request.getAttributes().getValue(HttpAttributes.URI_TEMPLATE.toString()))) {
                    lostWrites++;
                }
                request.release();
            }
            assertEquals(0, lostWrites, "typed metadata writes lost to a concurrent materialisation");
        } finally {
            pool.shutdownNow();
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void readsAfterASetterReturnedNeverSeeTheOldValue() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        ChannelHandlerContext ctx = channel.pipeline().firstContext();
        HttpServerConfiguration configuration = new HttpServerConfiguration();
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            AtomicInteger staleReads = new AtomicInteger();
            for (int round = 0; round < WRITE_ROUNDS; round++) {
                NettyHttpRequest<Object> request = new NettyHttpRequest<>(
                    new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/foo/1"),
                    NettyByteBodyFactory.empty(),
                    ctx,
                    ConversionService.SHARED,
                    configuration
                );
                request.setRouteMatchMetadata(new Object());
                request.setRouteInfoMetadata(new Object());
                request.setUriTemplateMetadata("/old");
                Object routeMatch = new Object();
                Object routeInfo = new Object();
                AtomicBoolean written = new AtomicBoolean();

                CyclicBarrier barrier = new CyclicBarrier(3);
                Future<?> materialiser = pool.submit(() -> {
                    barrier.await();
                    return request.getAttributes();
                });
                Future<?> writer = pool.submit(() -> {
                    barrier.await();
                    request.setRouteMatchMetadata(routeMatch);
                    request.setRouteInfoMetadata(routeInfo);
                    request.setUriTemplateMetadata("/new");
                    written.set(true);
                    return null;
                });
                Future<?> reader = pool.submit(() -> {
                    barrier.await();
                    while (!written.get()) {
                        Thread.onSpinWait();
                    }
                    // the setters have returned: no read may return the old values any more
                    RouteMetadataHolder holder = request;
                    if (holder.getRouteMatchMetadata() != routeMatch
                        || request.getAttribute(HttpAttributes.ROUTE_MATCH).orElse(null) != routeMatch
                        || request.getAttributes().getValue(HttpAttributes.ROUTE_MATCH.toString()) != routeMatch
                        || holder.getRouteInfoMetadata() != routeInfo
                        || request.getAttribute(HttpAttributes.ROUTE_INFO).orElse(null) != routeInfo
                        || !"/new".equals(holder.getUriTemplateMetadata())
                        || !"/new".equals(request.getAttribute(HttpAttributes.URI_TEMPLATE).orElse(null))) {
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

    private static boolean read(NettyHttpRequest<Object> request, Object routeMatch, Object routeInfo) {
        RouteMetadataHolder holder = request;
        return holder.getRouteMatchMetadata() == routeMatch
            && holder.getRouteInfoMetadata() == routeInfo
            && "/foo/{id}".equals(holder.getUriTemplateMetadata())
            && request.getAttribute(HttpAttributes.ROUTE_MATCH).orElse(null) == routeMatch
            && request.getAttribute(HttpAttributes.ROUTE_INFO).orElse(null) == routeInfo
            && "/foo/{id}".equals(request.getAttribute(HttpAttributes.URI_TEMPLATE).orElse(null));
    }
}
