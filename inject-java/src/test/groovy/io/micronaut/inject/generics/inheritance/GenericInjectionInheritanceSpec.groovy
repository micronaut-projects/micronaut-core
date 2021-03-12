package io.micronaut.inject.generics.inheritance

import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class GenericInjectionInheritanceSpec extends Specification {
    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run()

    void "test generic injection with inheritance"() {
        given:
        def userClient = context.getBean(UserDaoClient)
        def jobClient = context.getBean(JobDaoClient)

        expect:
        userClient.dao instanceof UserDao
        userClient.anotherDao instanceof UserDao
        userClient.dao.is(userClient.anotherDao)
        userClient.constructorDao instanceof UserDao
        jobClient.dao instanceof JobDao
        jobClient.constructorDao instanceof JobDao
    }
}
