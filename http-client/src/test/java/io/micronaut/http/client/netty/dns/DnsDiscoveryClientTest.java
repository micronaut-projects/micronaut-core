package io.micronaut.http.client.netty.dns;

import io.micronaut.context.ApplicationContext;
import io.micronaut.discovery.ServiceInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DnsDiscoveryClientTest {

    private TestDnsServer dns;
    private ApplicationContext ctx;

    @BeforeEach
    void setUp() throws Exception {
        dns = new TestDnsServer();
    }

    @AfterEach
    void tearDown() {
        if (ctx != null) {
            ctx.close();
        }
        dns.close();
    }

    private DnsDiscoveryClient start(String recordType, Map<String, Object> extra) {
        Map<String, Object> config = new HashMap<>();
        String prefix = DnsServiceConfiguration.PREFIX + ".my-svc.";
        config.put(prefix + "hostname", "my-svc.test.local");
        config.put(prefix + "record-type", recordType);
        config.put(prefix + "port", 8080);
        config.put(prefix + "servers", List.of(dns.address()));
        config.put(prefix + "query-timeout", "300ms");
        config.put(prefix + "min-ttl", "100ms");
        extra.forEach((k, v) -> config.put(prefix + k, v));
        ctx = ApplicationContext.run(config);
        return ctx.getBean(DnsDiscoveryClient.class);
    }

    private static List<ServiceInstance> instances(DnsDiscoveryClient client) throws Exception {
        return client.getInstancesAsync("my-svc").toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    private static Set<URI> uris(List<ServiceInstance> instances) {
        return instances.stream().map(ServiceInstance::getURI).collect(Collectors.toSet());
    }

    @Test
    void offUnlessConfigured() {
        try (ApplicationContext context = ApplicationContext.run()) {
            assertFalse(context.containsBean(DnsDiscoveryClient.class));
        }
    }

    @Test
    void addressRecords() throws Exception {
        dns.set("my-svc.test.local", TestDnsServer.a("127.0.0.1", 30), TestDnsServer.a("127.0.0.2", 30));
        DnsDiscoveryClient client = start("A", Map.of());

        assertEquals(Set.of(URI.create("http://127.0.0.1:8080"), URI.create("http://127.0.0.2:8080")), uris(instances(client)));
        assertEquals(List.of("my-svc"), client.getServiceIdsAsync().toCompletableFuture().get());
        assertTrue(instances(client).stream().allMatch(i -> i.getId().equals("my-svc")));
        assertTrue(client.getInstancesAsync("other").toCompletableFuture().get().isEmpty());
    }

    @Test
    void srvRecordsUseTheLowestPriorityAndCarryTheWeight() throws Exception {
        dns.set("my-svc.test.local",
            TestDnsServer.srv(10, 5, 9001, "a.test.local", 30),
            TestDnsServer.srv(10, 1, 9002, "b.test.local", 30),
            TestDnsServer.srv(20, 100, 9003, "c.test.local", 30));
        DnsDiscoveryClient client = start("SRV", Map.of());

        List<ServiceInstance> instances = instances(client);
        assertEquals(Set.of(URI.create("http://a.test.local:9001"), URI.create("http://b.test.local:9002")), uris(instances));
        Map<Integer, Long> weights = instances.stream().collect(Collectors.toMap(ServiceInstance::getPort,
            i -> i.getMetadata().get(DnsDiscoveryClient.WEIGHT, Long.class).orElseThrow()));
        assertEquals(Map.of(9001, 5L, 9002, 1L), weights);
    }

    @Test
    void ttlRefreshChangesTheList() throws Exception {
        dns.set("my-svc.test.local", TestDnsServer.a("127.0.0.1", 0));
        DnsDiscoveryClient client = start("A", Map.of());
        assertEquals(Set.of(URI.create("http://127.0.0.1:8080")), uris(instances(client)));

        dns.set("my-svc.test.local", TestDnsServer.a("127.0.0.3", 0), TestDnsServer.a("127.0.0.4", 0));
        await().atMost(Duration.ofSeconds(10)).until(() ->
            uris(instances(client)).equals(Set.of(URI.create("http://127.0.0.3:8080"), URI.create("http://127.0.0.4:8080"))));
    }

    @Test
    void maxTtlBoundsTheRefresh() throws Exception {
        dns.set("my-svc.test.local", TestDnsServer.a("127.0.0.1", 3600));
        DnsDiscoveryClient client = start("A", Map.of("max-ttl", "200ms"));
        instances(client);
        dns.set("my-svc.test.local", TestDnsServer.a("127.0.0.5", 3600));
        await().atMost(Duration.ofSeconds(10)).until(() -> uris(instances(client)).equals(Set.of(URI.create("http://127.0.0.5:8080"))));
    }

    @Test
    void nxdomainKeepsTheStaleList() throws Exception {
        dns.set("my-svc.test.local", TestDnsServer.a("127.0.0.1", 0));
        DnsDiscoveryClient client = start("A", Map.of());
        Set<URI> expected = Set.of(URI.create("http://127.0.0.1:8080"));
        assertEquals(expected, uris(instances(client)));

        dns.records.clear();
        int before = dns.queries.get();
        await().atMost(Duration.ofSeconds(10)).until(() -> dns.queries.get() > before + 2);
        assertEquals(expected, uris(instances(client)));

        dns.set("my-svc.test.local", TestDnsServer.a("127.0.0.6", 0));
        await().atMost(Duration.ofSeconds(10)).until(() -> uris(instances(client)).equals(Set.of(URI.create("http://127.0.0.6:8080"))));
    }

    @Test
    void timeoutKeepsTheStaleList() throws Exception {
        dns.set("my-svc.test.local", TestDnsServer.a("127.0.0.1", 0));
        DnsDiscoveryClient client = start("A", Map.of());
        Set<URI> expected = Set.of(URI.create("http://127.0.0.1:8080"));
        assertEquals(expected, uris(instances(client)));

        dns.drop = true;
        int before = dns.queries.get();
        await().atMost(Duration.ofSeconds(10)).until(() -> dns.queries.get() > before + 2);
        assertEquals(expected, uris(instances(client)));
    }

    @Test
    void firstLookupFailingGivesAnEmptyList() throws Exception {
        DnsDiscoveryClient client = start("A", Map.of());
        assertTrue(instances(client).isEmpty());
    }
}
