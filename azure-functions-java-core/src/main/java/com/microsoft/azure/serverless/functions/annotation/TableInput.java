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
 * <p>The following example shows an HTTP trigger which returned the total count of the items in a table storage:</p>
 *
 * <pre>{@literal @}FunctionName("getallcount")
 * public int run(
 *    {@literal @}HttpTrigger(name = "req",
 *                  methods = {"get"},
 *                  authLevel = AuthorizationLevel.ANONYMOUS) Object dummyShouldNotBeUsed,
 *    {@literal @}TableInput(name = "items",
 *                 tableName = "mytablename",
 *                 partitionKey = "myparkey",
 *                 connection = "myconnvarname") MyItem[] items
 * ) {
 *     return items.length;
 * }</pre>
 *
 * @see com.microsoft.azure.serverless.functions.annotation.HttpTrigger
 * @since 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface TableInput {
    String name();

    String dataType() default "";

    String tableName();

    String partitionKey() default "";

    String rowKey() default "";

    String filter() default "";

    String take() default "";

    String connection() default "";
}
