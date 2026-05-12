# Choose gRPC transport from endpoint scheme

- Task ID: `select-grpc-transport-from-uri`
- Round: 1
- Branch: worker/task-2
- Dependencies: expose-functions-uri

## Description

Refactor `src/main/java/com/microsoft/azure/functions/worker/JavaWorkerClient.java` so channel construction reads the endpoint scheme from `functions-uri` when present instead of hard-coding plaintext. If the URI is `https`, build the channel with transport security and let TLS or certificate failures surface; do not retry or downgrade to plaintext. If the URI is `http`, or no URI is available and startup fell back to legacy host+port args, keep the current plaintext behavior. Preserve the existing message-size behavior and fail fast with a clear error for unsupported schemes.