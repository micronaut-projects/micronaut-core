package io.micronaut.test.rxjava;

import io.micronaut.context.BeanContext;
import io.micronaut.rxjava3.http.client.Rx3StreamingHttpClient;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@MicronautTest
public class RxTest {

    @Inject
    BeanContext beanContext;

    @Test
    void testMicronaut4Inject() {
        Assertions.assertNotNull(beanContext.getBean(Rx3StreamingHttpClient.class));
    }

}
