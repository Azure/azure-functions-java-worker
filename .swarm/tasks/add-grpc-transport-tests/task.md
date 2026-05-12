# Add plaintext and TLS transport coverage

- Task ID: `add-grpc-transport-tests`
- Round: 1
- Branch: worker/task-3
- Dependencies: select-grpc-transport-from-uri

## Description

Add focused tests under `src/test/java` (and `src/test/resources` if needed) that cover: (1) the existing plaintext path still working with the current test host, (2) a TLS-enabled gRPC host that succeeds when the worker is given a trusted secure `functions-uri`, and (3) a negative secure-path case that proves `https` is not silently downgraded to plaintext. Keep the existing functional tests green and validate the final change set with `mvn test`.