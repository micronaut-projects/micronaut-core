from micronaut.core.bind.annotation import Bindable


# tag::clazz[]
@Bindable
def Metadata():
    def decorator(func):
        return func
    return decorator
# end::clazz[]
