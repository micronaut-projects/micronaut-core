package io.micronaut.discovery.vault

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

/**
 *  Tests for Vault KV version 1.
 *
 *  @author thiagolocatelli
 */
@RestoreSystemProperties
class VaultClientV1ConfigTest extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [(MockingVaultServerV1Controller.ENABLED): true])

    
    void "test configuration order"() {
        given:
        System.setProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY, "true")
        ApplicationContext context = ApplicationContext.run([
                (MockingVaultServerV1Controller.ENABLED): true,
                "micronaut.application.name": "myapp",
                "micronaut.config-client.enabled": true,
                "vault.client.config.enabled": true,
                "vault.client.kv-version": "V1",
                "vault.client.token": "testtoken",
                "vault.client.secret-engine-name": "backendv1",
                "vault.client.uri": embeddedServer.getURL().toString()
        ], "first", "second")

        expect:
        1 == context.getRequiredProperty("v1-secret-1", Integer.class)
        1 == context.getRequiredProperty("v1-secret-2", Integer.class)
        1 == context.getRequiredProperty("v1-secret-3", Integer.class)
        1 == context.getRequiredProperty("v1-secret-4", Integer.class)
        1 == context.getRequiredProperty("v1-secret-5", Integer.class)
        1 == context.getRequiredProperty("v1-secret-6", Integer.class)

        cleanup:
        context.stop()
    }

}
