package io.micronaut.jackson.beans;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.ClassIntrospector;

public class MicronautClassIntrospector extends ClassIntrospector {
    @Override
    public ClassIntrospector copy() {
        return new MicronautClassIntrospector();
    }

    @Override
    public BeanDescription forSerialization(SerializationConfig cfg, JavaType type, MixInResolver r) {
        return null;
    }

    @Override
    public BeanDescription forDeserialization(DeserializationConfig cfg, JavaType type, MixInResolver r) {
        return null;
    }

    @Override
    public BeanDescription forDeserializationWithBuilder(DeserializationConfig cfg, JavaType type, MixInResolver r) {
        return null;
    }

    @Override
    public BeanDescription forCreation(DeserializationConfig cfg, JavaType type, MixInResolver r) {
        return null;
    }

    @Override
    public BeanDescription forClassAnnotations(MapperConfig<?> cfg, JavaType type, MixInResolver r) {
        return null;
    }

    @Override
    public BeanDescription forDirectClassAnnotations(MapperConfig<?> cfg, JavaType type, MixInResolver r) {
        return null;
    }
}
