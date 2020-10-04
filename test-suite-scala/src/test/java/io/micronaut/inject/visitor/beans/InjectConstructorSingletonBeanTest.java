package io.micronaut.inject.visitor.beans;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.DefaultApplicationContext;
import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class InjectConstructorSingletonBeanTest {
    @Test
    public void testApplicationContextJavaBean() {
        ApplicationContext applicationContext = new DefaultApplicationContext();
        applicationContext.start();

        test.java.TestInjectConstructorSingletonBean singletonBean = applicationContext.getBean(test.java.TestInjectConstructorSingletonBean.class);

        assertNotNull(singletonBean);
        assertEquals("not injected", singletonBean.singletonBean.getNotInjected());
       // assertEquals("not injected", singletonBean.singletonScalaBean.getNotInjected());
        assertEquals(2, singletonBean.engines.length);
    }

    @Test
    public void testApplicationContextScalaBean() {
        ApplicationContext applicationContext = new DefaultApplicationContext();
        applicationContext.start();

        test.scala.TestInjectConstructorSingletonScalaBean singletonBean = applicationContext.getBean(test.scala.TestInjectConstructorSingletonScalaBean.class);

        assertNotNull(singletonBean);
        assertEquals("not injected", singletonBean.singletonBean().getNotInjected());
        assertEquals("not injected - scala", singletonBean.singletonScalaBean().getNotInjected());
    }
}
