package io.micronaut.docs.config.env;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.PropertySource;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;

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
}
