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
package io.micronaut.http.client.internal;

import io.micronaut.http.HttpMethod;
import io.micronaut.http.client.ExchangeRetryConfiguration;
import io.micronaut.http.client.ServiceHttpClientConfiguration;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExchangeRetryConfigurationTest {

    @Test
    void defaults() {
        ExchangeRetryConfiguration configuration = new ExchangeRetryConfiguration();
        assertFalse(configuration.isEnabled());
        assertNull(configuration.getPolicy());
        assertEquals(ExchangeRetryConfiguration.DEFAULT_MAX_ATTEMPTS, configuration.getMaxAttempts());
        assertEquals(List.of(502, 503, 504), configuration.getStatuses());
        assertEquals(List.of(), configuration.getStatusClasses());
        assertNull(configuration.getMethods());
        assertTrue(configuration.isRetryOnUnprocessed());
        assertTrue(configuration.isRetryOnTimeout());
        assertTrue(configuration.isRetryOnFailure());
        assertNull(configuration.getAttemptTimeout());
        assertTrue(configuration.isRespectRetryAfter());
        assertEquals(Duration.ofSeconds(30), configuration.getMaxRetryAfter());
        assertTrue(configuration.getBudget().isEnabled());
        assertEquals(10, configuration.getBudget().getMaxTokens());
        assertEquals(0.1, configuration.getBudget().getTokenRatio());
        assertTrue(configuration.isRetriedStatus(503));
        assertFalse(configuration.isRetriedStatus(500));
        assertTrue(configuration.isRetriedMethod(HttpMethod.GET));
        assertTrue(configuration.isRetriedMethod(HttpMethod.PUT));
        assertFalse(configuration.isRetriedMethod(HttpMethod.POST));
    }

    @Test
    void setters() {
        ExchangeRetryConfiguration configuration = new ExchangeRetryConfiguration();
        configuration.setEnabled(true);
        configuration.setPolicy("p");
        configuration.setMaxAttempts(2);
        configuration.setStatuses(List.of(429));
        configuration.setStatusClasses(List.of("5xx"));
        configuration.setMethods(List.of(HttpMethod.POST));
        configuration.setRetryOnUnprocessed(false);
        configuration.setRetryOnTimeout(false);
        configuration.setRetryOnFailure(false);
        configuration.setAttemptTimeout(Duration.ofSeconds(2));
        configuration.setRespectRetryAfter(false);
        configuration.setMaxRetryAfter(Duration.ofSeconds(3));
        configuration.getBudget().setEnabled(false);
        configuration.getBudget().setMaxTokens(5);
        configuration.getBudget().setTokenRatio(0.5);
        assertTrue(configuration.isEnabled());
        assertEquals("p", configuration.getPolicy());
        assertEquals(2, configuration.getMaxAttempts());
        assertEquals(List.of(429), configuration.getStatuses());
        assertEquals(List.of("5xx"), configuration.getStatusClasses());
        assertEquals(List.of(HttpMethod.POST), configuration.getMethods());
        assertFalse(configuration.isRetryOnUnprocessed());
        assertFalse(configuration.isRetryOnTimeout());
        assertFalse(configuration.isRetryOnFailure());
        assertEquals(Duration.ofSeconds(2), configuration.getAttemptTimeout());
        assertFalse(configuration.isRespectRetryAfter());
        assertEquals(Duration.ofSeconds(3), configuration.getMaxRetryAfter());
        assertFalse(configuration.getBudget().isEnabled());
        assertEquals(5, configuration.getBudget().getMaxTokens());
        assertEquals(0.5, configuration.getBudget().getTokenRatio());
        assertTrue(configuration.isRetriedStatus(429));
        assertTrue(configuration.isRetriedStatus(500));
        assertTrue(configuration.isRetriedStatus(599));
        assertFalse(configuration.isRetriedStatus(404));
        assertTrue(configuration.isRetriedMethod(HttpMethod.POST));
        assertFalse(configuration.isRetriedMethod(HttpMethod.GET));
        configuration.setMethods(null);
        assertNull(configuration.getMethods());
    }

    @Test
    void invalidValues() {
        ExchangeRetryConfiguration configuration = new ExchangeRetryConfiguration();
        assertThrows(IllegalArgumentException.class, () -> configuration.setMaxAttempts(0));
        assertThrows(IllegalArgumentException.class, () -> configuration.setStatusClasses(List.of("6xx")));
        assertThrows(IllegalArgumentException.class, () -> configuration.setStatusClasses(List.of("50x")));
        assertThrows(IllegalArgumentException.class, () -> configuration.setStatusClasses(List.of("5")));
        assertThrows(IllegalArgumentException.class, () -> configuration.getBudget().setMaxTokens(0));
        assertThrows(IllegalArgumentException.class, () -> configuration.getBudget().setTokenRatio(0));
        assertThrows(IllegalArgumentException.class, () -> configuration.getBudget().setTokenRatio(Double.NaN));
    }

    @Test
    void aConfigurationBuiltWithoutContextHasRetryDisabled() {
        ServiceHttpClientConfiguration.ServiceExchangeRetryConfiguration configuration = new ServiceHttpClientConfiguration.ServiceExchangeRetryConfiguration(null, null);
        assertFalse(configuration.isEnabled());
        assertNotNull(configuration.getBudget());
    }
}
