package example.failure;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.client.HttpClient;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Regardless whether running this is a test or main, it fails with a CircularDependency
 * because of the {@link TwoOptionalsFilter}.
 */
public class CircularDependencyFailureWithOptionalsAndKaptTest {

    public static void main(String[] args) throws MalformedURLException {
        ApplicationContext applicationContext = ApplicationContext.run();

        try {
            System.out.println(applicationContext.createBean(HttpClient.class, new URL("http://localhost:80")));
        } finally {
            applicationContext.stop();
        }
    }
}