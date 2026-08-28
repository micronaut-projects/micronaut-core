class SparkPlug:
    def __init__(
        self,
        name: str | None,
        type: str | None,
        company_name: str | None,
    ):
        self.name = name
        self.type = type
        self.company_name = company_name

    def get_name(self) -> str | None:
        return self.name

    def get_type(self) -> str | None:
        return self.type

    def get_company_name(self) -> str | None:
        return self.company_name

    @staticmethod
    def builder() -> "SparkPlugBuilder":
        return SparkPlugBuilder()

    def __str__(self) -> str:
        return f"{self.type or ''}({self.company_name or ''} {self.name or ''})"


class SparkPlugBuilder:
    name: str | None = "4504 PK20TT"
    type: str | None = "Platinum TT"
    company_name: str | None = "Denso"

    def withName(self, name: str | None) -> "SparkPlugBuilder":
        self.name = name
        return self

    def withType(self, type: str | None) -> "SparkPlugBuilder":
        self.type = type
        return self

    def withCompanyName(self, company_name: str | None) -> "SparkPlugBuilder":
        self.company_name = company_name
        return self

    def build(self) -> SparkPlug:
        return SparkPlug(self.name, self.type, self.company_name)


Builder = SparkPlugBuilder
SparkPlug.Builder = SparkPlugBuilder
