package io.micronaut.inject.executable.inheritance

import io.micronaut.AbstractBeanDefinitionSpec
import io.micronaut.inject.BeanDefinition

class InheritedExecutableSpec extends AbstractBeanDefinitionSpec {

    void "test extending an abstract class with an executable method"() {
        when:
        BeanDefinition definition = buildBeanDefinition("test.GenericController", """
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

abstract class GenericController<T> {

    public abstract String getPath();

    @Executable
    public String save(T entity) {
        return "parent";
    }
}

""")
        then:
        definition == null
    }

    void "test the same method isn't written twice"() {
        BeanDefinition definition = buildBeanDefinition("test.StatusController", """
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;
import javax.inject.Singleton;

@Executable
@Singleton
class StatusController extends GenericController<String> {

    @Override
    public String getPath() {
        return "/statuses";
    }

    @Override
    public String save(String entity) {
        return "child";
    }

}

abstract class GenericController<T> {

    public abstract String getPath();

    @Executable
    public String save(T entity) {
        return "parent";
    }
    
    @Executable
    public String save() {
        return "parent";
    }
}

""")
        expect:
        definition != null
        definition.getExecutableMethods().any { it.methodName == "getPath" }
        definition.getExecutableMethods().any { it.methodName == "save" && it.argumentTypes == [String] as Class[] }
        definition.getExecutableMethods().any { it.methodName == "save" && it.argumentTypes.length == 0 }

        when:
        definition.getClass().getClassLoader().loadClass('test.$StatusControllerDefinition$$exec1')
        definition.getClass().getClassLoader().loadClass('test.$StatusControllerDefinition$$exec2')
        definition.getClass().getClassLoader().loadClass('test.$StatusControllerDefinition$$exec3')

        then:
        noExceptionThrown()

        when: //there should only be 2 executable methods
        definition.getClass().getClassLoader().loadClass('test.$StatusControllerDefinition$$exec4')

        then:
        thrown(ClassNotFoundException)
    }

    void "test with multiple generics"() {
        BeanDefinition definition = buildBeanDefinition("test.StatusController","""
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;
import javax.inject.Singleton;

abstract class GenericController<T, ID extends Serializable> {

    @Executable
    public T save(T entity) {
        return entity;
    }

    @Executable
    public T find(ID id) {
        return null;
    }

    public abstract T create(ID id);
}

@Executable
@Singleton
class StatusController extends GenericController<String, Integer> {

    public String create(Integer id) {
        return id.toString();
    }
}
""")
        expect:
        definition != null
        definition.getExecutableMethods().any { it.methodName == "create" && it.argumentTypes == [Integer] as Class[] }
        definition.getExecutableMethods().any { it.methodName == "save" && it.argumentTypes == [String] as Class[] }
        definition.getExecutableMethods().any { it.methodName == "find" && it.argumentTypes == [Integer] as Class[] }

        when:
        definition.getClass().getClassLoader().loadClass('test.$StatusControllerDefinition$$exec1')
        definition.getClass().getClassLoader().loadClass('test.$StatusControllerDefinition$$exec2')
        definition.getClass().getClassLoader().loadClass('test.$StatusControllerDefinition$$exec3')

        then:
        noExceptionThrown()

        when: //there should only be 2 executable methods
        definition.getClass().getClassLoader().loadClass('test.$StatusControllerDefinition$$exec4')

        then:
        thrown(ClassNotFoundException)
    }

    void "test multiple inheritance"() {
        BeanDefinition definition = buildBeanDefinition("test.Z", """
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;
import javax.inject.Singleton;

interface X {

    @Executable
    void test();
}

abstract class Y implements X {

    @Override
    public void test() {
        
    }
    
}

@Singleton
class Z extends Y {

    @Override
    public void test() {
        
    }
}
""")
        expect:
        definition != null
        definition.executableMethods.size() == 1
    }
}
