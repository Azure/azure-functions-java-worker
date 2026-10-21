# Service Bus SDK bindings

This development sample binds native `ServiceBusReceivedMessage` objects without
opening an application-owned receiver. The single-message function uses
`@BindingName("message") ServiceBusMessageActions` for synchronous manual completion.
The batch function binds `ServiceBusReceivedMessage[]` and uses automatic completion.
`List<ServiceBusReceivedMessage>` is also supported by the matching SDK types analyzer.

## Required coordinated builds

This is not supported by the currently published Java packages alone. Before building,
install the coordinated branches of these repositories into the same local Maven repository.

* [azure-functions-java-additions](https://github.com/Azure/azure-functions-java-additions)
  supplies SPI `1.2.0-SNAPSHOT`, SDK types `1.1.0-SNAPSHOT` and the optional
  `azure-functions-java-servicebus:1.0.0-SNAPSHOT`.
* [azure-functions-java-library](https://github.com/Azure/azure-functions-java-library)
  supplies `3.3.1-SNAPSHOT`, including `autoCompleteMessages` on Service Bus triggers.
  Do not remove `autoCompleteMessages = false` to make an older library compile.
* [azure-maven-plugins](https://github.com/microsoft/azure-maven-plugins)
  supplies the coordinated packaging changes. The development branch currently uses
  plugin version `1.42.0`. A previously published artifact with that version does not
  imply these changes are available. Install that branch locally or override the
  version property with the eventual released version.
* The Service Bus module requires the coordinated
  [Azure SDK for Java](https://github.com/Azure/azure-sdk-for-java) change that creates
  a native received message from host AMQP data. It must not use private constructors
  or reflection as a substitute.
* Run the Functions host with this worker build, not an unmodified worker bundled with
  Core Tools. This worker does not make the feature available in deployed Functions
  until the coordinated worker and package versions are released.

The host must provide deferred SDK binding payloads and the Service Bus settlement
endpoint. Service Bus extension `5.13.2` is the earliest verified baseline for the
settlement wire contract used here. The extension bundle range in `host.json` does not
pin its exact extension version. Verify the resolved extension meets that baseline.
No minimum Functions host version or end-to-end host compatibility is claimed here.

The coordinated source review used Functions host
[`62473a2`](https://github.com/Azure/azure-functions-host/tree/62473a2c4f9218551b7b9409bcc84928cd6f9140).
That host accepts per-binding deferred SDK metadata for Java without a new worker
initialization capability. Its
[`WorkerFunctionDescriptorProvider`](https://github.com/Azure/azure-functions-host/blob/62473a2c4f9218551b7b9409bcc84928cd6f9140/src/WebJobs.Script.Grpc/WorkerFunctionDescriptorProvider.cs#L63-L78)
can disable trigger deferred binding when another input or output binding contains
a `{...}` expression. Avoid such bindings in this sample. SDK hydration requires
actual `model_binding_data` or `collection_model_binding_data` input and must fail
clearly if the host sends a plain body instead. A source review is not a live host test.

## Run locally

From the repository root, after installing the prerequisites:

```powershell
mvn -s settings.xml -f samples\servicebus-sdk-binding\pom.xml clean package
Copy-Item samples\servicebus-sdk-binding\local.settings.json.example samples\servicebus-sdk-binding\local.settings.json
```

Set `ServiceBusConnection` in the local file. Create queues named `sdk-messages`
and `sdk-batches`. Run Azurite for the sample storage setting, or replace it with
your own local storage configuration. Configure Core Tools to use the newly built
worker, then run the sample from its directory:

```powershell
mvn azure-functions:run
```

After packaging, inspect each generated `function.json` under
`target\azure-functions\servicebus-sdk-binding`. The manual function must have one
Service Bus trigger named `message`, `supportsDeferredBinding: true` and
`autoCompleteMessages: false`. It must not have an extra input binding for `actions`.
The batch trigger must have cardinality `many`.

From the repository root, run the optional packaged-sample integration test:

```powershell
mvn -s settings.xml "-Dtest=ServiceBusSampleIntegrationTest" "-Dservicebus.sample.root=$PWD\samples\servicebus-sdk-binding\target\azure-functions\servicebus-sdk-binding" test
```

This exercises the real function classloader, shared worker SPI, optional provider
discovery, native message and batch hydration, and companion actions construction
using a fixed binary host envelope. It does not invoke the sample functions or
make a settlement request. The test is skipped unless the sample path property is set.

Send messages to each queue and check that message IDs and bodies are logged. Verify
the manually completed message is removed. For a failure exercise, throw before
`completeMessage` and verify normal Service Bus retry and delivery-count behavior.
Do not complete, renew, abandon, defer or dead-letter a message after the invocation
returns. The host owns its lock and invocation payload cache, and actions become
inactive when the invocation ends.

Manual actions use the host endpoint already supplied to the worker through
`--functions-uri` or `--host` and `--port`. Do not configure a second receiver or a
customer-defined settlement endpoint. A missing endpoint must fail explicitly for
manual actions, but is not required for message-only hydration.

Live Service Bus delivery and settlement require a configured host and namespace.
Unit tests alone do not establish these end-to-end behaviors.
