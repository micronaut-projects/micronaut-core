from micronaut.context.annotation import Executable
from jakarta.inject import Singleton

# tag::example[]
from .NotNull import NotNull

@Singleton
class NotNullExample:
    @NotNull
    @Executable
    def doWork(self, taskName : str):
        print(f"Doing job: {taskName}")
# end::example[]
