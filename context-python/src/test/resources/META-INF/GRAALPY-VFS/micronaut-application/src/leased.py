# A coroutine that blocks on a Java gate: the pooled context that runs it stays leased meanwhile.
async def hold(gate):
    gate()
    return "done"
