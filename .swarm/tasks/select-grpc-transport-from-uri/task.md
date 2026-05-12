# Choose gRPC transport from endpoint scheme

- Task ID: `select-grpc-transport-from-uri`
- Round: 1
- Branch: worker/task-2
- Dependencies: expose-functions-uri

## Description

Refactor `src/main/java/com/microsoft/azure/functions/worker/JavaWorkerClient.java` so channel construction prefers the full endpoint URI when present instead of hard-coding `.usePlaintext()`. Use plaintext only for `http` or legacy host/port startup, use transport security for `https`, preserve the current message-size behavior, and add clear error/log handling for unsupported or misconfigured secure endpoints. Do not silently downgrade a secure URI back to plaintext.