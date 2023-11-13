package io.micronaut.docs.config.immutable;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Property(name = "spec.name", value="ValueAddedTaxConfigurationTest")
@Property(name = "vat.percentage", value = "21.0")
@MicronautTest(startApplication = false)
class ValueAddedTaxConfigurationTest {

    @Inject
    BeanContext beanContext;

    @Test
    void immutableConfigurationViaJavaRecords() {
        assertTrue(beanContext.containsBean(ValueAddedTaxConfiguration.class));
        assertEquals(new BigDecimal("21.0"), beanContext.getBean(ValueAddedTaxConfiguration.class).percentage());
    }
}
