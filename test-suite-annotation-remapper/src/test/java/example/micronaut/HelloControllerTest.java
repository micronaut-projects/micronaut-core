package example.micronaut;

import io.micronaut.context.BeanContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
public class HelloControllerTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    BeanContext beanContext;

    @Test
    void testHelloWorldResponse() {
        String response = client.toBlocking()
            .retrieve(HttpRequest.GET("/hello"));
        assertEquals("""
            {"name":"Denis","age":123}""", response);
    }

    @Test
    void testNewAnnotationIsAdded() {
        BeanDefinition<HelloController> beanDefinition = beanContext.getBeanDefinition(HelloController.class);
        assertTrue(beanDefinition.hasAnnotation("Something"));
    }

}
