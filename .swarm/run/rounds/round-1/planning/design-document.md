## Summary
`JavaWorkerClient` hard-codes `ManagedChannelBuilder.forAddress(...).usePlaintext()` and therefore can never participate in a secure host/worker gRPC channel. The host already emits `--functions-uri`, and `Application` already parses it, but the worker ignores it.

## Findings
- **Fix necessary:** Yes. Today the Java worker guarantees plaintext and blocks any host-side secure transport rollout.
- **Historical context:** Plaintext has been present since the file’s first 2018 revision, which matched the original localhost child-process trust model. In 2023 the worker added support for updated prefixed args (`functions-uri`, etc.), but the transport code stayed on legacy host/port; that looks like a missed follow-up gap, not a newly introduced behavior.
- **Regression / breaking change:** Keep the existing CLI contract and fallback behavior. Prefer `functions-uri` when present, but keep HTTP/legacy host+port working so current hosts/customers do not break. If the host explicitly sends `https`, fail closed on TLS errors rather than downgrading to plaintext.
- **Customer contract:** No new flags are needed; the worker will finally honor an existing argument. Full end-to-end mitigation still depends on the host advertising a secure URI (and a certificate the JVM can trust).
- **Testing today:** `mvn test` currently passes (63 tests, 0 failures/errors/skips). CI also runs build plus emulated/E2E matrices, but there is no direct coverage of secure gRPC channel negotiation.

## Plan
1. Surface the parsed endpoint URI through `IApplication` in a compatibility-safe way.
2. Make `JavaWorkerClient` select plaintext vs TLS from the URI scheme instead of always calling `usePlaintext()`.
3. Add explicit plaintext regression tests and TLS transport tests so the new path is exercised in CI.