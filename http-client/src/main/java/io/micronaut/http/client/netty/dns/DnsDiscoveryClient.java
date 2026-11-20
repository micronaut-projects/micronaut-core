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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.discovery.AsyncDiscoveryClient;
import io.micronaut.discovery.DiscoveryClient;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.netty.channel.DefaultEventLoopGroupConfiguration;
import io.micronaut.http.netty.channel.EventLoopGroupConfiguration;
import io.micronaut.http.netty.channel.EventLoopGroupFactory;
import io.micronaut.http.netty.channel.EventLoopGroupRegistry;
import io.micronaut.http.netty.channel.NettyChannelType;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.scheduling.TaskExecutors;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelFactory;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DefaultDnsRecordDecoder;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.resolver.dns.DnsNameResolver;
import io.netty.resolver.dns.DnsNameResolverBuilder;
import io.netty.resolver.dns.DnsServerAddressStreamProvider;
import io.netty.resolver.dns.DnsServerAddressStreamProviders;
import io.netty.resolver.dns.NoopAuthoritativeDnsServerCache;
import io.netty.resolver.dns.NoopDnsCache;
import io.netty.resolver.dns.NoopDnsCnameCache;
import io.netty.resolver.dns.SequentialDnsServerAddressStreamProvider;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ScheduledFuture;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Named;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link DiscoveryClient} that finds the instances of the services configured under
 * {@value DnsServiceConfiguration#PREFIX} with DNS address (A/AAAA) or SRV records. The records
 * are looked up on a Netty event loop when a service is first requested, then again when their
 * TTL expires. A lookup that fails, or finds no record, keeps the instances of the last one.
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
    private static final int DEFAULT_DNS_PORT = 53;

    private final Map<String, ServiceWatcher> watchers;
    private final List<String> serviceIds;

    /**
     * @param configurations     The DNS discovery of the services
     * @param eventLoopGroupRegistry The event loop groups
     * @param eventLoopGroupFactory  The Netty transport
     * @param beanContext            The bean context
     * @param blockingExecutor       Resolves the host names of the configured name servers
     */
    DnsDiscoveryClient(List<DnsServiceConfiguration> configurations,
                       EventLoopGroupRegistry eventLoopGroupRegistry,
                       EventLoopGroupFactory eventLoopGroupFactory,
                       BeanContext beanContext,
                       @Named(TaskExecutors.BLOCKING) Executor blockingExecutor) {
        Map<String, ServiceWatcher> map = new LinkedHashMap<>();
        for (DnsServiceConfiguration configuration : configurations) {
            String id = NameUtils.hyphenate(configuration.getServiceId());
            String groupName = configuration.getEventLoopGroup();
            EventLoopGroup group = EventLoopGroupConfiguration.DEFAULT.equals(groupName)
                ? eventLoopGroupRegistry.getDefaultEventLoopGroup()
                : eventLoopGroupRegistry.getEventLoopGroup(groupName)
                    .orElseThrow(() -> new HttpClientException("Specified event loop group is not defined: " + groupName));
            EventLoopGroupConfiguration groupConfiguration = beanContext.findBean(EventLoopGroupConfiguration.class, Qualifiers.byName(groupName))
                .orElseGet(DefaultEventLoopGroupConfiguration::new);
            map.put(id, new ServiceWatcher(id, configuration, group.next(),
                () -> (DatagramChannel) eventLoopGroupFactory.channelInstance(NettyChannelType.DATAGRAM_SOCKET, groupConfiguration),
                () -> (SocketChannel) eventLoopGroupFactory.channelInstance(NettyChannelType.CLIENT_SOCKET, groupConfiguration),
                blockingExecutor));
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
    @Internal
    static final class ServiceWatcher {
        private final String serviceId;
        private final DnsServiceConfiguration configuration;
        private final EventLoop eventLoop;
        private final ChannelFactory<DatagramChannel> datagramChannelFactory;
        private final ChannelFactory<SocketChannel> socketChannelFactory;
        private final Executor blockingExecutor;
        private final CompletableFuture<List<ServiceInstance>> first = new CompletableFuture<>();
        private volatile List<ServiceInstance> instances = Collections.emptyList();
        private volatile boolean started;
        private volatile boolean closed;
        // event loop state
        @Nullable
        private DnsNameResolver resolver;
        @Nullable
        private ScheduledFuture<?> next;
        private boolean failing;
        @Nullable
        private CompletableFuture<DnsServerAddressStreamProvider> nameServers;

        ServiceWatcher(String serviceId, DnsServiceConfiguration configuration, EventLoop eventLoop,
                       ChannelFactory<DatagramChannel> datagramChannelFactory,
                       ChannelFactory<SocketChannel> socketChannelFactory,
                       Executor blockingExecutor) {
            this.serviceId = serviceId;
            this.configuration = configuration;
            this.eventLoop = eventLoop;
            this.datagramChannelFactory = datagramChannelFactory;
            this.socketChannelFactory = socketChannelFactory;
            this.blockingExecutor = blockingExecutor;
        }

        CompletionStage<List<ServiceInstance>> instances() {
            if (first.isDone()) {
                return CompletableFuture.completedFuture(instances);
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
            first.complete(instances);
        }

        private void closeOnLoop() {
            if (next != null) {
                next.cancel(false);
            }
            if (resolver != null) {
                resolver.close();
                resolver = null;
            }
        }

        /**
         * @return The resolver, or {@code null} while the name servers are being resolved, after
         * which the lookup runs again
         */
        private @Nullable DnsNameResolver resolver() {
            if (resolver == null) {
                if (nameServers == null) {
                    // a configured name server may be a host name: its lookup blocks, so it never runs on the event loop
                    List<String> servers = configuration.getServers();
                    nameServers = servers.isEmpty()
                        ? CompletableFuture.completedFuture(DnsServerAddressStreamProviders.platformDefault())
                        : CompletableFuture.supplyAsync(() -> nameServers(servers), blockingExecutor);
                }
                if (!nameServers.isDone()) {
                    nameServers.whenComplete((provider, error) -> eventLoop.execute(this::refresh));
                    return null;
                }
                resolver = new DnsNameResolverBuilder(eventLoop)
                    .datagramChannelFactory(datagramChannelFactory)
                    // a truncated UDP answer is queried again over TCP
                    .socketChannelFactory(socketChannelFactory)
                    .resolveCache(NoopDnsCache.INSTANCE)
                    .cnameCache(NoopDnsCnameCache.INSTANCE)
                    .authoritativeDnsServerCache(NoopAuthoritativeDnsServerCache.INSTANCE)
                    .queryTimeoutMillis(configuration.getQueryTimeout().toMillis())
                    .nameServerProvider(nameServers.join())
                    .build();
            }
            return resolver;
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
            DnsNameResolver r;
            try {
                r = resolver();
            } catch (RuntimeException e) {
                if (nameServers != null && nameServers.isCompletedExceptionally()) {
                    // look the name servers up again on the next attempt
                    nameServers = null;
                }
                onFailure(e instanceof CompletionException && e.getCause() != null ? e.getCause() : e);
                return;
            }
            if (r == null) {
                return;
            }
            List<DnsRecordType> types = switch (configuration.getRecordType()) {
                case A -> List.of(DnsRecordType.A);
                case AAAA -> List.of(DnsRecordType.AAAA);
                case ANY_ADDRESS -> List.of(DnsRecordType.A, DnsRecordType.AAAA);
                case SRV -> List.of(DnsRecordType.SRV);
            };
            List<DefaultDnsQuestion> questions = new ArrayList<>(types.size());
            try {
                for (DnsRecordType type : types) {
                    questions.add(new DefaultDnsQuestion(name, type));
                }
            } catch (RuntimeException e) {
                // an invalid host name, such as one with a label longer than 63 characters
                onFailure(e);
                return;
            }
            List<DnsRecord> answers = new ArrayList<>();
            AtomicInteger pending = new AtomicInteger(questions.size());
            Throwable[] failure = new Throwable[1];
            for (DefaultDnsQuestion question : questions) {
                Future<List<DnsRecord>> query = r.resolveAll(question);
                query.addListener(f -> {
                    // the listeners run on the event loop of the resolver
                    if (f.isSuccess()) {
                        @SuppressWarnings("unchecked")
                        List<DnsRecord> records = (List<DnsRecord>) f.getNow();
                        answers.addAll(records);
                    } else {
                        failure[0] = f.cause();
                    }
                    if (pending.decrementAndGet() == 0) {
                        try {
                            onAnswers(answers, failure[0]);
                        } finally {
                            for (DnsRecord record : answers) {
                                ReferenceCountUtil.release(record);
                            }
                        }
                    }
                });
            }
        }

        private void onAnswers(List<DnsRecord> answers, @Nullable Throwable failure) {
            if (closed) {
                return;
            }
            List<ServiceInstance> found = new ArrayList<>();
            Parsed parsed;
            try {
                parsed = parse(answers, found);
            } catch (RuntimeException e) {
                onFailure(e);
                return;
            }
            long ttl = parsed.ttl();
            if (found.isEmpty() && !parsed.withdrawn()) {
                onFailure(failure != null ? failure : new UnknownHostException("No " + configuration.getRecordType() + " record for " + configuration.getHostname()));
                return;
            }
            instances = Collections.unmodifiableList(found);
            if (parsed.withdrawn()) {
                LOG.info("DNS discovery of service [{}]: the SRV records of [{}] declare the service unavailable", serviceId, configuration.getHostname());
            }
            if (failing) {
                failing = false;
                LOG.info("DNS discovery of service [{}] recovered: {} instance(s)", serviceId, found.size());
            } else if (LOG.isDebugEnabled()) {
                LOG.debug("DNS discovery of service [{}] found {}", serviceId, found);
            }
            first.complete(instances);
            schedule(refreshDelay(Duration.ofSeconds(ttl)));
        }

        private void onFailure(Throwable cause) {
            if (!failing) {
                failing = true;
                LOG.warn("DNS discovery of service [{}] failed to look up [{}], keeping the {} known instance(s): {}",
                    serviceId, configuration.getHostname(), instances.size(), cause.toString());
            }
            first.complete(instances);
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

        private Parsed parse(List<DnsRecord> answers, List<ServiceInstance> found) {
            long ttl = Long.MAX_VALUE;
            long withdrawnTtl = -1;
            if (configuration.getRecordType() == DnsServiceConfiguration.RecordType.SRV) {
                int lowestPriority = Integer.MAX_VALUE;
                List<int[]> values = new ArrayList<>();
                List<String> targets = new ArrayList<>();
                List<Long> ttls = new ArrayList<>();
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
                    if (target.isEmpty()) {
                        // "." means the service is not available at this name
                        withdrawnTtl = withdrawnTtl < 0 ? record.timeToLive() : Math.min(withdrawnTtl, record.timeToLive());
                        continue;
                    }
                    lowestPriority = Math.min(lowestPriority, priority);
                    values.add(new int[] {priority, weight, port});
                    targets.add(target);
                    ttls.add(record.timeToLive());
                }
                for (int i = 0; i < values.size(); i++) {
                    int[] v = values.get(i);
                    if (v[0] != lowestPriority) {
                        continue;
                    }
                    ttl = Math.min(ttl, ttls.get(i));
                    Map<String, String> metadata = new HashMap<>();
                    metadata.put(WEIGHT, Integer.toString(v[1]));
                    metadata.put("priority", Integer.toString(v[0]));
                    found.add(instance(targets.get(i), v[2], metadata));
                }
            } else {
                for (DnsRecord record : answers) {
                    if (!(record instanceof DnsRawRecord raw)) {
                        continue;
                    }
                    int length = raw.content().readableBytes();
                    if ((record.type() == DnsRecordType.A && length != 4) || (record.type() == DnsRecordType.AAAA && length != 16)
                        || (record.type() != DnsRecordType.A && record.type() != DnsRecordType.AAAA)) {
                        continue;
                    }
                    InetAddress address;
                    try {
                        address = InetAddress.getByAddress(ByteBufUtil.getBytes(raw.content()));
                    } catch (UnknownHostException e) {
                        continue;
                    }
                    ttl = Math.min(ttl, record.timeToLive());
                    found.add(instance(address.getHostAddress(), configuration.getPort(), Collections.emptyMap()));
                }
            }
            if (found.isEmpty() && withdrawnTtl >= 0) {
                return new Parsed(withdrawnTtl, true);
            }
            return new Parsed(ttl == Long.MAX_VALUE ? 0 : ttl, false);
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

        private static DnsServerAddressStreamProvider nameServers(List<String> servers) {
            if (servers.isEmpty()) {
                return DnsServerAddressStreamProviders.platformDefault();
            }
            List<InetSocketAddress> addresses = new ArrayList<>(servers.size());
            for (String server : servers) {
                URI uri = URI.create("dns://" + server.trim());
                if (uri.getHost() == null) {
                    throw new IllegalArgumentException("Invalid DNS server: " + server);
                }
                addresses.add(new InetSocketAddress(uri.getHost(), uri.getPort() == -1 ? DEFAULT_DNS_PORT : uri.getPort()));
            }
            return new SequentialDnsServerAddressStreamProvider(addresses);
        }

        /**
         * The lowest TTL of the records used, in seconds, and whether the SRV records declare
         * that the service is not available (a single record with the target {@code .}).
         *
         * @param ttl       The TTL
         * @param withdrawn Whether the service is withdrawn
         */
        private record Parsed(long ttl, boolean withdrawn) {
        }
    }

    /**
     * An instance with value equality on its URI, so that a refresh finding the same records
     * keeps the state of the load balancer.
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
        public java.util.Optional<String> getInstanceId() {
            return delegate.getInstanceId();
        }

        @Override
        public io.micronaut.core.convert.value.ConvertibleValues<String> getMetadata() {
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
