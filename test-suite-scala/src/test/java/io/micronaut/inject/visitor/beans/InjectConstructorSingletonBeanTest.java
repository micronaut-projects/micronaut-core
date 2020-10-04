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
    }

    @Test
    public void testApplicationContextScalaBean() {
        ApplicationContext applicationContext = new DefaultApplicationContext();
        applicationContext.start();

        test.scala.TestInjectConstructorSingletonBean singletonBean = applicationContext.getBean(test.scala.TestInjectConstructorSingletonBean.class);

        assertNotNull(singletonBean);
        assertEquals("not injected", singletonBean.singletonBean().getNotInjected());
    }
}
