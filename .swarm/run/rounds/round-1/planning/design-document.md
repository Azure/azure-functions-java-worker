## Summary
`JavaWorkerClient` always builds `ManagedChannelBuilder.forAddress(...).usePlaintext()`, so the Java worker ignores the already-parsed `--functions-uri` and can never join a secure host/worker gRPC channel.

## Findings
- **Fix necessary:** Yes. Today the worker guarantees plaintext and blocks any host-side TLS rollout, leaving any non-local transport exposed to MITM tampering.
- **Historical context:** Plaintext dates back to the early 2018 same-machine child-process design. In 2023 the worker added prefixed startup args, including `functions-uri`, plus fallback to legacy args, but `JavaWorkerClient` was not updated; this looks like an old assumption that became a gap once URI-based startup existed.
- **TLS behavior clarification:** The fix should honor TLS, not ignore it. When `functions-uri` uses `https`, the worker should build a TLS gRPC channel and fail if handshake, certificate, or hostname validation fails. Only `http` URIs, or legacy startup that supplies just host+port, should continue to use plaintext.
- **Regression / breaking change:** No new CLI or protocol contract. Existing `http` and legacy host+port launches keep their current behavior. The only observable behavior change is that a previously ignored or misconfigured `https` endpoint will stop connecting insecurely and instead fail closed.
- **Customer contract:** No new flags are required. Trust still comes from the JVM trust configuration already available via existing Java options, so hosts using private CAs do not need a new worker-specific switch.
- **Testing today:** `mvn test` currently passes (63 tests, 0 failures/errors/skips). Repo CI also runs build plus emulated, docker, and end-to-end matrices, but there is no direct secure gRPC transport coverage.

## Plan
1. Expose the parsed `functions-uri` through `IApplication` in a compatibility-safe way.
2. Make `JavaWorkerClient` choose transport from the URI scheme: `https` => TLS with no plaintext downgrade, `http` or legacy host+port => plaintext.
3. Add focused plaintext/TLS transport tests and rerun `mvn test`.