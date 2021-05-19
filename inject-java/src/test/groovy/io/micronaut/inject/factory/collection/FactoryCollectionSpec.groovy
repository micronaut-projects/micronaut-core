package io.micronaut.inject.factory.collection

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.NonUniqueBeanException
import io.micronaut.core.type.Argument
import io.micronaut.inject.qualifiers.Qualifiers

class FactoryCollectionSpec extends AbstractTypeElementSpec {

    void 'test produce multiple prototype beans from a factory'() {
        given:
        ApplicationContext context = buildContext('''
package test;

import io.micronaut.context.annotation.*;
import java.util.*;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import jakarta.inject.*;
import io.micronaut.context.BeanProvider;
@Singleton
class Catalogue {
    @All
    @Inject
    List<Product> all;
    
    @WishList
    @Inject
    Set<Product> wishlist;
    
    @Any
    @Inject
    BeanProvider<Product> provider;
}

@Factory
class Shop {

   @Bean @All
   public List<Product> getAllProducts() { 
        return Arrays.asList(
            new Product("one"),
            new Product("two"),
            new Product("three")
        ); 
   }

   @Bean @WishList
   public List<Product> getWishList() { 
        return Arrays.asList(
            new Product("four"),
            new Product("five")
        );   
   }
}

@Qualifier
@Retention(RUNTIME)
@interface WishList {}


@Qualifier
@Retention(RUNTIME)
@interface All {}

class Product {
    public final String name;
    Product(String name) {
        this.name = name;
    }
}
''')
        when:
        def productClass = context.classLoader.loadClass('test.Product')
        def shopClass = context.classLoader.loadClass('test.Shop')
        def bean = getBean(context, 'test.Catalogue')

        then:
        context.getBeanDefinitions(shopClass).size() == 1
        context.getBeanDefinitions(shopClass, Qualifiers.byStereotype("test.WishList")).size() == 0
        bean.wishlist.size() == 2
        bean.wishlist.find { it.name == 'four' }
        bean.all.size() == 3
        bean.provider.stream().count() == 5
        context.containsBean(productClass)

        when:"A the container element is looked up"
        context.getBean(productClass)

        then:"A non-unique bean exception is thrown"
        thrown(NonUniqueBeanException)

        when:"A the container element is looked up with a specific qualifier"
        context.getBean(productClass, Qualifiers.byStereotype("test.WishList"))

        then:"A non-unique bean exception is thrown"
        thrown(NonUniqueBeanException)

        when:"The container type is looked up"
        List beans = context.getBean(Argument.listOf(productClass), Qualifiers.byStereotype("test.WishList"))

        then:"The beans are correct"
        beans.size() == 2
        beans.find { it.name == 'four' }

        cleanup:
        context.close()
    }

    void 'test produce multiple singleton beans from a factory'() {
        given:
        ApplicationContext context = buildContext('''
package test;

import io.micronaut.context.annotation.*;
import java.util.*;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import jakarta.inject.*;
import io.micronaut.context.BeanProvider;
@Singleton
class Catalogue {
    @All
    @Inject
    List<Product> all;
    
    @WishList
    @Inject
    Set<Product> wishlist;
    
    @Any
    @Inject
    BeanProvider<Product> provider;
}

@Factory
class Shop {

   @Singleton @All
   public List<Product> getAllProducts() { 
        return Arrays.asList(
            new Product("one"),
            new Product("two"),
            new Product("three")
        ); 
   }

   @Singleton @WishList
   public List<Product> getWishList() { 
        return Arrays.asList(
            new Product("four"),
            new Product("five")
        );   
   }
}

@Qualifier
@Retention(RUNTIME)
@interface WishList {}


@Qualifier
@Retention(RUNTIME)
@interface All {}

class Product {
    public final String name;
    Product(String name) {
        this.name = name;
    }
}
''')
        when:
        def productClass = context.classLoader.loadClass('test.Product')
        def bean = getBean(context, 'test.Catalogue')

        then:
        bean.wishlist.size() == 2
        bean.wishlist.find { it.name == 'four' }
        bean.all.size() == 3
        bean.provider.stream().count() == 5
        context.containsBean(productClass)

        when:"A the container element is looked up"
        context.getBean(productClass)

        then:"A non-unique bean exception is thrown"
        thrown(NonUniqueBeanException)

        when:"A the container element is looked up with a specific qualifier"
        context.getBean(productClass, Qualifiers.byStereotype("test.WishList"))

        then:"A non-unique bean exception is thrown"
        thrown(NonUniqueBeanException)

        when:"The container type is looked up"
        List beans = context.getBean(Argument.listOf(productClass), Qualifiers.byStereotype("test.WishList"))

        then:"The beans are correct"
        beans.size() == 2
        beans.find { it.name == 'four' }
        beans.is(context.getBean(Argument.listOf(productClass), Qualifiers.byStereotype("test.WishList")))
        beans.containsAll(bean.wishlist)

        cleanup:
        context.close()
    }


}
