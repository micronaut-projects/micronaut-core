# tag::class[]
class Feature:
    def __init__(self, name: str):
        self._name = name

    def name(self) -> str:
        return self._name

    def __str__(self) -> str:  # <1>
        return self._name
# end::class[]
