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
package io.micronaut.http.client.jdk;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.ServiceHttpClientConfiguration;
import io.micronaut.http.client.netty.NettyHttpClientConfiguration;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdkConfigurationWithNettyTest {
    @Test
    void jdkSettingsBindWhenTheNettyConfigurationIsPrimary() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "micronaut.http.client.jdk.use-micronaut-redirects", true,
            "micronaut.http.services.backend.url", "http://localhost",
            "micronaut.http.services.backend.jdk.decode-error-type", true
        ))) {
            assertInstanceOf(NettyHttpClientConfiguration.class, context.getBean(HttpClientConfiguration.class));
            assertTrue(context.getBean(DefaultJdkHttpClientConfiguration.class).getJdk().isUseMicronautRedirects());
            ServiceHttpClientConfiguration service = context.getBean(ServiceHttpClientConfiguration.class, Qualifiers.byName("backend"));
            JdkHttpClientConfiguration.JdkConfiguration jdk = assertInstanceOf(JdkHttpClientConfiguration.class, service).getJdk();
            assertTrue(jdk.isUseMicronautRedirects());
            assertTrue(jdk.isDecodeErrorType());
            assertFalse(jdk.isApplyRequestTimeout());
        }
    }
}
