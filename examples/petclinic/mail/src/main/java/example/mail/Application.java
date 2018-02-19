package example.mail;

import org.particleframework.runtime.ParticleApplication;

import javax.inject.Singleton;

/**
 * @author sdelamo
 * @since 1.0
 */
@Singleton
public class Application {

    public static void main(String[] args) {
        ParticleApplication.run(Application.class);
    }
}
