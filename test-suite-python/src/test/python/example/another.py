"""
Example Python application for testing Micronaut Python compilation.
"""
from jakarta.inject import Singleton

@Singleton
class AnotherService:
    def say_hello(self) -> str:
        return "Hello from Another Python service!"

