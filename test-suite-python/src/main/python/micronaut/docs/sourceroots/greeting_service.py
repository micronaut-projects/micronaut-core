from jakarta.inject import Singleton


@Singleton
class GreetingService:
    def greet(self, name: str) -> str:
        return f"Hello {name}"
