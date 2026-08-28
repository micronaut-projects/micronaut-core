# tag::class[]
from dataclasses import dataclass

@dataclass
class SampleEvent:
    message : str = "Something happened"
# end::class[]
