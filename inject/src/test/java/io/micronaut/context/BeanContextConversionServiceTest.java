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
package io.micronaut.context;

import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.core.convert.MutableConversionService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeanContextConversionServiceTest {

    @Test
    void contextAndEnvironmentShareOneIsolatedConversionService() {
        try (ApplicationContext first = ApplicationContext.run(); ApplicationContext second = ApplicationContext.run()) {
            assertSame(first.getConversionService(), first.getEnvironment().getConversionService());
            assertSame(first.getConversionService(), first.getBean(MutableConversionService.class));
            assertNotSame(first.getConversionService(), second.getConversionService());
            first.getBean(MutableConversionService.class).addConverter(String.class, Token.class, Token::new);
            first.getEnvironment().addPropertySource(PropertySource.of("conversion", Map.of("value", "first")));

            assertEquals(new Token("first"), first.getProperty("value", Token.class).orElseThrow());
            assertFalse(second.getConversionService().convert("first", Token.class).isPresent());
        }
    }

    @Test
    void anExplicitConversionServiceStillWins() {
        MutableConversionService configured = MutableConversionService.create();
        configured.addConverter(String.class, Token.class, Token::new);
        ApplicationContextConfiguration configuration = new ApplicationContextConfiguration() {
            @Override
            public List<String> getEnvironments() {
                return List.of();
            }

            @Override
            public Optional<MutableConversionService> getConversionService() {
                return Optional.of(configured);
            }
        };
        try (ApplicationContext context = new DefaultApplicationContext(configuration).start()) {
            assertSame(configured, context.getConversionService());
            assertSame(configured, context.getEnvironment().getConversionService());
            assertEquals(new Token("configured"), context.getConversionService().convert("configured", Token.class).orElseThrow());
        }
    }

    @Test
    void aBorrowedEnvironmentKeepsItsServiceAndLifecycle() {
        ApplicationContextConfiguration configuration = new ApplicationContextConfiguration() {
            @Override
            public List<String> getEnvironments() {
                return List.of();
            }
        };
        try (Environment environment = Environment.create(configuration).start()) {
            try (ApplicationContext context = new DefaultApplicationContext(configuration, environment).start()) {
                assertSame(environment.getConversionService(), context.getConversionService());
            }
            assertTrue(environment.isRunning());
        }
    }

    @Test
    void theDefaultServiceStillConvertsAfterRestart() {
        try (ApplicationContext context = ApplicationContext.builder().properties(Map.of("value", "42")).start()) {
            assertEquals(42, context.getProperty("value", Integer.class).orElseThrow());
            context.stop();
            context.start();
            assertEquals(42, context.getProperty("value", Integer.class).orElseThrow());
        }
    }

    private record Token(String value) {
    }
}
