from dataclasses import dataclass


# tag::class[]
@dataclass
class V8Engine:
    rod_length: float  # <1>
    cylinders: int = 8

    def start(self) -> str:
        return f"Starting V{self.cylinders} [rodLength={self.rod_length}]"
# end::class[]
