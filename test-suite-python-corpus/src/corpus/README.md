# The corpus

Pinned copies of the Python sources of two real Pyronaut applications, compiled by
`StaticCompilationCorpusTest` with the type checker at `error` and static compilation in mode
`all`. The test asserts that both build, that the checker reports nothing, and that every
decision of the static compiler (each function with its outcome) is the one recorded in
`<name>.decisions.txt`: a regression in the compile rate of either project fails the build, and
so does a change that loses some compiled bodies while gaining others.

| Corpus | Origin | Commit | License |
|---|---|---|---|
| `fullstack` | https://github.com/micronaut-projects/pyronaut-full-stack-template (`src`) | `8850c126e7118c732a2ece758aba7690a29a68e3` | Apache License 2.0, as declared in the project's README at that commit (the repository has no LICENSE file) |
| `petclinic` | https://github.com/micronaut-projects/pyronaut-petclinic (`src`) | `9cdff239414604f89bcaf68222f2e9536891a756` | None declared at that commit: the repository has no LICENSE file and its README and `pyproject.toml` state no license |

Both projects belong to the `micronaut-projects` organization, as this repository does. The
copies are the projects' code as written, not adapted to the compiler: what they leave in Python
(unhinted helper parameters, `**kwargs`, lambdas, standard library code) is the record of what the
compiler does not take yet.

When the compiler changes what it compiles, the test fails with the decisions no longer made and
those newly made; when the change is intended, copy `build/corpus/<name>/decisions.txt` over
`<name>.decisions.txt`. Each run cleans `build/corpus/<name>` first, then writes the classes, the
static compilation report and the decisions of the compilation there.

To compile a checkout of a project instead of its copy, run the test with
`-Dcorpus.fullstack.dir=<path to src>` or `-Dcorpus.petclinic.dir=<path to src>`: the path and
the sources it holds are inputs of the task, and the decisions are written to
`build/corpus/<name>/decisions.txt` without being compared.
