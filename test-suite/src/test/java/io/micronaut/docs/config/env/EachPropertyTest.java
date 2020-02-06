package io.micronaut.docs.config.env;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.PropertySource;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
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
        assertEquals(1000L, beansOfType.get(0).getLimit().longValue());
        assertEquals(5000L, beansOfType.get(1).getLimit().longValue());

        applicationContext.close();
    }
}
