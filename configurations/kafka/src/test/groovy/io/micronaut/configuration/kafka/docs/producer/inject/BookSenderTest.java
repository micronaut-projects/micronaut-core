package io.micronaut.configuration.kafka.docs.producer.inject;

import io.micronaut.configuration.kafka.config.AbstractKafkaConfiguration;
import io.micronaut.configuration.kafka.docs.consumer.batch.Book;
import io.micronaut.context.ApplicationContext;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

public class BookSenderTest {

    // tag::test[]
    @Test
    public void testBookSender() throws IOException {
        Map<String, Object> config = Collections.singletonMap( // <1>
                AbstractKafkaConfiguration.EMBEDDED, true
        );

        try (ApplicationContext ctx = ApplicationContext.run(config)) {
            BookSender bookSender = ctx.getBean(BookSender.class); // <2>
            Book book = new Book();
            book.setTitle("The Stand");
            bookSender.send("Stephen King", book);
        }
    }
    // end::test[]
}
