package io.micronaut.inject.visitor.beans;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.DefaultApplicationContext;
import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertNotNull;

public class SingletonBeanTest {
    @Test
    public void testApplicationContextScalaBean() {
        ApplicationContext applicationContext = new DefaultApplicationContext();
        applicationContext.start();

        test.scala.TestSingletonBean singletonBean = applicationContext.getBean(test.scala.TestSingletonBean.class);

        assertNotNull(singletonBean);
    }

    @Test
    public void testApplicationContextJavaBean() {
        ApplicationContext applicationContext = new DefaultApplicationContext();
        applicationContext.start();

        test.java.TestSingletonBean singletonBean = applicationContext.getBean(test.java.TestSingletonBean.class);

        assertNotNull(singletonBean);
    }
}
