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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Checks the parts of the training warm-up that need no server: the host the requests go to and
 * the validation of the settings. The warm-up itself is checked against the Netty server.
 */
class TrainingWarmupTest {

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

        assertEquals("micronaut.application.training.warmup.repeat must be at least 1 but was " + repeat, e.getMessage());
        assertEquals(TrainingWarmupConfiguration.DEFAULT_REPEAT, configuration.getRepeat());
    }

    @Test
    void repeatOfOneOrMoreIsAccepted() {
        TrainingWarmupConfiguration configuration = new TrainingWarmupConfiguration();

        configuration.setRepeat(3);

        assertEquals(3, configuration.getRepeat());
    }
}
