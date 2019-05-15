package io.micronaut.discovery.vault;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.discovery.vault.config.client.v1.response.VaultResponseV1;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 *  Tests for Vault KV version 1.
 *
 *  @author thiagolocatelli
 */
public class VaultClientV1ConfigTest {

    private static EmbeddedServer vaultServerV1;
    private static HttpClient vaultServerHttpClientV1;

    private static EmbeddedServer applicationServer;
    private static HttpClient applicationServerHttpClient;

    @BeforeClass
    public static void setupServer() {

        System.setProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY, "true");

        Map<String, Object> vaultServerPropertiesMap = new HashMap<>();
        vaultServerPropertiesMap.put(MockingVaultServerV1Controller.ENABLED, true);
        vaultServerPropertiesMap.put("micronaut.server.port", -1);
        vaultServerPropertiesMap.put("micronaut.application.name", "vault-config-server-v1");

        vaultServerV1 = ApplicationContext.run(EmbeddedServer.class, vaultServerPropertiesMap);

        vaultServerHttpClientV1 = vaultServerV1.getApplicationContext()
                .createBean(HttpClient.class, LoadBalancer.fixed(vaultServerV1.getURL()));

        Map<String, Object> applicationServerPropertiesMap = new HashMap<>();
        applicationServerPropertiesMap.put(ApplicationTestController.ENABLED, true);
        applicationServerPropertiesMap.put("micronaut.application.name", "vault-config-clientapp-v1");
        applicationServerPropertiesMap.put("micronaut.config-client.enabled", true);
        applicationServerPropertiesMap.put("vault.client.config.enabled", true);
        applicationServerPropertiesMap.put("vault.client.kv-version", "V1");
        applicationServerPropertiesMap.put("vault.client.token", "testtoken");
        applicationServerPropertiesMap.put("vault.client.secret-engine-name", "backendv1");
        applicationServerPropertiesMap.put("vault.client.uri", vaultServerV1.getURI());
        applicationServer = ApplicationContext.run(EmbeddedServer.class, applicationServerPropertiesMap);

        applicationServerHttpClient = applicationServer
                .getApplicationContext()
                .createBean(HttpClient.class, LoadBalancer.fixed(applicationServer.getURL()));

    }

    @AfterClass
    public static void stopServer() {
        System.setProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY, "");
        if (vaultServerV1 != null) {
            vaultServerV1.stop();
        }
        if (vaultServerHttpClientV1 != null) {
            vaultServerHttpClientV1.stop();
        }

        if (applicationServer != null) {
            applicationServer.stop();
        }
        if (applicationServerHttpClient != null) {
            applicationServerHttpClient.stop();
        }
    }

    @Test
    public void testReadValuesFromVaultServerWithoutProfile() throws Exception {
        HttpRequest request = HttpRequest.GET("/v1/backendv1/vault-config-sample");
        VaultResponseV1 response = vaultServerHttpClientV1.toBlocking().retrieve(request, VaultResponseV1.class);

        assertNotNull(response);
        assertTrue(response.getData().containsKey("vault-backend-key-one"));
        assertEquals(response.getData().get("vault-backend-key-one"), "vault-config-sample");

        assertTrue(response.getData().containsKey("vault-backend-name"));
        assertEquals(response.getData().get("vault-backend-name"), "backendv1-vault-config-sample");

        assertTrue(response.getData().containsKey("vault-backend-kv-version"));
        assertEquals(response.getData().get("vault-backend-kv-version"), "v1-vault-config-sample");
    }

    @Test
    public void testReadValuesFromVaultServerWithProfile() throws Exception {
        HttpRequest request = HttpRequest.GET("/v1/backendv1/vault-config-sample/prod");
        VaultResponseV1 response = vaultServerHttpClientV1.toBlocking().retrieve(request, VaultResponseV1.class);

        assertNotNull(response);
        assertTrue(response.getData().containsKey("vault-backend-key-one"));
        assertEquals(response.getData().get("vault-backend-key-one"), "vault-config-sample/prod");

        assertTrue(response.getData().containsKey("vault-backend-name"));
        assertEquals(response.getData().get("vault-backend-name"), "backendv1-vault-config-sample/prod");

        assertTrue(response.getData().containsKey("vault-backend-kv-version"));
        assertEquals(response.getData().get("vault-backend-kv-version"), "v1-vault-config-sample/prod");
    }

    @Test
    public void testReadValuesFromApplicationController() throws Exception {

        Map<String, String> responseType = new HashMap<>();

        HttpRequest request = HttpRequest.GET("/test-vault");
        Map<String, String> response = applicationServerHttpClient.toBlocking().retrieve(request, responseType.getClass());

        assertNotNull(response);

        assertTrue(response.containsKey("vault-backend-key-one"));
        assertTrue(response.containsKey("vault-backend-kv-version"));
        assertTrue(response.containsKey("vault-backend-name"));
    }

}
