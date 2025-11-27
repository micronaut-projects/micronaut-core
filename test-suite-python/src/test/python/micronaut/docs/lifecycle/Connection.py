# tag::class[]
class Connection:
    stopped : bool = False

    def stop(self): # <2>
        self.stopped = True
# end::class[]
