# tag::class[]
from micronaut.context.annotation import Factory, Prototype
from micronaut.docs.aop.advice.MyBean import MyBean
from micronaut.docs.aop.advice.Timed import Timed


@Factory
class MyFactory:
    @Prototype
    @Timed
    def my_bean(self) -> MyBean:
        return MyBean()
# end::class[]
