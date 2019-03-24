package io.micronaut.docs.server.intro;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.util.HashMap;

import static org.junit.Assert.assertEquals;

/**
 * @author graemerocher
 * @since 1.0
 */
// tag::class-init[]
public class HelloClientSpec  {

    private static EmbeddedServer server;
    private static HelloClient client;

    @BeforeClass
    public static void setupServer() {
        // end::class-init[]
        server = ApplicationContext.run(EmbeddedServer.class,
                new HashMap<String, Object>() {{
                    put("spec.name", HelloControllerSpec.class.getSimpleName());
                    put("spec.lang", "java");
                }}
                , Environment.TEST);
        /*
// tag::embeddedServer[]
        server = ApplicationContext.run(EmbeddedServer.class) // <1>
// end::embeddedServer[]
        */
        // tag::class-end[]
        client = server
                .getApplicationContext()
                .getBean(HelloClient.class);// <2>
    }

    @AfterClass
    public static void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    public void testHelloWorldResponse(){
        assertEquals("Hello World", client.hello().blockingGet());// <3>
    }
}
// end::class-end[]
