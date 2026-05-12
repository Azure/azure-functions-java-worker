# Expose functions-uri to worker startup code

- Task ID: `expose-functions-uri`
- Round: 1
- Branch: worker/task-1
- Dependencies: (none)

## Description

Update `src/main/java/com/microsoft/azure/functions/worker/IApplication.java` and `Application.java` so the parsed `--functions-uri` value is available through a compatibility-safe accessor. Use a Java 8 default interface method (or equivalent non-breaking pattern) so existing implementers such as `FunctionsTestHost` and any downstream consumers do not need source changes, while preserving the current fallback from prefixed args to legacy host/port/request identifiers.