package io.micronaut.inject.factory.collection

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext

class FactoryCollectionSpec extends AbstractTypeElementSpec {

    void 'test produce multiple beans from a factory'() {
        given:
        ApplicationContext context = buildContext('''
package test;

import io.micronaut.context.annotation.*;
import java.util.*;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import jakarta.inject.*;

@Singleton
class Catalogue {
    @All
    @Inject
    List<Product> all;
    
    @WishList
    @Inject
    List<Product> wishlist;
    
    @Any
    @Inject
    Product product;
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
    String name;
    Product(String name) {
        this.name = name;
    }
}
''')
        when:
        def bean = getBean(context, 'test.Catalogue')

        then:
        bean.wishlist.size() == 2
        bean.all.size() == 3

        cleanup:
        context.close()
    }
}
