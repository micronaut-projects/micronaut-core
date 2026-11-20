package io.micronaut.http.client.netty.dns;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DnsDiscoveryClientLoadBalancingTest {

    @Test
    void clientBalancesAcrossServersFoundWithDns() throws Exception {
        try (TestDnsServer dns = new TestDnsServer();
             EmbeddedServer one = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", "DnsLbServer", "server.name", "one"));
             EmbeddedServer two = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", "DnsLbServer", "server.name", "two"))) {
            dns.set("pods.test.local",
                TestDnsServer.srv(0, 1, one.getPort(), "localhost", 30),
                TestDnsServer.srv(0, 1, two.getPort(), "localhost", 30));
            try (ApplicationContext ctx = ApplicationContext.run(Map.of(
                "spec.name", "DnsLbClient",
                DnsServiceConfiguration.PREFIX + ".pods.hostname", "pods.test.local",
                DnsServiceConfiguration.PREFIX + ".pods.record-type", "SRV",
                DnsServiceConfiguration.PREFIX + ".pods.servers", List.of(dns.address())))) {
                NameClient client = ctx.getBean(NameClient.class);
                Set<String> seen = new HashSet<>();
                for (int i = 0; i < 10; i++) {
                    seen.add(client.name());
                }
                assertEquals(Set.of("one", "two"), seen);
            }
        }
    }

    @Requires(property = "spec.name", value = "DnsLbClient")
    @Client(id = "pods")
    interface NameClient {
        @Get("/dns-lb/name")
        String name();
    }

    @Requires(property = "spec.name", value = "DnsLbServer")
    @Controller("/dns-lb")
    static class NameController {
        @Value("${server.name}")
        String name;

        @Get("/name")
        String name() {
            return name;
        }
    }
}
