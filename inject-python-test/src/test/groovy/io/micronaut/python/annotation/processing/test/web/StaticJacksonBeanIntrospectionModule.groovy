package io.micronaut.python.annotation.processing.test.web

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Replaces
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.naming.NameUtils
import jakarta.inject.Singleton

@Singleton
@Replaces(io.micronaut.jackson.modules.BeanIntrospectionModule)
 class StaticJacksonBeanIntrospectionModule extends io.micronaut.jackson.modules.BeanIntrospectionModule {
    private final ApplicationContext applicationContext

    StaticJacksonBeanIntrospectionModule(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext
    }

    @Override
    protected BeanIntrospection<Object> findIntrospection(Class<?> beanClass) {
        def className = beanClass.name
        def simpleName = NameUtils.getSimpleName(className)
        def beanDefName = (simpleName.startsWith('$') ? '' : '$') + simpleName + '$Introspection'
        def packageName = NameUtils.getPackageName(className)
        String beanFullName = "${packageName}.${beanDefName}"

        try {
            return (BeanIntrospection)applicationContext.classLoader.loadClass(beanFullName).newInstance()
        } catch (ClassNotFoundException e) {
            return null
        }
    }
}
