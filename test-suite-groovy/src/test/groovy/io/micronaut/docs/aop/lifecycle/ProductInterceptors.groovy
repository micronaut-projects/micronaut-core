package io.micronaut.docs.aop.lifecycle

// tag::imports[]
import io.micronaut.aop.*
import io.micronaut.context.annotation.Factory

// end::imports[]

// tag::class[]
@Factory
class ProductInterceptors {
    private final ProductService productService

    ProductInterceptors(ProductService productService) {
        this.productService = productService
    }
// end::class[]

    // tag::constructor-interceptor[]
    @InterceptorBean(ProductBean.class)
    ConstructorInterceptor<Product> aroundConstruct() { // <1>
        return  { context ->
            final Object[] parameterValues = context.parameterValues // <2>
            final Object parameterValue = parameterValues[0]
            if (parameterValue == null || parameterValues[0].toString().isEmpty()) {
                throw new IllegalArgumentException("Invalid product name")
            }
            String productName = parameterValues[0].toString().toUpperCase()
            parameterValues[0] = productName
            final Product product = context.proceed() // <3>
            productService.addProduct(product)
            return product
        }
    }
    // end::constructor-interceptor[]

    // tag::method-interceptor[]
    @InterceptorBean(ProductBean.class) // <1>
    MethodInterceptor<Product, Object> aroundInvoke() {
        return { context ->
            final Product product = context.getTarget()
            switch (context.kind) {
                case InterceptorKind.POST_CONSTRUCT: // <2>
                    product.setActive(true)
                    return context.proceed()
                case InterceptorKind.PRE_DESTROY: // <3>
                    productService.removeProduct(product)
                    return context.proceed()
                default:
                    return context.proceed()
            }
        }
    }
    // end::method-interceptor[]

// tag::class[]
}
// end::class[]

