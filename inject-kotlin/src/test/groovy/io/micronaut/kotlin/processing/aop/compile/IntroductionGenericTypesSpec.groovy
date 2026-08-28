package io.micronaut.kotlin.processing.aop.compile

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.ReturnType
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.InstantiatableBeanDefinition
import io.micronaut.inject.writer.BeanDefinitionVisitor
import spock.lang.Specification

import static io.micronaut.annotation.processing.test.KotlinCompiler.*

class IntroductionGenericTypesSpec extends Specification {

    void "test that generic return types are correct when implementing an interface with type arguments"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test

import io.micronaut.kotlin.processing.aop.introduction.Stub
import io.micronaut.context.annotation.*
import java.net.URL

interface MyInterface<T: URL> {

    fun getURL(): T

    fun getURLs(): List<T>
}


@Stub
@jakarta.inject.Singleton
@Executable
interface MyBean: MyInterface<URL>

''')
        then:
        !beanDefinition.isAbstract()
        beanDefinition != null
        beanDefinition.injectedFields.size() == 0
        beanDefinition.executableMethods.size() == 2

        beanDefinition.getRequiredMethod("getURL").targetMethod.returnType == URL
        beanDefinition.getRequiredMethod("getURL").returnType.type == URL
        beanDefinition.getRequiredMethod("getURLs").returnType.type == List
        beanDefinition.getRequiredMethod("getURLs").returnType.asArgument().hasTypeVariables()
        beanDefinition.getRequiredMethod("getURLs").returnType.asArgument().typeVariables['E'].type == URL
    }

    void "test that generic return types are correct when implementing an interface with type arguments 2"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test

import io.micronaut.kotlin.processing.aop.introduction.Stub
import io.micronaut.context.annotation.*
import java.net.URL

interface MyInterface<T: Person> {

    fun getPeopleSingle(): reactor.core.publisher.Mono<List<T>>

    fun getPerson(): T

    fun getPeople(): List<T>

    fun save(person: T)

    fun saveAll(person: List<T>)

    fun getPeopleArray(): Array<T>

    fun getPeopleListArray(): List<Array<T>>

    fun <V: URL> getPeopleMap(): Map<T,V>
}

@Stub
@jakarta.inject.Singleton
@Executable
interface MyBean: MyInterface<SubPerson>

open class Person

class SubPerson: Person()

''')
        then:
        !beanDefinition.isAbstract()
        beanDefinition != null
        returnType(beanDefinition, "getPerson").type.name == 'test.SubPerson'
        returnType(beanDefinition, "getPeople").type == List
        returnType(beanDefinition, "getPeople").asArgument().hasTypeVariables()
        returnType(beanDefinition, "getPeople").asArgument().typeVariables['E'].type.name == 'test.SubPerson'
        returnType(beanDefinition, "getPeopleMap").typeVariables['K'].type.name == 'test.SubPerson'
        returnType(beanDefinition, "getPeopleMap").typeVariables['V'].type == URL
        returnType(beanDefinition, "getPeopleArray").type.isArray()
        returnType(beanDefinition, "getPeopleArray").type.name.contains('test.SubPerson')
        returnType(beanDefinition, "getPeopleListArray").type == List
        returnType(beanDefinition, "getPeopleListArray").typeVariables['E'].type.isArray()
        beanDefinition.findPossibleMethods("save").findFirst().get().targetMethod != null
        beanDefinition.findPossibleMethods("getPerson").findFirst().get().targetMethod != null
        def getPeopleSingle = returnType(beanDefinition, "getPeopleSingle")
        getPeopleSingle.typeVariables['T'].type== List
        getPeopleSingle.typeVariables['T'].typeVariables['E'].type.name == 'test.SubPerson'


        when:
        ApplicationContext context = ApplicationContext.run()
        def instance = ((InstantiatableBeanDefinition)beanDefinition).instantiate(context)

        then:"the methods are invocable"
        instance.getPerson() == null
        instance.getPeople() == null
        instance.getPeopleArray() == null
        instance.getPeopleSingle() == null
        instance.save(null) == null
        instance.saveAll([]) == null

        cleanup:
        context.close()
    }

    ReturnType returnType(BeanDefinition bd, String name) {
        bd.findPossibleMethods(name).findFirst().get().returnType
    }
}
