package io.micronaut.docs.aop.lifecycle.pertarget.labelled;

import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LabelInterceptorTest {

    @Test
    void testEachTargetIsLabelledWithItsQualifier() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "labelled-channels.sms.sender", "Fred",
            "labelled-channels.email.sender", "Bob"))) {
            // tag::test[]
            Channel sms = context.getBean(Channel.class, Qualifiers.byName("sms"));
            Channel email = context.getBean(Channel.class, Qualifiers.byName("email"));

            assertEquals("[sms] Hello from Fred", sms.send("Hello"));
            assertEquals("[email] Hello from Bob", email.send("Hello"));
            // end::test[]
        }
    }
}
