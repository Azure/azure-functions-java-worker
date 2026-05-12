# Expose functions-uri to worker startup code

- Task ID: `expose-functions-uri`
- Round: 1
- Branch: worker/task-1
- Dependencies: (none)

## Description

Update `src/main/java/com/microsoft/azure/functions/worker/IApplication.java` and `Application.java` so the parsed `--functions-uri` value is available through a compatibility-safe accessor. Use a Java 8 default interface method (or an equivalent non-breaking pattern) so existing implementers do not need source changes, while preserving the current precedence of prefixed args over legacy host/port/request/message-size arguments and keeping host/port extraction intact.