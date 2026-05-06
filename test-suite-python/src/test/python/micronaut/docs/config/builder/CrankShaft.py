class CrankShaft:
    def __init__(self, rod_length: float | None):
        self.rod_length = rod_length

    def get_rod_length(self) -> float | None:
        return self.rod_length

    @staticmethod
    def builder():
        return CrankShaft.Builder()

    class Builder:
        rod_length: float | None = None

        def withRodLength(self, rod_length: float | None):
            self.rod_length = rod_length
            return self

        def build(self):
            return CrankShaft(self.rod_length)


Builder = CrankShaft.Builder
