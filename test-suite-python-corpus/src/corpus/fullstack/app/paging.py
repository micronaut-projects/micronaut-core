"""Pagination helpers.

``Pageable.from(page, size)`` is the natural Micronaut Data call, but ``from`` is
a Python keyword, so it cannot be written as an attribute access. Pyronaut
exposes the method under its real Java name, which means it has to be reached
with ``getattr``. Doing that once here keeps the workaround out of the
controllers.

See PLAN.md section 12 — this is tracked upstream.
"""

from micronaut.data.model import Pageable

_pageable_from = getattr(Pageable, "from")

DEFAULT_PAGE_SIZE = 100
MAX_PAGE_SIZE = 500


def page_request(page: int = 0, size: int = DEFAULT_PAGE_SIZE) -> Pageable:
    """Build a Pageable, clamping the inputs so a client cannot ask for the world."""
    page = max(0, int(page))
    size = min(max(1, int(size)), MAX_PAGE_SIZE)
    return _pageable_from(page, size)
