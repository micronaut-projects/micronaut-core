## Brief overview
- Documentation for the Micronaut modules is primarily written in Asciidoc format, focusing on user guides, API references, and integration examples. The documentation emphasizes clarity, completeness, and practical examples to help developers integrate Micronaut modules effectively. Insights from the codebase show a focus on modular documentation aligned with subprojects, including setup instructions, usage examples, and troubleshooting tips.
- All the files are within `src/main/docs/guide`. In that directory, there is a `toc.yml` file that is used to generate the table of contents and decide which `.adoc` files are to be included.

## Development workflow
- Write documentation in Asciidoc: Place source files in the appropriate `src/main/docs` directory.
- Build and assemble the documentation guide: Use `./gradlew docs` from the root directory to generate HTML documentation. Since the output of this task may be huge, ignore the output and check the last process exit code to tell if it works. Otherwise, ask the user. If it works, verify the output for formatting and content accuracy.
- Once assembled, the guide will be at `build/docs/`.
- Include examples: Create and reference code examples from the doc-examples/ directory, ensuring they are testable and up-to-date with the latest service versions.
- Test documentation: Run builds regularly and check for broken links or outdated information. Integrate doc checks into CI pipelines using Gradle tasks.
- Review and update: Conduct peer reviews for new documentation or changes, ensuring alignment with coding standards and project updates.

## Documentation best practices
- Follow Asciidoc conventions: Use consistent headings, lists, code blocks, and admonitions (e.g., NOTE, TIP, WARNING) for better readability.
- Provide comprehensive coverage: Include installation instructions, configuration details, usage examples, error handling, and performance tips for each service.
- Use practical examples: Incorporate runnable code snippets from doc-examples/ to demonstrate real-world usage, with clear explanations and expected outputs.
- Ensure accessibility: Use descriptive alt text for images, maintain logical structure, and avoid jargon without explanations.
- Version control: Document version-specific changes and maintain backward compatibility notes.
- Security and best practices: Highlight secure usage patterns, such as proper authentication and data handling.

## Project context
- Focus on Micronaut-specific integration that this project is providing, emphasizing GraalVM compatibility, annotation-driven configurations, and modular design.
- Prioritize user-centric content: Guides should facilitate quick starts, advanced customizations, and troubleshooting for developers building Micronaut applications.
- Align with coding guidelines: Documentation should complement code by explaining architectural decisions, such as the use of factories, interceptors, and annotation processors.
