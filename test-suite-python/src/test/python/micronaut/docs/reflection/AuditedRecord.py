# tag::imports[]
from dataclasses import dataclass
from typing import Annotated

from micronaut.core.annotation import AllowsReflection, Introspected
from docs.reflection import Audited
# end::imports[]


# tag::class[]
@AllowsReflection  # <1>
@Audited("records")  # <2>
@Introspected
@dataclass
class AuditedRecord:
    id: Annotated[int | None, Audited("record_id")] = None  # <3>
    title: str = ""
# end::class[]
