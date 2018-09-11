package io.micronaut.http;

import java.util.Collections;
import java.util.List;

/**
 * Mutable version of {@link HttpParameters} which allows adding new parameters.
 *
 * @author Vladimir Orany
 */
public interface MutableHttpParameters extends HttpParameters {

    /**
     * Add new http parameter.
     * @param name      the name of the parameter
     * @param value     the value of the parameter
     * @return self
     */
    default MutableHttpParameters add(CharSequence name, CharSequence value) {
        add(name, Collections.singletonList(value));
        return this;
    }

    /**
     * Add new http parameter.
     * @param name      the name of the parameter
     * @param values    the values of the parameter
     * @return self
     */
    MutableHttpParameters add(CharSequence name, List<CharSequence> values);

}
