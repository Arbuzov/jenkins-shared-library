# Contributing Guide

## Scope
This repository is a Jenkins Shared Library.
Contributions primarily target:
- `vars/` for pipeline steps (global vars)
- `src/` for reusable Groovy classes
- `resources/` for templates and static assets
- `src/test/groovy/` for Jenkins Pipeline Unit tests

## Project Structure Conventions
- Add reusable pipeline entry points in `vars/<functionName>.groovy`.
- Use `src/` for logic that should not depend on Jenkins step context.
- Keep function documentation close to code:
  - `vars/<functionName>.txt` for usage and parameters
  - `.md` is used for selected functions with longer operational notes.
- Keep template-driven content under `resources/` and load via `libraryResource(...)`.

## Coding Patterns for `vars/` Functions
- Use a single entry point:
  - `def call(Map arg = [:])` for named arguments.
- Keep `reportUsage(...)` at the beginning of `call(...)` when the function is part of shared step telemetry.
- Prefer explicit defaults from `arg` values.
- For public step parameters, use `UPPER_SNAKE_CASE` keys in the map (for example `CREDENTIALS_ID`, `DEBUG`, `PAGE_ID`).
- Validate inputs early and fail fast using `error(...)` with actionable messages.
- Return structured `Map` data when the step produces multiple outputs.
- Gate noisy logging behind a `DEBUG` flag and keep default execution concise.

## Error Handling Rules
- Use `error(...)` for pipeline-fatal conditions.
- Include enough context in error messages to identify the failing resource (URL, page name, file path, parameter).
- Use optional flags like `ALLOW_*` only when a non-fatal workflow is explicitly needed.
- If exceptions are caught for context enrichment, rethrow through `error(...)` to keep pipeline behavior explicit.

## Jenkins and Pipeline Usage
- Prefer declarative pipeline style when implementing end-to-end build helpers.
- Use `when` conditions for branch/CR-specific behavior instead of inline branch checks.
- Use `stash/unstash` for cross-stage artifacts.
- Add `post { cleanup { cleanWs() } }` where workspace hygiene is required.
- Use Jenkins credentials APIs (`withCredentials`, `credentialsId`) and never hardcode secrets.

## Documentation Requirements
For new or changed functions in `vars/`:
- Update or add `vars/<functionName>.txt` with:
  - purpose
  - usage examples
  - parameter table with defaults
  - return format
  - failure behavior
- Keep examples executable and aligned with actual argument names.

## Testing Requirements
- Add or update tests in `src/test/groovy/vars/*Test.groovy` for behavior changes.
- Use Jenkins Pipeline Unit (`BasePipelineTest`) to test step scripts.
- Register all Jenkins DSL methods used by the script via `helper.registerAllowedMethod(...)`.
- Test both positive and negative paths:
  - default behavior
  - parameter validation errors
  - edge cases (empty inputs, missing resources, optional flags)
- Keep tests deterministic (mock `httpRequest`, `readJSON`, `echo`, `error`, etc.).

## Local Validation
Use Maven for local checks:

```bash
mvn clean test site
```

At minimum, run:

```bash
mvn test
```

## Style and Maintenance
- Keep changes minimal and scoped.
- Follow existing Groovy style in touched files (indentation, naming, map literals).
- Prefer small, composable helper methods for parsing/transformation logic.
- Avoid introducing new dependencies unless strictly necessary.
- Preserve backward compatibility for function signatures used by pipelines.

## Pull Request Checklist
- Function behavior is documented (`vars/*.txt` or relevant `.md`).
- Unit tests are added/updated and pass locally.
- No secrets or environment-specific credentials are committed.
- Error messages are explicit and operator-friendly.
- Changes are compatible with existing Jenkins shared library usage.
