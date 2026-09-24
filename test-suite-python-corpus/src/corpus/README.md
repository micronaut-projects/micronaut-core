# The corpus

Pinned copies of the Python sources of two real Pyronaut applications, compiled by
`StaticCompilationCorpusTest` with the type checker at `error` and static compilation in mode
`all`. The test asserts that both build, that the checker reports nothing, and that the number of
compiled bodies does not fall: a regression in the compile rate of either project fails the build.

| Corpus | Origin | Commit |
|---|---|---|
| `fullstack` | https://github.com/micronaut-projects/pyronaut-full-stack-template (`src`) | 8850c12 |
| `petclinic` | https://github.com/micronaut-projects/pyronaut-petclinic (`src`) | 9cdff23 |

The copies are the projects' code as written, not adapted to the compiler: what they leave in
Python (unhinted helper parameters, `**kwargs`, lambdas, standard library code) is the record of
what the compiler does not take yet. To compile a checkout of a project instead of its copy, run
the test with `-Dcorpus.fullstack.dir=<path to src>` or `-Dcorpus.petclinic.dir=<path to src>`.
