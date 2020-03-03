package io.micronaut.docs.config.env;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.PropertySource;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.junit.Test;
import io.micronaut.core.util.CollectionUtils;

import static org.junit.Assert.assertNotNull;
import static io.micronaut.docs.config.env.DataSourceFactory.*;
import java.net.URISyntaxException;
import java.util.Collection;

import static org.junit.Assert.assertEquals;

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
        assertEquals(2, beansOfType.size()); // <1>

        DataSource firstConfig = applicationContext.getBean(
                DataSource.class,
                Qualifiers.byName("one") // <2>
        );

        // end::beans[]
        assertNotNull(firstConfig);

        applicationContext.close();
    }
}
