# A main module that fails: the context factory must unregister and close the context it built.
raise RuntimeError("failing_main.py refuses to load")
