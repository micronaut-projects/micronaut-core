package io.micronaut.docs.server.intro;

import io.micronaut.context.annotation.Property;
// tag::imports[]
import io.micronaut.test.annotation.MicronautTest;
import javax.inject.Inject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
// end::imports[]

@Property(name = "spec.name", value = "HelloControllerSpec")
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
