package io.micronaut.http.form;


import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Property(name = "micronaut.http.forms.max-decoded-key-value-parameters", value = "512")
@MicronautTest(startApplication = false)
class FormConfigurationViaPropertyTest {

    @Inject
    FormConfiguration formConfiguration;

    @Test
    void maxParamCanBeSetViaProperty() {
        assertEquals(512, formConfiguration.getMaxDecodedKeyValueParameters());
    }
}
