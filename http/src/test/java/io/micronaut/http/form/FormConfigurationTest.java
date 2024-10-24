package io.micronaut.http.form;


import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest(startApplication = false)
class FormConfigurationTest {

    @Inject
    FormConfiguration formConfiguration;

    @Test
    void defaultMaxParams() {
        assertEquals(1024, formConfiguration.getMaxDecodedKeyValueParameters());
    }
}
