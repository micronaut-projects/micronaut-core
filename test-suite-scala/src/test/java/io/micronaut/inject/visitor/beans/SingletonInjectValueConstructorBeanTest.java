package io.micronaut.inject.visitor.beans;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.DefaultApplicationContext;
import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class SingletonInjectValueConstructorBeanTest {

    @Test
    public void testApplicationContextJavaBean() {
        ApplicationContext applicationContext = new DefaultApplicationContext();
        applicationContext.start();

        test.java.TestSingletonInjectValueConstructorBean singletonBean = applicationContext.getBean(test.java.TestSingletonInjectValueConstructorBean.class);

        assertNotNull(singletonBean);
        assertEquals("injected String", singletonBean.getHost());
        assertEquals(42, singletonBean.getPort());
    }

    @Test
    public void testApplicationContextScalaBean() {
        ApplicationContext applicationContext = new DefaultApplicationContext();
        applicationContext.start();

        test.scala.TestSingletonInjectValueConstructorBean singletonBean = applicationContext.getBean(test.scala.TestSingletonInjectValueConstructorBean.class);

        assertNotNull(singletonBean);
        assertEquals("injected String", singletonBean.getHost());
        //assertEquals(42, singletonBean.getPort());
    }
}
