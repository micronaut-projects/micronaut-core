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
package io.micronaut.http.server.cors;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorsOriginConfigurationTest {

    @Test
    void allowedOriginsRegexIsCompiledOnce() {
        CorsOriginConfiguration configuration = new CorsOriginConfiguration();
        configuration.setAllowedOriginsRegex("^https://foo\\.com$");
        String regex = configuration.getAllowedOriginsRegex().orElseThrow();

        Pattern pattern = configuration.getAllowedOriginsPattern(regex);

        assertEquals("^https://foo\\.com$", pattern.pattern());
        assertSame(pattern, configuration.getAllowedOriginsPattern(regex));
        assertTrue(pattern.matcher("https://foo.com").matches());
        assertFalse(pattern.matcher("https://bar.com").matches());
    }

    @Test
    void settingAnotherAllowedOriginsRegexReplacesThePattern() {
        CorsOriginConfiguration configuration = new CorsOriginConfiguration();
        configuration.setAllowedOriginsRegex("^https://foo\\.com$");
        configuration.getAllowedOriginsPattern(configuration.getAllowedOriginsRegex().orElseThrow());

        configuration.setAllowedOriginsRegex("^https://bar\\.com$");
        Pattern pattern = configuration.getAllowedOriginsPattern(configuration.getAllowedOriginsRegex().orElseThrow());

        assertTrue(pattern.matcher("https://bar.com").matches());
        assertFalse(pattern.matcher("https://foo.com").matches());
    }

    @Test
    void overriddenAllowedOriginsRegexIsCompiled() {
        CorsOriginConfiguration configuration = new CorsOriginConfiguration() {
            @Override
            public Optional<String> getAllowedOriginsRegex() {
                return Optional.of("^https://overridden\\.com$");
            }
        };
        configuration.setAllowedOriginsRegex("^https://foo\\.com$");

        Pattern pattern = configuration.getAllowedOriginsPattern(configuration.getAllowedOriginsRegex().orElseThrow());

        assertTrue(pattern.matcher("https://overridden.com").matches());
        assertFalse(pattern.matcher("https://foo.com").matches());
    }

    @Test
    void invalidAllowedOriginsRegexFailsWhenMatched() {
        CorsOriginConfiguration configuration = new CorsOriginConfiguration();

        assertDoesNotThrow(() -> configuration.setAllowedOriginsRegex("^https://(foo\\.com$"));
        assertThrows(PatternSyntaxException.class,
            () -> configuration.getAllowedOriginsPattern(configuration.getAllowedOriginsRegex().orElseThrow()));
    }
}
