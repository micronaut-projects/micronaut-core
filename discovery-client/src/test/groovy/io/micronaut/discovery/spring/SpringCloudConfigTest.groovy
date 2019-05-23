/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.discovery.spring;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.discovery.vault.MockingVaultServerV1Controller;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import spock.lang.AutoCleanup;
import spock.lang.Shared;
import spock.lang.Specification;

import java.util.HashMap;
import java.util.Map;

import static junit.framework.TestCase.assertNotNull;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

class SpringCloudConfigTest extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [(MockSpringCloudConfigServer.ENABLED): true])

    void "test configuration order"() {
        given:
        System.setProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY, "true")
        ApplicationContext context = ApplicationContext.run([
                (MockingVaultServerV1Controller.ENABLED): true,
                "micronaut.application.name": "myapp",
                "micronaut.config-client.enabled": true,
                "spring.cloud.config.enabled": true,
                "spring.cloud.config.uri": embeddedServer.getURL().toString()
        ], "first", "second")

        expect:
        1 == context.getRequiredProperty("config-secret-1", Integer.class)
        1 == context.getRequiredProperty("config-secret-2", Integer.class)
        1 == context.getRequiredProperty("config-secret-3", Integer.class)
        1 == context.getRequiredProperty("config-secret-4", Integer.class)
        1 == context.getRequiredProperty("config-secret-5", Integer.class)
        1 == context.getRequiredProperty("config-secret-6", Integer.class)

        cleanup:
        System.setProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY, "")
        context.stop()
    }

}
