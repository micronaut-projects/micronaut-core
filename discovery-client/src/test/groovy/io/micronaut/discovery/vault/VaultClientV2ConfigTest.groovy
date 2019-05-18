package io.micronaut.discovery.vault;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.discovery.vault.config.v2.VaultResponseV2;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import spock.lang.AutoCleanup;
import spock.lang.Shared;
import spock.lang.Specification;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 *  Tests for Vault KV version 2.
 *
 *  @author thiagolocatelli
 */
class VaultClientV2ConfigTest extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [(MockingVaultServerV2Controller.ENABLED): true])


    void "test configuration order"() {
        given:
        System.setProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY, "true")
        ApplicationContext context = ApplicationContext.run([
                (MockingVaultServerV1Controller.ENABLED): true,
                "micronaut.application.name": "myapp",
                "micronaut.config-client.enabled": true,
                "vault.client.config.enabled": true,
                "vault.client.kv-version": "V2",
                "vault.client.token": "testtoken",
                "vault.client.secret-engine-name": "backendv2",
                "vault.client.uri": embeddedServer.getURL().toString()
        ], "first", "second")

        expect:
        1 == context.getRequiredProperty("v2-secret-1", Integer.class)
        1 == context.getRequiredProperty("v2-secret-2", Integer.class)
        1 == context.getRequiredProperty("v2-secret-3", Integer.class)
        1 == context.getRequiredProperty("v2-secret-4", Integer.class)
        1 == context.getRequiredProperty("v2-secret-5", Integer.class)
        1 == context.getRequiredProperty("v2-secret-6", Integer.class)

        cleanup:
        System.setProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY, "")
        context.stop()
    }

}
