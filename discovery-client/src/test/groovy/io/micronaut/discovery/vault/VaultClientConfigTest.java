package io.micronaut.discovery.vault;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.discovery.vault.config.client.v1.response.VaultResponseV1;
import io.micronaut.discovery.vault.config.client.v2.response.VaultResponseV2;
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

public class VaultClientConfigTest {

    private static EmbeddedServer vaultServer;
    private static HttpClient vaultServerHttpClient;

    private static EmbeddedServer applicationServer;
    private static HttpClient applicationServerHttpClient;

    @BeforeClass
    public static void setupServer() {

        System.setProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY, "true");

        Map<String, Object> vaultServerPropertiesMap = new HashMap<>();
        vaultServerPropertiesMap.put(MockingVaultServerController.ENABLED, true);
        vaultServerPropertiesMap.put("micronaut.server.port", -1);
        vaultServerPropertiesMap.put("micronaut.environments", "dev,test");
        vaultServerPropertiesMap.put("vault.client.config.enabled", false);
        vaultServer = ApplicationContext.run(EmbeddedServer.class, vaultServerPropertiesMap);

        vaultServerHttpClient = vaultServer
                .getApplicationContext()
                .createBean(HttpClient.class, LoadBalancer.fixed(vaultServer.getURL()));

        Map<String, Object> applicationServerPropertiesMap = new HashMap<>();
        applicationServerPropertiesMap.put(ApplicationTestController.ENABLED, true);
        applicationServerPropertiesMap.put("micronaut.environments", "dev");
        applicationServerPropertiesMap.put("micronaut.application.name", "vault-config-sample");
        applicationServerPropertiesMap.put("micronaut.config-client.enabled", true);
        applicationServerPropertiesMap.put("vault.client.config.enabled", true);
        applicationServerPropertiesMap.put("vault.client.kv-version", "V1");
        applicationServerPropertiesMap.put("vault.client.token", "testtoken");
        applicationServerPropertiesMap.put("vault.client.backend", "backendv1");
        applicationServerPropertiesMap.put("vault.client.uri", vaultServer.getURI());
        applicationServer = ApplicationContext.run(EmbeddedServer.class, applicationServerPropertiesMap);

        applicationServerHttpClient = applicationServer
                .getApplicationContext()
                .createBean(HttpClient.class, LoadBalancer.fixed(applicationServer.getURL()));

    }

    @AfterClass
    public static void stopServer() {
        System.setProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY, "");
        if (vaultServer != null) {
            vaultServer.stop();
        }
        if (vaultServerHttpClient != null) {
            vaultServerHttpClient.stop();
        }

        if (applicationServer != null) {
            applicationServer.stop();
        }
        if (applicationServerHttpClient != null) {
            applicationServerHttpClient.stop();
        }
    }

    @Test
    public void testReadValuesFromVaultServerV1() throws Exception {
        HttpRequest request = HttpRequest.GET("/v1/backendv1/vault-config-sample/prod");
        VaultResponseV1 response = vaultServerHttpClient.toBlocking().retrieve(request, VaultResponseV1.class);

        assertNotNull(response);
        assertTrue(response.getData().containsKey("vault-backend-key-one"));
        assertEquals(response.getData().get("vault-backend-key-one"), "vault-config-sample-prod");

        assertTrue(response.getData().containsKey("vault-backend-name"));
        assertEquals(response.getData().get("vault-backend-name"), "backendv1-prod");
    }

    @Test
    public void testReadValuesFromVaultServerV2() throws Exception {
        HttpRequest request = HttpRequest.GET("/v1/backendv2/data/vault-config-sample/prod");
        VaultResponseV2 response = vaultServerHttpClient.toBlocking().retrieve(request, VaultResponseV2.class);

        assertNotNull(response);
        assertNotNull(response.getData());

        assertTrue(response.getData().getData().containsKey("vault-backend-key-one"));
        assertEquals(response.getData().getData().get("vault-backend-key-one"), "vault-config-sample-prod");

        assertTrue(response.getData().getData().containsKey("vault-backend-name"));
        assertEquals(response.getData().getData().get("vault-backend-name"), "backendv2-prod");
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
