/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.serverless.functions.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface ServiceBusTrigger {
    enum AccessRights {
        MANAGE,
        SEND,
        LISTEN,
        MANAGE_NOTIFICATION_HUB
    }

    String name();

    String queueName() default "";

    String topicName() default "";

    String subscriptionName() default "";

    String connection();

    AccessRights access() default AccessRights.MANAGE;
}
