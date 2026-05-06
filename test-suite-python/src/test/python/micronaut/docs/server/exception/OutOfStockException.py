# tag::clazz[]
import java

RuntimeException = java.type("java.lang.RuntimeException")


class OutOfStockException(RuntimeException):
    pass
# end::clazz[]
