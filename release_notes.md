# Unreleased

Native HTTP streaming is available as an opt-in preview with
`FUNCTIONS_JAVA_ENABLE_HTTP_STREAMING=true`. It supports live request streams,
streaming response sources and output callbacks through the host's `HttpUri`
transport. Existing applications retain buffered gRPC by default.

The preview requires the companion core library streaming API. The current
`1.4.0-SNAPSHOT` dependency must be replaced with a released artifact before
production delivery. See the README for usage, stream ownership and transport
compatibility limits.
