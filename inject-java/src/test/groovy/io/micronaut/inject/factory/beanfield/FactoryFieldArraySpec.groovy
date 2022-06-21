package io.micronaut.inject.factory.beanfield

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.NonUniqueBeanException
import io.micronaut.core.type.Argument
import io.micronaut.inject.qualifiers.Qualifiers

class FactoryFieldArraySpec extends AbstractTypeElementSpec {

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

    @All
    @Inject
    Product[] allAsArray;

    @WishList
    @Inject
    Set<Product> wishlist;

    @WishList
    @Inject
    Product[] wishlistAsArray;

    @Any
    @Inject
    BeanProvider<Product> provider;
}

@Factory
class Shop {

   @Bean @All
   public Product[] allProducts = new Product[] {
        new Product("one"),
        new Product("two"),
        new Product("three")
   };

   @Bean @WishList
   public Product[] wishList = new Product[] {
        new Product("four"),
        new Product("five")
    };
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
        def productArrayClass = Class.forName('[Ltest.Product;', true, context.classLoader)
        def shopClass = context.classLoader.loadClass('test.Shop')
        def bean = getBean(context, 'test.Catalogue')

        then:
        context.getBeanDefinitions(productClass).size() == 2
        context.getBeanDefinitions(shopClass).size() == 1
        context.getBeanDefinitions(shopClass, Qualifiers.byStereotype("test.WishList")).size() == 0
        bean.wishlist.size() == 2
        bean.wishlist.find { it.name == 'four' }
        bean.wishlistAsArray.length == 2
        bean.all.size() == 3
        bean.allAsArray.length == 3
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
        Object[] beans = context.getBean(Argument.of(productArrayClass), Qualifiers.byStereotype("test.WishList"))

        then:"The beans are correct"
        beans.length == 2
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

    @All
    @Inject
    Product[] allAsArray;

    @WishList
    @Inject
    Set<Product> wishlist;

    @WishList
    @Inject
    Product[] wishlistAsArray;

    @Any
    @Inject
    BeanProvider<Product> provider;
}

@Factory
class Shop {

   @Bean @Singleton @All
   public Product[] allProducts = new Product[] {
        new Product("one"),
        new Product("two"),
        new Product("three")
    };

   @Bean @Singleton @WishList
   public Product[] wishList = new Product[] {
        new Product("four"),
        new Product("five")
    };
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
        def productArrayClass = Class.forName('[Ltest.Product;', true, context.classLoader)
        def bean = getBean(context, 'test.Catalogue')

        then:
        bean.wishlist.size() == 2
        bean.wishlist.find { it.name == 'four' }
        bean.wishlistAsArray.length == 2
        bean.all.size() == 3
        bean.allAsArray.length == 3
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
        Object[] beans = context.getBean(Argument.of(productArrayClass), Qualifiers.byStereotype("test.WishList"))

        then:"The beans are correct"
        beans.length == 2
        beans.find { it.name == 'four' }
        beans.is(context.getBean(Argument.of(productArrayClass), Qualifiers.byStereotype("test.WishList")))
        Arrays.asList(beans).containsAll(bean.wishlist)

        cleanup:
        context.close()
    }


}
