_EVENTS: list[str] = []


def add(event: str):
    _EVENTS.append(event)


def events() -> list[str]:
    return list(_EVENTS)


def clear():
    _EVENTS.clear()
