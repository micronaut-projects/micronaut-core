package io.micronaut.inject.qualifiers.compose;

import io.micronaut.context.BeanContext;
import org.junit.Assert;
import org.junit.Test;
import spock.lang.Issue;

public class CompositionTest {

    @Test
    @Issue("#609")
    public void testComposition() {
        try (final BeanContext context = BeanContext.run()) {
            final int result = context.getBean(Thing.class).getNumber();
            Assert.assertEquals("Should have resolved 3 candidates for annotation qualifier", 3, result);
        }
    }
}
