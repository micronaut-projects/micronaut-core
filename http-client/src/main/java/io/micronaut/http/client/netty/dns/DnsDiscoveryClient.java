/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.http.client.netty.dns;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.discovery.AsyncDiscoveryClient;
import io.micronaut.discovery.DiscoveryClient;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.http.netty.channel.DefaultEventLoopGroupConfiguration;
import io.micronaut.http.netty.channel.EventLoopGroupConfiguration;
import io.micronaut.http.netty.channel.EventLoopGroupFactory;
import io.micronaut.http.netty.channel.EventLoopGroupRegistry;
import io.micronaut.http.netty.channel.NettyChannelType;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.ChannelFactory;
import io.netty.channel.EventLoop;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DefaultDnsRecordDecoder;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.resolver.dns.DnsNameResolver;
import io.netty.resolver.dns.DnsNameResolverBuilder;
import io.netty.resolver.dns.DnsServerAddressStreamProvider;
import io.netty.resolver.dns.DnsServerAddressStreamProviders;
import io.netty.resolver.dns.NoopAuthoritativeDnsServerCache;
import io.netty.resolver.dns.NoopDnsCache;
import io.netty.resolver.dns.NoopDnsCnameCache;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ScheduledFuture;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

/**
 * A {@link DiscoveryClient} that finds the instances of the services configured under
 * {@value DnsServiceConfiguration#PREFIX} with DNS address (A/AAAA) or SRV records. The records
 * are looked up on the default Netty event loop group when a service is first requested, then
 * again when their TTL expires. A lookup that fails keeps the instances of the last successful
 * one, for at most {@link DnsServiceConfiguration#getMaxStale()}; an answer without records
 * leaves the service without instances.
 *
 * <p>The name servers are those of the system, unless the context has a
 * {@link DnsServerAddressStreamProvider} bean.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
@Singleton
@Requires(property = DnsServiceConfiguration.PREFIX)
public final class DnsDiscoveryClient implements DiscoveryClient, AsyncDiscoveryClient {

    /**
     * The instance metadata holding the SRV weight, read by the weighted load balancer strategy.
     */
    public static final String WEIGHT = "weight";

    private static final Logger LOG = LoggerFactory.getLogger(DnsDiscoveryClient.class);

    private final Map<String, ServiceWatcher> watchers;
    private final List<String> serviceIds;

    /**
     * @param configurations         The DNS discovery of the services
     * @param eventLoopGroupRegistry The event loop groups
     * @param eventLoopGroupFactory  The Netty transport
     * @param beanContext            The bean context
     */
    @SuppressWarnings("resource") // the event loop group belongs to the registry
    DnsDiscoveryClient(List<DnsServiceConfiguration> configurations,
                       EventLoopGroupRegistry eventLoopGroupRegistry,
                       EventLoopGroupFactory eventLoopGroupFactory,
                       BeanContext beanContext) {
        EventLoopGroupConfiguration groupConfiguration = beanContext
            .findBean(EventLoopGroupConfiguration.class, Qualifiers.byName(EventLoopGroupConfiguration.DEFAULT))
            .orElseGet(DefaultEventLoopGroupConfiguration::new);
        // read here, on the thread creating the bean: the system name servers come from a file
        DnsServerAddressStreamProvider nameServers = beanContext.findBean(DnsServerAddressStreamProvider.class)
            .orElseGet(DnsServerAddressStreamProviders::platformDefault);
        ChannelFactory<DatagramChannel> datagramChannels =
            () -> (DatagramChannel) eventLoopGroupFactory.channelInstance(NettyChannelType.DATAGRAM_SOCKET, groupConfiguration);
        ChannelFactory<SocketChannel> socketChannels =
            () -> (SocketChannel) eventLoopGroupFactory.channelInstance(NettyChannelType.CLIENT_SOCKET, groupConfiguration);
        Map<String, ServiceWatcher> map = new LinkedHashMap<>();
        for (DnsServiceConfiguration configuration : configurations) {
            String id = NameUtils.hyphenate(configuration.getServiceId());
            EventLoop eventLoop = eventLoopGroupRegistry.getDefaultEventLoopGroup().next();
            map.put(id, new ServiceWatcher(id, configuration, eventLoop, datagramChannels, socketChannels, nameServers));
        }
        this.watchers = Collections.unmodifiableMap(map);
        this.serviceIds = List.copyOf(map.keySet());
    }

    @Override
    public String getDescription() {
        return "dns";
    }

    @Override
    public Publisher<List<ServiceInstance>> getInstances(String serviceId) {
        return Mono.fromCompletionStage(() -> getInstancesAsync(serviceId));
    }

    @Override
    public Publisher<List<String>> getServiceIds() {
        return Mono.just(serviceIds);
    }

    @Override
    public CompletionStage<List<ServiceInstance>> getInstancesAsync(String serviceId) {
        ServiceWatcher watcher = watchers.get(NameUtils.hyphenate(serviceId));
        if (watcher == null) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        return watcher.instances();
    }

    @Override
    public CompletionStage<List<String>> getServiceIdsAsync() {
        return CompletableFuture.completedFuture(serviceIds);
    }

    @Override
    @PreDestroy
    public void close() {
        for (ServiceWatcher watcher : watchers.values()) {
            watcher.close();
        }
    }

    /**
     * Looks up the records of one service and keeps the last instances found.
     */
    private static final class ServiceWatcher {
        private final String serviceId;
        private final DnsServiceConfiguration configuration;
        private final EventLoop eventLoop;
        private final ChannelFactory<DatagramChannel> datagramChannels;
        private final ChannelFactory<SocketChannel> socketChannels;
        private final DnsServerAddressStreamProvider nameServers;
        private final CompletableFuture<List<ServiceInstance>> first = new CompletableFuture<>();
        private final AtomicReference<List<ServiceInstance>> instances = new AtomicReference<>(Collections.emptyList());
        private volatile boolean started;
        private volatile boolean closed;
        // the state below is only used on the event loop
        private @Nullable DnsNameResolver resolver;
        private @Nullable ScheduledFuture<?> next;
        private boolean failing;
        private long lastSuccessNanos;

        ServiceWatcher(String serviceId, DnsServiceConfiguration configuration, EventLoop eventLoop,
                       ChannelFactory<DatagramChannel> datagramChannels,
                       ChannelFactory<SocketChannel> socketChannels,
                       DnsServerAddressStreamProvider nameServers) {
            this.serviceId = serviceId;
            this.configuration = configuration;
            this.eventLoop = eventLoop;
            this.datagramChannels = datagramChannels;
            this.socketChannels = socketChannels;
            this.nameServers = nameServers;
        }

        private List<ServiceInstance> current() {
            return Objects.requireNonNull(instances.get());
        }

        CompletionStage<List<ServiceInstance>> instances() {
            if (first.isDone()) {
                return CompletableFuture.completedFuture(current());
            }
            if (!started) {
                synchronized (this) {
                    if (!started) {
                        started = true;
                        eventLoop.execute(this::refresh);
                    }
                }
            }
            // a copy, so that a caller cancelling its stage does not cancel the others
            return first.thenApply(list -> list);
        }

        void close() {
            closed = true;
            if (!eventLoop.isShuttingDown()) {
                eventLoop.execute(this::closeOnLoop);
            }
            first.complete(current());
        }

        private void closeOnLoop() {
            ScheduledFuture<?> scheduled = next;
            if (scheduled != null) {
                scheduled.cancel(false);
            }
            DnsNameResolver r = resolver;
            if (r != null) {
                r.close();
                resolver = null;
            }
        }

        @SuppressWarnings("resource") // closed by closeOnLoop
        private DnsNameResolver resolver() {
            DnsNameResolver r = resolver;
            if (r == null) {
                r = new DnsNameResolverBuilder(eventLoop)
                    .datagramChannelFactory(datagramChannels)
                    // a truncated UDP answer is queried again over TCP
                    .socketChannelFactory(socketChannels)
                    .resolveCache(NoopDnsCache.INSTANCE)
                    .cnameCache(NoopDnsCnameCache.INSTANCE)
                    .authoritativeDnsServerCache(NoopAuthoritativeDnsServerCache.INSTANCE)
                    .queryTimeoutMillis(configuration.getQueryTimeout().toMillis())
                    .nameServerProvider(nameServers)
                    .build();
                resolver = r;
            }
            return r;
        }

        private void refresh() {
            if (closed) {
                return;
            }
            String name = configuration.getHostname();
            if (!name.endsWith(".")) {
                // a fully qualified name: the search domains do not apply
                name = name + ".";
            }
            List<DnsRecordType> types = switch (configuration.getRecordType()) {
                case A -> List.of(DnsRecordType.A);
                case AAAA -> List.of(DnsRecordType.AAAA);
                case ANY_ADDRESS -> List.of(DnsRecordType.A, DnsRecordType.AAAA);
                case SRV -> List.of(DnsRecordType.SRV);
            };
            DnsNameResolver r;
            List<DefaultDnsQuestion> questions = new ArrayList<>(types.size());
            try {
                r = resolver();
                for (DnsRecordType type : types) {
                    // throws for an invalid host name, such as one with a label longer than 63 characters
                    questions.add(new DefaultDnsQuestion(name, type));
                }
            } catch (RuntimeException e) {
                onFailure(e);
                return;
            }
            List<DnsRecord> answers = new ArrayList<>();
            AtomicInteger pending = new AtomicInteger(questions.size());
            List<Throwable> failures = new ArrayList<>(1);
            for (DefaultDnsQuestion question : questions) {
                query(r, question, (records, error) -> {
                    // the callbacks run on the event loop of the resolver
                    if (error != null) {
                        failures.add(error);
                    } else {
                        answers.addAll(records);
                    }
                    if (pending.decrementAndGet() == 0) {
                        try {
                            onAnswers(answers, failures.isEmpty() ? null : failures.get(0));
                        } finally {
                            for (DnsRecord record : answers) {
                                ReferenceCountUtil.release(record);
                            }
                        }
                    }
                });
            }
        }

        /**
         * Queries one question. A response without the requested records is a successful answer,
         * unlike an error response such as NXDOMAIN; a CNAME is followed.
         *
         * @param r        The resolver
         * @param question The question
         * @param callback Called with the retained records of the answer, or with the failure
         */
        private void query(DnsNameResolver r, DefaultDnsQuestion question, BiConsumer<List<DnsRecord>, @Nullable Throwable> callback) {
            r.query(question).addListener((Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> f) -> {
                if (!f.isSuccess()) {
                    callback.accept(List.of(), f.cause());
                    return;
                }
                AddressedEnvelope<DnsResponse, InetSocketAddress> envelope = f.getNow();
                List<DnsRecord> records = new ArrayList<>();
                boolean cname = false;
                DnsResponseCode code;
                try {
                    DnsResponse response = envelope.content();
                    code = response.code();
                    for (int i = 0; i < response.count(DnsSection.ANSWER); i++) {
                        DnsRecord record = response.recordAt(DnsSection.ANSWER, i);
                        if (record.type() == question.type()) {
                            records.add(ReferenceCountUtil.retain(record));
                        } else if (record.type() == DnsRecordType.CNAME) {
                            cname = true;
                        }
                    }
                } finally {
                    envelope.release();
                }
                if (code != DnsResponseCode.NOERROR) {
                    records.forEach(ReferenceCountUtil::release);
                    callback.accept(List.of(), new UnknownHostException(code + " for " + question.name() + " " + question.type().name()));
                } else if (records.isEmpty() && cname) {
                    // an alias: the resolver follows it
                    r.resolveAll(question).addListener((Future<List<DnsRecord>> all) -> {
                        if (all.isSuccess()) {
                            callback.accept(all.getNow(), null);
                        } else {
                            callback.accept(List.of(), all.cause());
                        }
                    });
                } else {
                    callback.accept(records, null);
                }
            });
        }

        private void onAnswers(List<DnsRecord> answers, @Nullable Throwable failure) {
            if (closed) {
                return;
            }
            List<ServiceInstance> found = new ArrayList<>();
            long ttl;
            try {
                ttl = parse(answers, found);
            } catch (RuntimeException e) {
                onFailure(e);
                return;
            }
            if (found.isEmpty() && failure != null) {
                onFailure(failure);
                return;
            }
            // an answer without records is a successful lookup: the service has no instance
            instances.set(Collections.unmodifiableList(found));
            lastSuccessNanos = System.nanoTime();
            if (failing) {
                failing = false;
                LOG.info("DNS discovery of service [{}] recovered: {} instance(s)", serviceId, found.size());
            } else if (LOG.isDebugEnabled()) {
                LOG.debug("DNS discovery of service [{}] found {}", serviceId, found);
            }
            first.complete(current());
            schedule(refreshDelay(Duration.ofSeconds(ttl)));
        }

        private void onFailure(Throwable cause) {
            if (!failing) {
                failing = true;
                LOG.warn("DNS discovery of service [{}] failed to look up [{}], keeping the {} known instance(s) for at most {}: {}",
                    serviceId, configuration.getHostname(), current().size(), configuration.getMaxStale(), cause.toString());
            }
            if (!current().isEmpty() && System.nanoTime() - lastSuccessNanos > configuration.getMaxStale().toNanos()) {
                LOG.warn("DNS discovery of service [{}] dropped its {} instance(s), found more than {} ago",
                    serviceId, current().size(), configuration.getMaxStale());
                instances.set(Collections.emptyList());
            }
            first.complete(current());
            // a failed lookup is retried after min-ttl, whatever the refresh interval
            schedule(configuration.getMinTtl());
        }

        private Duration refreshDelay(Duration ttl) {
            Duration interval = configuration.getRefreshInterval();
            if (interval != null) {
                return interval;
            }
            Duration delay = ttl;
            if (delay.compareTo(configuration.getMinTtl()) < 0) {
                delay = configuration.getMinTtl();
            }
            if (delay.compareTo(configuration.getMaxTtl()) > 0) {
                delay = configuration.getMaxTtl();
            }
            return delay;
        }

        private void schedule(Duration delay) {
            if (closed) {
                return;
            }
            next = eventLoop.schedule(this::refresh, Math.max(1, delay.toMillis()), TimeUnit.MILLISECONDS);
        }

        /**
         * Adds the instances of the records to {@code found}.
         *
         * @return The lowest TTL of the records used, in seconds
         */
        private long parse(List<DnsRecord> answers, List<ServiceInstance> found) {
            long ttl = Long.MAX_VALUE;
            if (configuration.getRecordType() == DnsServiceConfiguration.RecordType.SRV) {
                List<SrvRecord> records = new ArrayList<>();
                int lowestPriority = Integer.MAX_VALUE;
                for (DnsRecord record : answers) {
                    if (record.type() != DnsRecordType.SRV || !(record instanceof DnsRawRecord raw)) {
                        continue;
                    }
                    ByteBuf content = raw.content().duplicate();
                    if (content.readableBytes() < 7) {
                        continue;
                    }
                    int priority = content.readUnsignedShort();
                    int weight = content.readUnsignedShort();
                    int port = content.readUnsignedShort();
                    String target = DefaultDnsRecordDecoder.decodeName(content);
                    if (target.endsWith(".")) {
                        target = target.substring(0, target.length() - 1);
                    }
                    ttl = Math.min(ttl, record.timeToLive());
                    if (target.isEmpty()) {
                        // the target "." declares that the service is not available at this name
                        continue;
                    }
                    lowestPriority = Math.min(lowestPriority, priority);
                    records.add(new SrvRecord(priority, weight, port, target));
                }
                for (SrvRecord record : records) {
                    if (record.priority() == lowestPriority) {
                        Map<String, String> metadata = new HashMap<>();
                        metadata.put(WEIGHT, Integer.toString(record.weight()));
                        metadata.put("priority", Integer.toString(record.priority()));
                        found.add(instance(record.target(), record.port(), metadata));
                    }
                }
            } else {
                for (DnsRecord record : answers) {
                    if (!(record instanceof DnsRawRecord raw)) {
                        continue;
                    }
                    int length = raw.content().readableBytes();
                    boolean address = (record.type() == DnsRecordType.A && length == 4)
                        || (record.type() == DnsRecordType.AAAA && length == 16);
                    if (!address) {
                        continue;
                    }
                    InetAddress inetAddress;
                    try {
                        inetAddress = InetAddress.getByAddress(ByteBufUtil.getBytes(raw.content()));
                    } catch (UnknownHostException e) {
                        continue;
                    }
                    ttl = Math.min(ttl, record.timeToLive());
                    found.add(instance(inetAddress.getHostAddress(), configuration.getPort(), Collections.emptyMap()));
                }
            }
            return ttl == Long.MAX_VALUE ? 0 : ttl;
        }

        private ServiceInstance instance(String host, int port, Map<String, String> metadata) {
            URI uri;
            try {
                uri = new URI(configuration.getScheme(), null, host, port, null, null, null);
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("Invalid instance " + host + ":" + port + " of service " + serviceId, e);
            }
            ServiceInstance.Builder builder = ServiceInstance.builder(serviceId, uri)
                .instanceId(uri.getHost() + ":" + port);
            if (!metadata.isEmpty()) {
                builder.metadata(metadata);
            }
            return new DnsServiceInstance(builder.build());
        }

        /**
         * An SRV record.
         *
         * @param priority The priority
         * @param weight   The weight
         * @param port     The port
         * @param target   The target host
         */
        private record SrvRecord(int priority, int weight, int port, String target) {
        }
    }

    /**
     * An instance with value equality on its URI and metadata, so that a refresh finding the same
     * records keeps the state of the load balancer.
     *
     * @param delegate The instance
     */
    private record DnsServiceInstance(ServiceInstance delegate) implements ServiceInstance {
        @Override
        public String getId() {
            return delegate.getId();
        }

        @Override
        public URI getURI() {
            return delegate.getURI();
        }

        @Override
        public Optional<String> getInstanceId() {
            return delegate.getInstanceId();
        }

        @Override
        public ConvertibleValues<String> getMetadata() {
            return delegate.getMetadata();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof DnsServiceInstance other && other.getURI().equals(getURI())
                && other.getMetadata().asMap().equals(getMetadata().asMap());
        }

        @Override
        public int hashCode() {
            return getURI().hashCode();
        }

        @Override
        public String toString() {
            return getURI().toString();
        }
    }
}
