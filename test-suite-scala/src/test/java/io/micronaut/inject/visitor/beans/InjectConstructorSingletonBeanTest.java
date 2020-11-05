package io.micronaut.inject.visitor.beans;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.DefaultApplicationContext;
import org.junit.jupiter.api.Test;
import test.java.TestInjectSingletonBean;

import static org.junit.Assert.*;

public class InjectConstructorSingletonBeanTest {
    @Test
    public void testApplicationContextJavaBean() {
        ApplicationContext applicationContext = new DefaultApplicationContext();
        applicationContext.start();

        TestInjectSingletonBean singletonBean = applicationContext.getBean(TestInjectSingletonBean.class);

        assertNotNull(singletonBean);
        assertEquals("not injected", singletonBean.singletonBean.getNotInjected());
        assertEquals(2, singletonBean.engines.length);

        assertTrue(singletonBean.existingOptionalEngine.isPresent());
        assertFalse(singletonBean.nonExistingOptionalEngine.isPresent());

    }

    @Test
    public void testApplicationContextScalaBean() {
        ApplicationContext applicationContext = new DefaultApplicationContext();
        applicationContext.start();

        test.scala.TestInjectSingletonScalaBean singletonBean = applicationContext.getBean(test.scala.TestInjectSingletonScalaBean.class);

        assertNotNull(singletonBean);
        assertEquals("not injected", singletonBean.singletonBean().getNotInjected());
        assertEquals("not injected - scala", singletonBean.singletonScalaBean().getNotInjected());
    }
}
