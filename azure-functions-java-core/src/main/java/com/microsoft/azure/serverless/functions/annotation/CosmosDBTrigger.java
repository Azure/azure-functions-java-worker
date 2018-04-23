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

/**
 *
 * @since 1.0.0
 */
@Binding(BindingType.TRIGGER)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface CosmosDBTrigger {
    String name();

    String dataType() default "";

    String databaseName();

    String collectionName();

    String leaseCollectionName();

    boolean createLeaseCollectionIfNotExists() default false;

    String connectionStringSetting();
}
