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
package io.micronaut.http.server;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.runtime.EmbeddedApplication;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the parts of the training warm-up that need no server: the host the requests go to, the
 * validation of the settings and the switch that creates the warm-up beans. The warm-up itself is
 * checked against the Netty server.
 */
class TrainingWarmupTest {
    // The property names, which the build plugins set
    private static final String TRAINING_ENABLED = "micronaut.application.training.enabled";
    private static final String REPEAT = "micronaut.application.training.warmup.repeat";

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "0.0.0.0", "::", "[::]"})
    void requestsGoToLoopbackWhenTheHostIsNotSetOrAWildcardAddress(String configuredHost) {
        // The server then binds the wildcard address, and EmbeddedServer.getHost() falls back to $HOSTNAME
        String loopback = InetAddress.getLoopbackAddress().getHostAddress();

        assertEquals(loopback.indexOf(':') >= 0 ? '[' + loopback + ']' : loopback, TrainingWarmup.requestHost(configuredHost));
    }

    @ParameterizedTest
    @CsvSource({
        "localhost, localhost",
        "app.example.test, app.example.test",
        "192.168.1.10, 192.168.1.10",
        "::1, [::1]",
        "[::1], [::1]",
        "fe80::1, [fe80::1]"
    })
    void requestsGoToTheConfiguredHost(String configuredHost, String expected) {
        assertEquals(expected, TrainingWarmup.requestHost(configuredHost));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void repeatBelowOneIsRejected(int repeat) {
        TrainingWarmupConfiguration configuration = new TrainingWarmupConfiguration();

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> configuration.setRepeat(repeat));

        assertEquals(REPEAT + " must be at least 1 but was " + repeat, e.getMessage());
        // The default
        assertEquals(1, configuration.getRepeat());
    }

    @Test
    void repeatBelowOneFailsTheBinding() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(TRAINING_ENABLED, "true", REPEAT, 0))) {
            BeanInstantiationException e = assertThrows(BeanInstantiationException.class, () -> context.getBean(TrainingWarmupConfiguration.class));

            assertTrue(e.getMessage().contains(REPEAT + " must be at least 1 but was 0"), e::getMessage);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "TRUE", "True"})
    void trueInAnyCaseCreatesTheWarmupSettings(String value) {
        try (ApplicationContext context = ApplicationContext.run(Map.of(TRAINING_ENABLED, value))) {
            assertTrue(context.containsBean(TrainingWarmupConfiguration.class), value);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"yes", "on", "false"})
    void otherValuesDoNotCreateTheWarmupSettings(String value) {
        // As strings: Micronaut.start() does not stop the application for these values either
        try (ApplicationContext context = ApplicationContext.run(Map.of(TRAINING_ENABLED, value))) {
            assertFalse(context.containsBean(TrainingWarmupConfiguration.class), value);
        }
    }

    @Test
    void repeatOfOneOrMoreIsAccepted() {
        TrainingWarmupConfiguration configuration = new TrainingWarmupConfiguration();

        configuration.setRepeat(3);

        assertEquals(3, configuration.getRepeat());
    }

    @Test
    void sendsNoRequestWithoutAnEmbeddedApplication() {
        TrainingWarmupConfiguration configuration = new TrainingWarmupConfiguration();
        configuration.setPaths(List.of("/training-warmup/ok"));
        // Any call on the server, such as getScheme() or getPort(), means that the warm-up went ahead
        EmbeddedServer server = (EmbeddedServer) Proxy.newProxyInstance(EmbeddedServer.class.getClassLoader(),
            new Class<?>[]{EmbeddedServer.class}, (proxy, method, args) -> {
                throw new AssertionError("The warm-up called EmbeddedServer." + method.getName());
            });

        // This module has no EmbeddedServer implementation, so the context has no EmbeddedApplication
        try (ApplicationContext context = ApplicationContext.run()) {
            assertFalse(context.containsBean(EmbeddedApplication.class));
            TrainingWarmup warmup = new TrainingWarmup(context, new HttpServerConfiguration(), configuration);

            assertDoesNotThrow(() -> warmup.onApplicationEvent(new ServerStartupEvent(server)));
        }
    }
}
