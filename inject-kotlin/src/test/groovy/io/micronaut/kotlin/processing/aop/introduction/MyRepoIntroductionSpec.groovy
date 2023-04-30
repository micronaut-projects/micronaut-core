package io.micronaut.kotlin.processing.aop.introduction

import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.stream.Collectors

class MyRepoIntroductionSpec extends Specification {

    void "test generated introduction methods"() {
        when:
        def applicationContext = ApplicationContext.run()
        def bean = applicationContext.getBean(MyRepo)
        def interceptorDeclaredMethods = Arrays.stream(bean.getClass().getMethods()).filter(m -> m.getDeclaringClass() == bean.getClass()).collect(Collectors.toList())
        def repoDeclaredMethods = Arrays.stream(MyRepo.class.getMethods()).filter(m -> m.getDeclaringClass() == MyRepo.class).collect(Collectors.toList())
        def myRepoIntroducer = applicationContext.getBean(MyRepoIntroducer)

        then:
        repoDeclaredMethods.size() == 3
        interceptorDeclaredMethods.size() == 4
        bean.getClass().getName().contains("Intercepted")
        myRepoIntroducer.executableMethods.isEmpty()

        when:
        bean.aBefore()
        bean.xAfter()
        bean.findAll()

        then:
        myRepoIntroducer.executableMethods.size() == 3
        myRepoIntroducer.executableMethods.contains repoDeclaredMethods.find { method -> method.name == "aBefore" }
        myRepoIntroducer.executableMethods.contains repoDeclaredMethods.find { method -> method.name == "xAfter" }
        myRepoIntroducer.executableMethods.contains repoDeclaredMethods.find { method -> method.name == "findAll" && method.returnType == List.class }

        cleanup:
        applicationContext.close()
    }

    void "test interface overridden method"() {
        when:
        def applicationContext = ApplicationContext.run()
        def bean = applicationContext.getBean(CustomCrudRepo)
        def beanDef = applicationContext.getBeanDefinition(CustomCrudRepo)
        def findByIdMethods = beanDef.getExecutableMethods().findAll(m -> m.getName() == "findById")
        def myRepoIntroducer = applicationContext.getBean(MyRepoIntroducer)

        then:
        myRepoIntroducer.executableMethods.size() == 0
        findByIdMethods.size() == 1
        findByIdMethods[0].hasAnnotation(Marker)

        when:
        bean.findById(111)

        then:
        myRepoIntroducer.executableMethods.size() == 1
        myRepoIntroducer.executableMethods.clear()

        when:
        CrudRepo<Object, Object> crudRepo = bean
        crudRepo.findById(111)

        then:
        myRepoIntroducer.executableMethods.size() == 1

        cleanup:
        applicationContext.close()
    }

    void "test interface abstract overridden method"() {
        when:
        def applicationContext = ApplicationContext.run()
        def bean = applicationContext.getBean(AbstractCustomCrudRepo)
        def beanDef = applicationContext.getBeanDefinition(AbstractCustomCrudRepo)
        def findByIdMethods = beanDef.getExecutableMethods().findAll(m -> m.getName() == "findById")
        def myRepoIntroducer = applicationContext.getBean(MyRepoIntroducer)

        then:
        myRepoIntroducer.executableMethods.size() == 0
        findByIdMethods.size() == 1
        findByIdMethods[0].hasAnnotation(Marker)

        when:
        bean.findById(111)

        then:
        myRepoIntroducer.executableMethods.size() == 1
        myRepoIntroducer.executableMethods.clear()

        when:
        CrudRepo<Object, Object> crudRepo = bean
        crudRepo.findById(111)

        then:
        myRepoIntroducer.executableMethods.size() == 1

        cleanup:
        applicationContext.close()
    }

    void "test abstract overridden method"() {
        when:
        def applicationContext = ApplicationContext.run()
        def bean = applicationContext.getBean(AbstractCustomAbstractCrudRepo)
        def beanDef = applicationContext.getBeanDefinition(AbstractCustomAbstractCrudRepo)
        def findByIdMethods = beanDef.getExecutableMethods().findAll(m -> m.getName() == "findById")
        def myRepoIntroducer = applicationContext.getBean(MyRepoIntroducer)

        then:
        myRepoIntroducer.executableMethods.size() == 0
        findByIdMethods.size() == 1
        findByIdMethods[0].hasAnnotation(Marker)

        when:
        bean.findById(111)

        then:
        myRepoIntroducer.executableMethods.size() == 1
        myRepoIntroducer.executableMethods.clear()

        when:
        AbstractCrudRepo<Object, Object> crudRepo = bean
        crudRepo.findById(111)

        then:
        myRepoIntroducer.executableMethods.size() == 1

        cleanup:
        applicationContext.close()
    }

    void "test overridden void methods"() {
        when:
        def applicationContext = ApplicationContext.run()
        def bean = applicationContext.getBean(MyRepo2)
        def myRepoIntroducer = applicationContext.getBean(MyRepoIntroducer)
        bean.deleteById(1)

        then:
        myRepoIntroducer.executableMethods.size() == 1
        myRepoIntroducer.executableMethods.clear()

        cleanup:
        applicationContext.close()
    }

}
