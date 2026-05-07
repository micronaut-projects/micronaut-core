class CrankShaft:
    def __init__(self, rod_length: float | None):
        self.rod_length = rod_length

    def get_rod_length(self) -> float | None:
        return self.rod_length

    @staticmethod
    def builder() -> "CrankShaftBuilder":
        return CrankShaftBuilder()


class CrankShaftBuilder:
    rod_length: float | None = None

    def withRodLength(self, rod_length: float | None) -> "CrankShaftBuilder":
        self.rod_length = rod_length
        return self

    def build(self) -> CrankShaft:
        return CrankShaft(self.rod_length)


Builder = CrankShaftBuilder
CrankShaft.Builder = CrankShaftBuilder
