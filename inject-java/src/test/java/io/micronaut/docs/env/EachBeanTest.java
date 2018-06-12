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
package io.micronaut.docs.env;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.PropertySource;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.junit.Test;
import io.micronaut.core.util.CollectionUtils;

import static org.junit.Assert.assertNotNull;
import static io.micronaut.docs.env.DataSourceFactory.*;
import java.net.URISyntaxException;
import java.util.Collection;

import static org.junit.Assert.assertEquals;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
public class EachBeanTest {

    @Test
    public void testEachBean() throws URISyntaxException {
        // tag::config[]
        ApplicationContext applicationContext = ApplicationContext.run(PropertySource.of(
                "test",
                CollectionUtils.mapOf(
                "test.datasource.one.url", "jdbc:mysql://localhost/one",
                "test.datasource.two.url", "jdbc:mysql://localhost/two")
        ));
        // end::config[]

        // tag::beans[]
        Collection<DataSource> beansOfType = applicationContext.getBeansOfType(DataSource.class);
        assertEquals(beansOfType.size(), 2); // <1>

        DataSource firstConfig = applicationContext.getBean(
                DataSource.class,
                Qualifiers.byName("one") // <2>
        );

        // end::beans[]
        assertNotNull(firstConfig);
    }
}
