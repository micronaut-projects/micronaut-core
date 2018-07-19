package io.micronaut.configuration.kafka.docs;

import io.micronaut.configuration.kafka.config.AbstractKafkaConfiguration;
import io.micronaut.configuration.kafka.docs.quickstart.ProductClient;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.util.CollectionUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class DocTests {

    static ApplicationContext applicationContext;

    @BeforeClass
    public static void setup() {
        applicationContext = ApplicationContext.run(
                CollectionUtils.mapOf(
                        "kafka.bootstrap.servers", "localhost:${random.port}",
                        AbstractKafkaConfiguration.EMBEDDED, true
            )
        );
    }

    @AfterClass
    public static void cleanup() {
        applicationContext.stop();
    }


    @Test
    public void testSendProduct() {
        // tag::quickstart[]
        ProductClient client = applicationContext.getBean(ProductClient.class);
        client.sendProduct("Nike", "Blue Trainers");
        // end::quickstart[]
    }
}
