package io.micronaut.views.velocity;

import io.micronaut.context.annotation.Factory;
import org.apache.velocity.app.VelocityEngine;

import javax.inject.Singleton;
import java.util.Properties;

@Factory
public class VelocityFactory {

    @Singleton
    public VelocityEngine getVelocityEngine() {
        final Properties p = new Properties();
        p.setProperty("resource.loader", "class");
        p.setProperty("class.resource.loader.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
        return new VelocityEngine(p);
    }
}
