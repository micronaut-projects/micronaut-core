package io.micronaut.configuration.jmx.endpoint;

import io.micronaut.inject.BeanDefinition;

import javax.inject.Singleton;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.util.Hashtable;

@Singleton
public class DefaultEndpointNameGenerator implements EndpointNameGenerator {

    @Override
    public ObjectName generate(BeanDefinition beanDefinition, Object object) throws MalformedObjectNameException {
        Class type = beanDefinition.getBeanType();
        String pkg = type.getPackage().getName();
        Hashtable<String, String> properties = new Hashtable<>(1);
        properties.put("type", type.getSimpleName());

        return new ObjectName(pkg, properties);
    }
}
