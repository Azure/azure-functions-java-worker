#!/usr/bin/env python3
"""
End to end tests for the Service Bus SDK type binding in the Azure Functions Java worker.

A real Functions host in the Linux consumption image, the worker under test and the Service Bus
emulator run together. The Python azure-servicebus client sends messages and the assertions check
what happened at the broker, so a settlement only counts when the broker applied it.

The tests are opt-in because the app needs artifacts that are not released yet. To run them:

  1. Build the app: ./build-apps.ps1 -IncludeServiceBus
  2. pip install -e "azure-functions-test-kit[servicebus]"
  3. Review the Service Bus emulator and SQL Server license terms, then set
     SERVICEBUS_EMULATOR_ACCEPT_EULA=Y and FUNCTIONS_TEST_SERVICEBUS=true
  4. pytest linux-consumption-tests/test_servicebus_sdk_types.py --reruns 0
"""

import os
import re
import time
import uuid

import pytest

if os.getenv("FUNCTIONS_TEST_SERVICEBUS", "").lower() != "true":
    pytest.skip("Service Bus SDK type tests are opt-in, set FUNCTIONS_TEST_SERVICEBUS=true",
                allow_module_level=True)

from azure.servicebus import ServiceBusClient, ServiceBusMessage, ServiceBusSubQueue

from azure_functions_test_kit import LinuxConsumptionTestEnvironment


APP_NAME = "ServiceBusSdkType"

COMPLETE_QUEUE = "sbdocker-complete"
DEAD_LETTER_QUEUE = "sbdocker-deadletter"
DEFER_QUEUE = "sbdocker-defer"
BATCH_QUEUE = "sbdocker-batch"
NO_SETTLE_QUEUE = "sbdocker-nosettle"
TOPIC = "sbdocker-topic"
SUBSCRIPTION = "sbdocker-sub"

DEAD_LETTER_REASON = "sbdocker-reason"
DEAD_LETTER_DESCRIPTION = "sbdocker-description"
NO_SETTLE_MAX_DELIVERY_COUNT = 2

HOST_TIMEOUT = 360
BROKER_TIMEOUT = 150
ZERO_LOCK_TOKEN = "00000000-0000-0000-0000-000000000000"

LOG_LINE = re.compile(
    r"SBSDK function=(?P<function>\w+) messageId=(?P<message_id>\S+) body=(?P<body>\S+) "
    r"lockToken=(?P<lock_token>\S+) sequenceNumber=(?P<sequence_number>\d+) "
    r"deliveryCount=(?P<delivery_count>\d+) subject=(?P<subject>\S+) "
    r"contentType=(?P<content_type>\S+) correlationId=(?P<correlation_id>\S+) "
    r"properties=(?P<properties>\{[^}]*\})")
LISTENER_STARTED = "ServiceBus listener started"
HOST_QUIET_SECONDS = 20
LOCK_DURATION_SECONDS = 10


def _queue(name, lock_duration="PT1M", max_delivery_count=10):
    return {
        "Name": name,
        "Properties": {
            "DeadLetteringOnMessageExpiration": False,
            "DefaultMessageTimeToLive": "PT1H",
            "DuplicateDetectionHistoryTimeWindow": "PT20S",
            "ForwardDeadLetteredMessagesTo": "",
            "ForwardTo": "",
            "LockDuration": lock_duration,
            "MaxDeliveryCount": max_delivery_count,
            "RequiresDuplicateDetection": False,
            "RequiresSession": False,
        },
    }


EMULATOR_CONFIG = {
    "UserConfig": {
        "Namespaces": [{
            "Name": "sbemulatorns",
            "Queues": [
                _queue(COMPLETE_QUEUE),
                _queue(DEAD_LETTER_QUEUE),
                _queue(DEFER_QUEUE),
                _queue(BATCH_QUEUE),
                _queue(NO_SETTLE_QUEUE, lock_duration="PT10S",
                       max_delivery_count=NO_SETTLE_MAX_DELIVERY_COUNT),
            ],
            "Topics": [{
                "Name": TOPIC,
                "Properties": {
                    "DefaultMessageTimeToLive": "PT1H",
                    "DuplicateDetectionHistoryTimeWindow": "PT20S",
                    "RequiresDuplicateDetection": False,
                },
                "Subscriptions": [{
                    "Name": SUBSCRIPTION,
                    "Properties": {
                        "DeadLetteringOnMessageExpiration": False,
                        "DefaultMessageTimeToLive": "PT1H",
                        "LockDuration": "PT1M",
                        "MaxDeliveryCount": 10,
                        "ForwardDeadLetteredMessagesTo": "",
                        "ForwardTo": "",
                        "RequiresSession": False,
                    },
                }],
            }],
        }],
        "Logging": {"Type": "File"},
    }
}


@pytest.fixture(scope="module")
def test_env():
    """One host and one emulator for all scenarios, each scenario uses its own entity or message ids"""
    environment_id = f"sbdocker-{uuid.uuid4()}"
    # Optional, pins the extension bundle without rebuilding the app, e.g. "[4.29.0]"
    bundle_version = os.getenv("SERVICEBUS_TEST_BUNDLE_VERSION")
    with LinuxConsumptionTestEnvironment(
            apps_to_upload=[APP_NAME],
            environment_id=environment_id,
            servicebus_emulator_config=EMULATOR_CONFIG) as env:
        app_settings = {
            "SCM_RUN_FROM_PACKAGE": env.get_blob_sas_url(APP_NAME),
            "AzureWebJobsStorage": env.docker_storage_connection_string,
            "ServiceBusConnection": env.docker_servicebus_connection_string,
        }
        if bundle_version:
            app_settings["AzureFunctionsJobHost__extensionBundle__version"] = bundle_version
        env.functions_controller.assign_container(env=app_settings)
        assert env.functions_controller.wait_for_host_running(timeout=HOST_TIMEOUT), \
            "Functions host did not reach running state within timeout"
        assert env.functions_controller.wait_for_functions_loaded(timeout=HOST_TIMEOUT), \
            "Functions did not load within timeout"
        wait_for_host_restarts_to_finish(env)
        yield env


@pytest.fixture(scope="module")
def broker(test_env):
    with ServiceBusClient.from_connection_string(test_env.servicebus_connection_string) as client:
        yield client


def new_message(body, **kwargs):
    message_id = f"sbdocker-{uuid.uuid4()}"
    properties = {"sbdocker-text": "hello", "sbdocker-number": 42}
    message = ServiceBusMessage(
        body,
        message_id=message_id,
        subject="sbdocker-subject",
        content_type="text/plain",
        correlation_id="sbdocker-correlation",
        application_properties=properties,
        **kwargs)
    return message_id, message


def wait_until(condition, description, timeout=BROKER_TIMEOUT, interval=2):
    deadline = time.time() + timeout
    while True:
        value = condition()
        if value:
            return value
        if time.time() >= deadline:
            raise AssertionError(f"Timed out after {timeout}s waiting for {description}")
        time.sleep(interval)


def wait_for_host_restarts_to_finish(test_env):
    """The host restarts itself a few times after specialization. A message that arrives in between
    reaches a host without an initialized worker and is only redelivered, so wait for the last restart."""
    state = {"listeners": -1, "since": time.time()}

    def quiet():
        listeners = test_env.functions_controller.get_container_logs().count(LISTENER_STARTED)
        if listeners != state["listeners"]:
            state.update(listeners=listeners, since=time.time())
        return listeners > 0 and time.time() - state["since"] >= HOST_QUIET_SECONDS

    wait_until(quiet, "the host to finish restarting", timeout=HOST_TIMEOUT, interval=3)


def function_logs(test_env, function, message_ids):
    """Parsed SBSDK log lines of one function for the given message ids, one per delivery"""
    logs = test_env.functions_controller.get_container_logs()
    entries = {}
    for line in logs.splitlines():
        match = LOG_LINE.search(line)
        if match and match["function"] == function and match["message_id"] in message_ids:
            # The host writes each function log line in more than one format, and a lock token is per delivery
            entries[(match["message_id"], match["lock_token"])] = match.groupdict()
    return sorted(entries.values(), key=lambda e: int(e["delivery_count"]))


def wait_for_function_log(test_env, function, message_ids, count=1):
    message_ids = set(message_ids)

    def seen():
        entries = function_logs(test_env, function, message_ids)
        return entries if len({e["message_id"] for e in entries}) >= count else None

    return wait_until(seen, f"{count} {function} log line(s) from the host")


def assert_decoded(entry, message_id, body):
    """The fields the function read from the SDK message must equal what the client sent"""
    assert entry["message_id"] == message_id
    assert entry["body"] == body
    assert entry["subject"] == "sbdocker-subject"
    assert entry["content_type"] == "text/plain"
    assert entry["correlation_id"] == "sbdocker-correlation"
    assert entry["properties"] == "{sbdocker-number=42, sbdocker-text=hello}"
    assert entry["lock_token"] != ZERO_LOCK_TOKEN
    assert uuid.UUID(entry["lock_token"]), "lock token must be a UUID"
    assert int(entry["sequence_number"]) > 0


def wait_for_settled_log(test_env, function, message_ids):
    """The function logs this after settling and then stays in the function for a while. The host completes a
    message on its own once the function returns, so a message that is gone from the broker while the function
    is still running was settled through the worker and not by the host."""
    pattern = re.compile(rf"SBSDK-SETTLED function={function} messageIds=([\w,-]+)")
    pending = set(message_ids)

    def settled():
        for ids in pattern.findall(test_env.functions_controller.get_container_logs()):
            pending.difference_update(ids.split(","))
        return not pending

    wait_until(settled, f"the {function} function to settle its messages", interval=1)


def peek_ids(receiver):
    return {m.message_id for m in receiver.peek_messages(max_message_count=100)}


def wait_until_gone(receiver, message_ids):
    """Active, locked and deferred messages all show in peek, so a settled message must be absent"""
    message_ids = set(message_ids)
    wait_until(lambda: not (peek_ids(receiver) & message_ids),
               f"{len(message_ids)} message(s) to leave the entity")


def send(broker, queue, messages):
    with broker.get_queue_sender(queue) as sender:
        sender.send_messages(messages)


def test_complete_single_message(test_env, broker):
    """A single ServiceBusReceivedMessage is decoded from the host envelope and completed"""
    message_id, message = new_message("sbdocker-complete-body")
    send(broker, COMPLETE_QUEUE, message)

    entries = wait_for_function_log(test_env, "CompleteMessage", [message_id])
    assert_decoded(entries[0], message_id, "sbdocker-complete-body")

    wait_for_settled_log(test_env, "CompleteMessage", [message_id])
    with broker.get_queue_receiver(COMPLETE_QUEUE) as receiver:
        assert message_id not in peek_ids(receiver), \
            "the message must be completed at the broker while the function is still running"
    with broker.get_queue_receiver(COMPLETE_QUEUE, sub_queue=ServiceBusSubQueue.DEAD_LETTER) as dlq:
        assert message_id not in peek_ids(dlq), "a completed message must not be dead lettered"
    assert len(function_logs(test_env, "CompleteMessage", {message_id})) == 1, \
        "a completed message must not be delivered again"


def test_dead_letter_with_reason_and_description(test_env, broker):
    """deadLetterMessage reaches the dead letter queue with the reason and description sent"""
    message_id, message = new_message("sbdocker-deadletter-body")
    send(broker, DEAD_LETTER_QUEUE, message)

    entries = wait_for_function_log(test_env, "DeadLetterMessage", [message_id])
    assert_decoded(entries[0], message_id, "sbdocker-deadletter-body")

    with broker.get_queue_receiver(DEAD_LETTER_QUEUE, sub_queue=ServiceBusSubQueue.DEAD_LETTER) as dlq:
        def find():
            return [m for m in dlq.receive_messages(max_message_count=10, max_wait_time=5)
                    if m.message_id == message_id]

        dead_lettered = wait_until(find, "the message in the dead letter queue")[0]
        assert dead_lettered.dead_letter_reason == DEAD_LETTER_REASON
        assert dead_lettered.dead_letter_error_description == DEAD_LETTER_DESCRIPTION
        assert str(dead_lettered) == "sbdocker-deadletter-body"
        dlq.complete_message(dead_lettered)
    with broker.get_queue_receiver(DEAD_LETTER_QUEUE) as receiver:
        assert message_id not in peek_ids(receiver)


def test_defer_message(test_env, broker):
    """deferMessage keeps the message at the broker, retrievable by the sequence number the function saw"""
    message_id, message = new_message("sbdocker-defer-body")
    send(broker, DEFER_QUEUE, message)

    entries = wait_for_function_log(test_env, "DeferMessage", [message_id])
    assert_decoded(entries[0], message_id, "sbdocker-defer-body")
    logged_sequence_number = int(entries[0]["sequence_number"])

    with broker.get_queue_receiver(DEFER_QUEUE) as receiver:
        def deferred():
            return [m for m in receiver.peek_messages(max_message_count=100) if m.message_id == message_id]

        peeked = wait_until(deferred, "the deferred message to show at the broker")[0]
        assert peeked.sequence_number == logged_sequence_number, \
            "the sequence number decoded by the worker must equal the broker's"

        received = receiver.receive_deferred_messages(sequence_numbers=[logged_sequence_number])
        assert [m.message_id for m in received] == [message_id]
        assert str(received[0]) == "sbdocker-defer-body"
        receiver.complete_message(received[0])
    assert len(function_logs(test_env, "DeferMessage", {message_id})) == 1


def test_complete_batch(test_env, broker):
    """List<ServiceBusReceivedMessage> gets every message decoded and all of them completed"""
    sent = [new_message(f"sbdocker-batch-body-{i}") for i in range(5)]
    message_ids = [message_id for message_id, _ in sent]
    send(broker, BATCH_QUEUE, [message for _, message in sent])

    entries = wait_for_function_log(test_env, "CompleteBatch", message_ids, count=len(sent))
    by_id = {e["message_id"]: e for e in entries}
    for i, (message_id, _) in enumerate(sent):
        assert_decoded(by_id[message_id], message_id, f"sbdocker-batch-body-{i}")

    with broker.get_queue_receiver(BATCH_QUEUE) as receiver:
        wait_for_settled_log(test_env, "CompleteBatch", message_ids)
        assert not (peek_ids(receiver) & set(message_ids)), \
            "every message must be completed at the broker while the function is still running"
    with broker.get_queue_receiver(BATCH_QUEUE, sub_queue=ServiceBusSubQueue.DEAD_LETTER) as dlq:
        assert not (peek_ids(dlq) & set(message_ids))


def test_complete_topic_subscription_message(test_env, broker):
    """The topic trigger takes the same path as the queue trigger"""
    message_id, message = new_message("sbdocker-topic-body")
    with broker.get_topic_sender(TOPIC) as sender:
        sender.send_messages(message)

    entries = wait_for_function_log(test_env, "CompleteTopicMessage", [message_id])
    assert_decoded(entries[0], message_id, "sbdocker-topic-body")

    with broker.get_subscription_receiver(TOPIC, SUBSCRIPTION) as receiver:
        wait_for_settled_log(test_env, "CompleteTopicMessage", [message_id])
        assert message_id not in peek_ids(receiver), \
            "the message must be completed at the broker while the function is still running"


def test_unsettled_message_is_completed_by_the_host(test_env, broker):
    """The host ignores host.json autoCompleteMessages for this function and completes it after a successful run.
    A function that does not settle therefore neither redelivers nor dead letters the message."""
    message_id, message = new_message("sbdocker-nosettle-body")
    send(broker, NO_SETTLE_QUEUE, message)

    entries = wait_for_function_log(test_env, "NoSettleMessage", [message_id])
    assert_decoded(entries[0], message_id, "sbdocker-nosettle-body")

    with broker.get_queue_receiver(NO_SETTLE_QUEUE) as receiver:
        wait_until_gone(receiver, [message_id])

        # The lock is short, so a message that was not completed would be delivered again within this time
        time.sleep(LOCK_DURATION_SECONDS * NO_SETTLE_MAX_DELIVERY_COUNT + 5)
        assert message_id not in peek_ids(receiver)
    assert len(function_logs(test_env, "NoSettleMessage", {message_id})) == 1
    with broker.get_queue_receiver(NO_SETTLE_QUEUE, sub_queue=ServiceBusSubQueue.DEAD_LETTER) as dlq:
        assert message_id not in peek_ids(dlq)


if __name__ == "__main__":
    pytest.main([__file__, "-v", "-s", "--reruns", "0"])
