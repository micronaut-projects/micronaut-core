/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.configuration.picocli;

import io.micronaut.configuration.picocli.MicronautFactory;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.DefaultApplicationContext;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.env.PropertySource;
import io.micronaut.core.util.CollectionUtils;
import org.junit.Ignore;
import spock.lang.Specification;

import javax.inject.Singleton;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import static org.junit.Assert.*;

public class MicronautFactoryTest {
    @Test
    public void createDelegatesToApplicationContext() throws Exception {
        System.setProperty("a.name", "testValue");
        ApplicationContext applicationContext = ApplicationContext.run(PropertySource.of(
                "test",
                CollectionUtils.mapOf("a.name", "testValue")
        ));
        MicronautFactory factory = new MicronautFactory(applicationContext);

        A a = factory.create(A.class);
        A another = applicationContext.getBean(A.class);

        assertSame("can get singleton A from factory and context", another, a);
        assertEquals("injected value is available", "testValue", a.injectedValue);

        applicationContext.close();
    }

    @Test
    public void createInstantiatesIfNotFound() throws Exception {
        ApplicationContext applicationContext = ApplicationContext.run(PropertySource.of(
                "test",
                CollectionUtils.mapOf("a.name", "testValue")
        ));
        MicronautFactory factory = new MicronautFactory(applicationContext);

        long before1 = count.incrementAndGet();
        B b1 = factory.create(B.class);

        long before2 = count.incrementAndGet();
        B b2 = factory.create(B.class);

        assertTrue(b1.seq > before1);
        assertTrue(b2.seq > before2);
        assertTrue(b2.seq > b1.seq);

        applicationContext.close();
    }

    static AtomicInteger count = new AtomicInteger();

    @Singleton
    static class A {
        @Value("${a.name:hello}")
        String injectedValue;
    }

    static class B {
        final int seq = count.incrementAndGet();
    }

}
