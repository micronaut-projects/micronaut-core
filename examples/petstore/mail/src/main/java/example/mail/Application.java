package example.mail;


import io.micronaut.runtime.Micronaut;

import javax.inject.Singleton;

/**
 * @author sdelamo
 * @since 1.0
 */
public class Application {

    public static void main(String[] args) {
        Micronaut.run(Application.class);
    }
}
