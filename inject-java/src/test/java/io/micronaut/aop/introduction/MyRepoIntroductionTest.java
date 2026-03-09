package io.micronaut.aop.introduction;

import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java port of MyRepoIntroductionSpec.
 */
class MyRepoIntroductionTest {

    @AfterEach
    void cleanup() {
        MyRepoIntroducer.EXECUTED_METHODS.clear();
        TxInterceptor.EXECUTED_METHODS.clear();
    }

    @Test
    void testGeneratedIntroductionMethods() {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
        MyRepo bean = applicationContext.getBean(MyRepo.class);

        List<Method> interceptorDeclaredMethods = Arrays.stream(bean.getClass().getMethods())
            .filter(m -> m.getDeclaringClass() == bean.getClass())
            .collect(Collectors.toList());

        List<Method> repoDeclaredMethods = Arrays.stream(MyRepo.class.getMethods())
            .filter(m -> m.getDeclaringClass() == MyRepo.class)
            .collect(Collectors.toList());

        // expectations
        assertEquals(4, repoDeclaredMethods.size());
        assertEquals(4, interceptorDeclaredMethods.size());
        assertTrue(bean.getClass().getName().contains("Intercepted"));
        assertTrue(MyRepoIntroducer.EXECUTED_METHODS.isEmpty());

        // invoke introduced methods
        bean.aBefore();
        bean.xAfter();
        bean.findAll();

        assertEquals(3, MyRepoIntroducer.EXECUTED_METHODS.size());
        assertTrue(
            MyRepoIntroducer.EXECUTED_METHODS.contains(
                repoDeclaredMethods.stream().filter(m -> m.getName().equals("aBefore")).findFirst().orElseThrow()
            )
        );
        assertTrue(
            MyRepoIntroducer.EXECUTED_METHODS.contains(
                repoDeclaredMethods.stream().filter(m -> m.getName().equals("xAfter")).findFirst().orElseThrow()
            )
        );
        assertTrue(
            MyRepoIntroducer.EXECUTED_METHODS.contains(
                repoDeclaredMethods.stream().filter(m -> m.getName().equals("findAll") && m.getReturnType() == List.class).findFirst().orElseThrow()
            )
        );
        MyRepoIntroducer.EXECUTED_METHODS.clear();
        }
    }

    @Test
    void testInterfaceOverriddenMethod() {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
        CustomCrudRepo bean = applicationContext.getBean(CustomCrudRepo.class);
        BeanDefinition<CustomCrudRepo> beanDef = applicationContext.getBeanDefinition(CustomCrudRepo.class);
        List<ExecutableMethod<?, ?>> findByIdMethods = beanDef.getExecutableMethods().stream()
            .filter(m -> m.getMethodName().equals("findById"))
            .collect(Collectors.toList());

        assertEquals(0, MyRepoIntroducer.EXECUTED_METHODS.size());
        assertEquals(1, findByIdMethods.size());
        assertTrue(findByIdMethods.get(0).hasAnnotation(Marker.class));

        bean.findById(111L);
        assertEquals(1, MyRepoIntroducer.EXECUTED_METHODS.size());
        MyRepoIntroducer.EXECUTED_METHODS.clear();

        CrudRepo<Object, Object> crudRepo = (CrudRepo<Object, Object>) (Object) bean;
        crudRepo.findById(111L);
        assertEquals(1, MyRepoIntroducer.EXECUTED_METHODS.size());
        MyRepoIntroducer.EXECUTED_METHODS.clear();
        }
    }

    @Test
    void testInterfaceAbstractOverriddenMethod() {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
        AbstractCustomCrudRepo bean = applicationContext.getBean(AbstractCustomCrudRepo.class);
        BeanDefinition<AbstractCustomCrudRepo> beanDef = applicationContext.getBeanDefinition(AbstractCustomCrudRepo.class);
        List<ExecutableMethod<?, ?>> findByIdMethods = beanDef.getExecutableMethods().stream()
            .filter(m -> m.getMethodName().equals("findById"))
            .collect(Collectors.toList());

        assertEquals(0, MyRepoIntroducer.EXECUTED_METHODS.size());
        assertEquals(1, findByIdMethods.size());
        assertTrue(findByIdMethods.get(0).hasAnnotation(Marker.class));

        bean.findById(111L);
        assertEquals(1, MyRepoIntroducer.EXECUTED_METHODS.size());
        MyRepoIntroducer.EXECUTED_METHODS.clear();

        CrudRepo<Object, Object> crudRepo = (CrudRepo<Object, Object>) (Object) bean;
        crudRepo.findById(111L);
        assertEquals(1, MyRepoIntroducer.EXECUTED_METHODS.size());
        MyRepoIntroducer.EXECUTED_METHODS.clear();
        }
    }

    @Test
    void testAbstractOverriddenMethod() {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
        AbstractCustomAbstractCrudRepo bean = applicationContext.getBean(AbstractCustomAbstractCrudRepo.class);
        BeanDefinition<AbstractCustomAbstractCrudRepo> beanDef = applicationContext.getBeanDefinition(AbstractCustomAbstractCrudRepo.class);
        List<ExecutableMethod<?, ?>> findByIdMethods = beanDef.getExecutableMethods().stream()
            .filter(m -> m.getMethodName().equals("findById"))
            .collect(Collectors.toList());

        assertEquals(0, MyRepoIntroducer.EXECUTED_METHODS.size());
        assertEquals(1, findByIdMethods.size());
        assertTrue(findByIdMethods.get(0).hasAnnotation(Marker.class));

        bean.findById(111L);
        assertEquals(1, MyRepoIntroducer.EXECUTED_METHODS.size());
        MyRepoIntroducer.EXECUTED_METHODS.clear();

        AbstractCrudRepo<Object, Object> crudRepo = (AbstractCrudRepo<Object, Object>) (Object) bean;
        crudRepo.findById(111L);
        assertEquals(1, MyRepoIntroducer.EXECUTED_METHODS.size());
        MyRepoIntroducer.EXECUTED_METHODS.clear();
        }
    }

    @Test
    void testReturnTypeAnnotationsAreMethodAnnotations() {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
        BeanDefinition<CustomCrudRepo2> beanDef = applicationContext.getBeanDefinition(CustomCrudRepo2.class);
        ExecutableMethod<?, ?> custom1Method = beanDef.getExecutableMethods().stream()
            .filter(m -> m.getMethodName().equals("custom1"))
            .findFirst().orElseThrow();
        ExecutableMethod<?, ?> custom2Method = beanDef.getExecutableMethods().stream()
            .filter(m -> m.getMethodName().equals("custom2"))
            .findFirst().orElseThrow();

        assertFalse(custom1Method.hasAnnotation(Marker.class));
        assertTrue(custom2Method.hasAnnotation(Marker.class));
        assertFalse(custom1Method.getReturnType().getAnnotationMetadata().hasAnnotation(Marker.class));
        assertTrue(custom2Method.getReturnType().getAnnotationMetadata().hasAnnotation(Marker.class));
        }
    }

    @Test
    void testOverriddenVoidMethods() {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
        MyRepo2 bean = applicationContext.getBean(MyRepo2.class);
        bean.deleteById(1);
        assertEquals(1, MyRepoIntroducer.EXECUTED_METHODS.size());
        MyRepoIntroducer.EXECUTED_METHODS.clear();
        }
    }

    @Test
    void testTxInterfaceRepoMethods() {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
        MyRepo3 bean = applicationContext.getBean(MyRepo3.class);
        bean.deleteById(1);
        assertEquals(1, MyRepoIntroducer.EXECUTED_METHODS.size());
        MyRepoIntroducer.EXECUTED_METHODS.clear();
        assertEquals(1, TxInterceptor.EXECUTED_METHODS.size());
        TxInterceptor.EXECUTED_METHODS.clear();
        }
    }

    @Test
    void testTxAbstractRepoMethods() {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
        MyRepo4 bean = applicationContext.getBean(MyRepo4.class);

        bean.deleteById(1);
        assertEquals(1, MyRepoIntroducer.EXECUTED_METHODS.size());
        MyRepoIntroducer.EXECUTED_METHODS.clear();
        assertEquals(1, TxInterceptor.EXECUTED_METHODS.size());
        TxInterceptor.EXECUTED_METHODS.clear();

        bean.findById(1);
        assertEquals(1, TxInterceptor.EXECUTED_METHODS.size());
        TxInterceptor.EXECUTED_METHODS.clear();
        }
    }

    @Test
    void testTxDefaultRepoMethods() {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
        MyRepo5 bean = applicationContext.getBean(MyRepo5.class);

        bean.deleteById(1);
        assertEquals(1, MyRepoIntroducer.EXECUTED_METHODS.size());
        MyRepoIntroducer.EXECUTED_METHODS.clear();
        assertEquals(1, TxInterceptor.EXECUTED_METHODS.size());
        TxInterceptor.EXECUTED_METHODS.clear();

        bean.findById(1);
        assertEquals(1, TxInterceptor.EXECUTED_METHODS.size());
        TxInterceptor.EXECUTED_METHODS.clear();
        }
    }

    @Test
    void testDelegatingMethods() {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
        MyRepo6Service myRepoService = applicationContext.getBean(MyRepo6Service.class);

        myRepoService.deleteById(1);
        assertEquals(1, MyRepoIntroducer.EXECUTED_METHODS.size());
        MyRepoIntroducer.EXECUTED_METHODS.clear();
        assertEquals(1, TxInterceptor.EXECUTED_METHODS.size());
        TxInterceptor.EXECUTED_METHODS.clear();
        }
    }
}
