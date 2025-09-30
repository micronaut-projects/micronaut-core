package io.micronaut.docs.aop.lifecycle

// tag::imports[]
import io.micronaut.aop.*
import io.micronaut.context.annotation.Factory
// end::imports[]

// tag::class[]
@Factory
class ProductInterceptors(private val productService: ProductService) {
// end::class[]

    // tag::constructor-interceptor[]
    @InterceptorBean(ProductBean::class)
    fun aroundConstruct(): ConstructorInterceptor<Product> { // <1>
        return ConstructorInterceptor { context: ConstructorInvocationContext<Product> ->
            val parameterValues = context.parameterValues // <2>
            val parameterValue = parameterValues[0]
            require(!(parameterValue == null || parameterValues[0].toString().isEmpty())) { "Invalid product name" }
            val productName = parameterValues[0].toString().uppercase()
            parameterValues[0] = productName
            val product = context.proceed() // <3>
            productService.addProduct(product)
            product
        }
    }
    // end::constructor-interceptor[]

    // tag::method-interceptor[]
    @InterceptorBean(ProductBean::class)
    fun  aroundInvoke(): MethodInterceptor<Product, Any> { // <1>
        return MethodInterceptor { context: MethodInvocationContext<Product, Any> ->
            val product = context.target
            return@MethodInterceptor when (context.kind) {
                InterceptorKind.POST_CONSTRUCT -> { // <2>
                    product.active = true
                    context.proceed()
                }
                InterceptorKind.PRE_DESTROY -> { // <3>
                    productService.removeProduct(product)
                    context.proceed()
                }
                else -> context.proceed()
            }
        }
    }
    // end::method-interceptor[]

// tag::class[]
}
// end::class[]
