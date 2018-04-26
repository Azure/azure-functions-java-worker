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
 * <p>The following example shows a cosmos db trigger which logs the count of the returned items:</p>
 *
 * <pre>{@literal @}FunctionName("cdbprocessor")
 * public void cosmosDbProcessor(
 *    {@literal @}CosmosDBTrigger(name = "items",
 *                      databaseName = "mydbname",
 *                      collectionName = "mycollname",
 *                      leaseCollectionName = "",
 *                      connectionStringSetting = "myconnvarname") MyDataItem[] items,
 *     final ExecutionContext context
 * ) {
 *     context.getLogger().info(items.length);
 * }</pre>
 *
 * @since 1.0.0
 */
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
