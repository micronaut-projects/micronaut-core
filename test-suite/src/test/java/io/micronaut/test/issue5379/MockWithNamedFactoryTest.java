package io.micronaut.test.issue5379;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

// test for issue https://github.com/micronaut-projects/micronaut-core/issues/5379
@MicronautTest
@Property(name = "test.google.foo", value = "whatever")
public class MockWithNamedFactoryTest {
    @MockBean(AmazonDynamoDB.class)
    AmazonDynamoDB amazonDynamoDB() {
        return () -> "mocked";
    }

    @Test
    void testMockWithNamedFactory(SomeConfigurationList authControllerList, GoogleUserDetailsMapper detailsMapper) {
        Assertions.assertEquals(
                "mocked",
                detailsMapper.get()
        );
    }
}
