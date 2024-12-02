plugins {
    id("io.micronaut.build.internal.convention-library")
}

dependencies {
    annotationProcessor(projects.micronautInjectJava)

    api(projects.micronautInject)
    api(projects.micronautHttp)
    compileOnly(libs.managed.groovy)

    testImplementation(projects.micronautContext)
    testImplementation(projects.micronautInjectGroovy)
    testImplementation(projects.micronautInjectJava)
    testAnnotationProcessor(projects.micronautInjectJava)
    testImplementation(projects.micronautInjectGroovyTest)
}


//compileTestGroovy.groovyOptions.forkOptions.jvmArgs = ['-Xdebug', '-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005']
//compileTestGroovy.groovyOptions.fork = true
