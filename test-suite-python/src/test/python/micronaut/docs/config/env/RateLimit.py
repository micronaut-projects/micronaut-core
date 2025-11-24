from dataclasses import dataclass
from java.time import Duration

@dataclass
class RateLimit:
    period : Duration
    limit : int
