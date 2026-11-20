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
package io.micronaut.http.server.limit;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

class RequestLimitRouteValidatorTest {
    private static final String SPEC = "RequestLimitRouteValidatorTest";

    @Test
    void aRouteNamingAMissingLimiterFailsTheStartupWithoutAnyLimiter() {
        Exception e = Assertions.assertThrows(Exception.class, () -> ApplicationContext.run(Map.of("spec.name", SPEC)).close());
        Assertions.assertTrue(messages(e).contains("reports"), messages(e));
    }

    @Test
    void aRouteNamingAMissingLimiterFailsTheStartupWithOtherLimiters() {
        Exception e = Assertions.assertThrows(Exception.class, () -> ApplicationContext.run(Map.of(
            "spec.name", SPEC,
            "micronaut.server.limits.other.max-concurrent", 1)).close());
        Assertions.assertTrue(messages(e).contains("reports"), messages(e));
    }

    @Test
    void aConfiguredLimiterStarts() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "spec.name", SPEC,
            "micronaut.server.limits.reports.max-concurrent", 1))) {
            Assertions.assertTrue(ctx.isRunning());
        }
    }

    private static String messages(Throwable e) {
        StringBuilder all = new StringBuilder();
        for (Throwable t = e; t != null; t = t.getCause()) {
            all.append(t.getMessage()).append(" | ");
        }
        return all.toString();
    }

    @Requires(property = "spec.name", value = SPEC)
    @Controller("/validated")
    static class ReportController {
        @RequestLimit("reports")
        @Get
        String report() {
            return "report";
        }
    }
}
