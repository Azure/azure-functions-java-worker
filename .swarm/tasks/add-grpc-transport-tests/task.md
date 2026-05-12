# Add plaintext and TLS transport coverage

- Task ID: `add-grpc-transport-tests`
- Round: 1
- Branch: worker/task-3
- Dependencies: select-grpc-transport-from-uri

## Description

Add focused tests under `src/test/java` (and `src/test/resources` if needed) that cover: (1) the existing plaintext path still working with the current test host, (2) successful connection to a TLS-enabled gRPC test host when the worker is given a trusted `https` `functions-uri`, and (3) a negative `https` case proving TLS errors are not silently downgraded to plaintext. Extend or split the current test host utility as needed, then rerun `mvn test` to keep the full suite green.