package io.micronaut.docs.server.intro;

// tag::imports[]
import io.micronaut.test.annotation.MicronautTest;
import javax.inject.Inject;
import static org.junit.jupiter.api.Assertions.assertEquals;
// end::imports[]

/**
 * @author graemerocher
 * @since 1.0
 */
// tag::class[]
@MicronautTest // <1>
public class HelloClientSpec  {

    @Inject
    HelloClient client; // <2>

    @Test
    public void testHelloWorldResponse(){
        assertEquals("Hello World", client.hello().blockingGet());// <3>
    }
}
// end::class[]
