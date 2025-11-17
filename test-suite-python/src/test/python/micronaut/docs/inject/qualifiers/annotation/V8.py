from jakarta.inject.Qualifier import Qualifier

def micronaut_annotation(name):
    def decorator(func):
        return func
    return decorator

@micronaut_annotation("V8")
@Qualifier
def V8(func):
    return func
