/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.docs.config.env;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.PropertySource;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class EachPropertyTest {

    @Test
    public void testEachProperty() throws URISyntaxException {
        // tag::config[]
        ApplicationContext applicationContext = ApplicationContext.run(PropertySource.of(
                "test",
                CollectionUtils.mapOf(
                        "test.datasource.one.url", "jdbc:mysql://localhost/one",
                        "test.datasource.two.url", "jdbc:mysql://localhost/two")
        ));
        // end::config[]

        // tag::beans[]
        Collection<DataSourceConfiguration> beansOfType = applicationContext.getBeansOfType(DataSourceConfiguration.class);
        assertEquals(2, beansOfType.size()); // <1>

        DataSourceConfiguration firstConfig = applicationContext.getBean(
                DataSourceConfiguration.class,
                Qualifiers.byName("one") // <2>
        );

        assertEquals(
                new URI("jdbc:mysql://localhost/one"),
                firstConfig.getUrl()
        );
        // end::beans[]

        applicationContext.close();
    }

    @Test
    public void testEachPropertyList() {
        List<Map> limits = new ArrayList<>();
        limits.add(CollectionUtils.mapOf("period", "10s", "limit", "1000"));
        limits.add(CollectionUtils.mapOf("period", "1m", "limit", "5000"));
        ApplicationContext applicationContext = ApplicationContext.run(Collections.singletonMap("ratelimits", limits));

        List<RateLimitsConfiguration> beansOfType = applicationContext.streamOfType(RateLimitsConfiguration.class).collect(Collectors.toList());

        assertEquals(
                2,
                beansOfType.size()
        );
        assertEquals(1000, beansOfType.get(0).getLimit().intValue());
        assertEquals(5000, beansOfType.get(1).getLimit().intValue());

        applicationContext.close();
    }
}
