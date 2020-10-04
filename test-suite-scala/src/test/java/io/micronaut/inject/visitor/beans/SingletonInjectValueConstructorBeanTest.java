package io.micronaut.inject.visitor.beans;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.DefaultApplicationContext;
import org.junit.jupiter.api.Test;

import static org.junit.Assert.*;

public class SingletonInjectValueConstructorBeanTest {

    @Test
    public void testApplicationContextJavaBean() {
        ApplicationContext applicationContext = new DefaultApplicationContext();
        applicationContext.start();

        test.java.TestSingletonInjectValueConstructorBean singletonBean = applicationContext.getBean(test.java.TestSingletonInjectValueConstructorBean.class);

        assertNotNull(singletonBean);
        assertEquals("injected String", singletonBean.injectedString);
        assertEquals(41, singletonBean.injectedByte);
        assertEquals(42, singletonBean.injectedShort);
        assertEquals(43, singletonBean.injectedInt);
        assertEquals(44, singletonBean.injectedLong);
        assertEquals(44.1f, singletonBean.injectedFloat, 0.00001);
        assertEquals(44.2f, singletonBean.injectedDouble, 0.00001);
        assertEquals('#', singletonBean.injectedChar);
        assertTrue(singletonBean.injectedBoolean);
        assertEquals(45, singletonBean.lookUpInteger);
        assertEquals(46, singletonBean.injectIntField);
    }

    @Test
    public void testApplicationContextScalaBean() {
        ApplicationContext applicationContext = new DefaultApplicationContext();
        applicationContext.start();

        test.scala.TestSingletonInjectValueConstructorBean singletonBean = applicationContext.getBean(test.scala.TestSingletonInjectValueConstructorBean.class);

        assertNotNull(singletonBean);
        assertEquals("injected String", singletonBean.injectedString());
        assertEquals(41, singletonBean.injectedByte());
        assertEquals(42, singletonBean.injectedShort());
        assertEquals(43, singletonBean.injectedInt());
        assertEquals(44, singletonBean.injectedLong());
        assertEquals(44.1f, singletonBean.injectedFloat(), 0.00001);
        assertEquals(44.2f, singletonBean.injectedDouble(), 0.00001);
        assertEquals('#', singletonBean.injectedChar());
        assertTrue(singletonBean.injectedBoolean());
        assertEquals(45, singletonBean.lookUpInteger());
        assertEquals(46, singletonBean.injectIntField());
    }
}
