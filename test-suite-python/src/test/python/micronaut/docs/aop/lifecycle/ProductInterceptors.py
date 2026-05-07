# tag::imports[]
from typing import Annotated

from jakarta.inject import Inject
from micronaut.aop import InterceptorBean, MethodInvocationContext
# end::imports[]

import java
from .ProductBean import ProductBean
from .ProductService import ProductService

ConstructorInterceptor = java.type("io.micronaut.aop.ConstructorInterceptor")
MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")
InterceptorKind = java.type("io.micronaut.aop.InterceptorKind")


# tag::class[]
class ProductInterceptors:
    product_service: Annotated[ProductService, Inject] = None
# end::class[]


# tag::constructor-interceptor[]
@InterceptorBean(ProductBean.__qualname__)
class ProductConstructorInterceptor(ConstructorInterceptor):  # <1>
    product_service: Annotated[ProductService, Inject] = None

    def intercept(self, context):
        parameter_values = context.getParameterValues()  # <2>
        parameter_value = parameter_values[0]
        if parameter_value is None or str(parameter_value) == "":
            raise ValueError("Invalid product name")
        product_name = str(parameter_value).upper()
        parameter_values[0] = product_name
        product = context.proceed()  # <3>
        self.product_service.add_product(product)
        return product
# end::constructor-interceptor[]


# tag::method-interceptor[]
@InterceptorBean(ProductBean.__qualname__)  # <1>
class ProductMethodInterceptor(MethodInterceptor):
    product_service: Annotated[ProductService, Inject] = None

    def intercept(self, context: MethodInvocationContext):
        product = context.getTarget()
        kind = context.getKind()
        if kind == InterceptorKind.POST_CONSTRUCT:  # <2>
            product.set_active(True)
            return context.proceed()
        if kind == InterceptorKind.PRE_DESTROY:  # <3>
            self.product_service.remove_product(product)
            return context.proceed()
        return context.proceed()
# end::method-interceptor[]
