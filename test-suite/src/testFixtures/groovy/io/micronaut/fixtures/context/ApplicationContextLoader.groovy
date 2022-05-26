package io.micronaut.fixtures.context

import io.micronaut.context.ApplicationContext

class ApplicationContextLoader {
    static ApplicationContext load(List<File> classpath, String className = null) {
        URLClassLoader cl = new URLClassLoader(
                classpath.collect {it.toURI().toURL() } as URL[],
                ApplicationContextLoader.classLoader
        )
        def ocl = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = cl
        try {
            def mnClass = cl.loadClass("io.micronaut.runtime.Micronaut")
            def ctx = mnClass.build()
            if (className) {
                def appClass = cl.loadClass(className)
                ctx = ctx.mainClass(appClass)
            }
            return ctx.build()
        } finally {
            Thread.currentThread().contextClassLoader = ocl
        }
    }
}
