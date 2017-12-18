/*
 * Copyright 2017 original authors
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
package org.particleframework.docs.env;

import org.junit.Assert;
import org.junit.Test;
import org.particleframework.context.ApplicationContext;
import org.particleframework.context.env.Environment;
import org.particleframework.context.env.PropertySource;
import org.particleframework.core.util.CollectionUtils;

import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
public class EnvironmentTest {

    @Test
    public void testRunEnvironment() {
        // tag::env[]
        ApplicationContext applicationContext = ApplicationContext.run("test", "android");
        Environment environment = applicationContext.getEnvironment();

        assertEquals(
                environment.getActiveNames(),
                CollectionUtils.setOf("test", "android")
        );
        // end::env[]

    }

    @Test
    public void testRunEnvironmentWithProperties() {
        // tag::envProps[]
        ApplicationContext applicationContext = ApplicationContext.run(
                PropertySource.of(
                        "particle.server.host", "foo",
                        "particle.server.port", 8080
                ),
                "test", "android");
        Environment environment = applicationContext.getEnvironment();

        assertEquals(
                environment.getProperty("particle.server.host", String.class).orElse("localhost"),
                "foo"
        );
        // end::envProps[]

    }
}
