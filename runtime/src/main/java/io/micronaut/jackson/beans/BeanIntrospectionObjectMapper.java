package io.micronaut.jackson.beans;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.ClassIntrospector;

/**
 * Variation of Jackson's default {@link ObjectMapper} that uses Micronaut's
 * introspection API to avoid reflection.
 *
 * @author graemerocher
 * @since 1.1
 */
public class BeanIntrospectionObjectMapper extends ObjectMapper {

    public BeanIntrospectionObjectMapper() {
    }

    public BeanIntrospectionObjectMapper(JsonFactory jf) {
        super(jf);
    }

    @Override
    protected ClassIntrospector defaultClassIntrospector() {
        return new MicronautClassIntrospector();
    }
}
