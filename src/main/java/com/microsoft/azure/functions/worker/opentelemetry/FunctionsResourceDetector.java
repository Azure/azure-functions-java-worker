package com.microsoft.azure.functions.worker.opentelemetry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.resources.Resource;

/**
 * Builds a {@link Resource} that describes the current Azure Functions
 * instance (or a local “func start” host) using environment variables.
 *
 * <p>All attribute keys follow the OpenTelemetry semantic-conventions
 * (<code>cloud.*</code>, <code>service.*</code>, <code>deployment.*</code>).
 */
public final class FunctionsResourceDetector {

    /* ────────────────────────────────────────────────────────────────── */
    /*  Attribute keys (OTel semantic-conventions)                       */
    /* ────────────────────────────────────────────────────────────────── */
    private static final String ATTR_CLOUD_PROVIDER         = "cloud.provider";
    private static final String ATTR_CLOUD_PLATFORM         = "cloud.platform";
    private static final String ATTR_CLOUD_REGION           = "cloud.region";
    private static final String ATTR_CLOUD_RESOURCE_ID      = "cloud.resource.id";
    private static final String ATTR_DEPLOYMENT_ENVIRONMENT = "deployment.environment";
    private static final String ATTR_SERVICE_NAME           = "service.name";

    /* ────────────────────────────────────────────────────────────────── */
    /*  Env-vars published by the Functions host                         */
    /* ────────────────────────────────────────────────────────────────── */
    private static final String ENV_SITE_NAME       = "WEBSITE_SITE_NAME";
    private static final String ENV_REGION_NAME     = "REGION_NAME";
    private static final String ENV_RESOURCE_GROUP  = "WEBSITE_RESOURCE_GROUP";
    private static final String ENV_OWNER_NAME      = "WEBSITE_OWNER_NAME";
    private static final String ENV_SLOT_NAME       = "WEBSITE_SLOT_NAME";

    private FunctionsResourceDetector() { /* utility – do not instantiate */ }

    /* ===================================================================
       Public API
       =================================================================== */

    /** Returns an OTel {@link Resource} describing the running Function-App. */
    public static Resource getResource() {
        AttributesBuilder builder = Attributes.builder();

        populateServiceAttributes(builder);
        populateRegionAttribute(builder);
        populateResourceId(builder);
        populateDeploymentEnvironment(builder);

        return Resource.create(builder.build());
    }

    /* ===================================================================
       Helpers
       =================================================================== */

    private static void populateServiceAttributes(AttributesBuilder builder) {
        String siteName = env(ENV_SITE_NAME);

        if (!siteName.isEmpty()) {
            builder.put(ATTR_SERVICE_NAME, siteName);
            builder.put(ATTR_CLOUD_PROVIDER, "azure");
            builder.put(ATTR_CLOUD_PLATFORM, "azure_functions");
        } else {
            // local “func start” run
            builder.put(ATTR_SERVICE_NAME, "java-function-app");
        }
    }

    private static void populateRegionAttribute(AttributesBuilder builder) {
        String region = env(ENV_REGION_NAME);
        if (!region.isEmpty()) {
            builder.put(ATTR_CLOUD_REGION, region);
        }
    }

    /**
     * WEBSITE_OWNER_NAME looks like {@code <subscriptionId>+<something>}.
     * Combined with WEBSITE_RESOURCE_GROUP and WEBSITE_SITE_NAME we can
     * build an ARM-style resourceId.
     */
    private static void populateResourceId(AttributesBuilder builder) {
        String ownerName     = env(ENV_OWNER_NAME);
        String resourceGroup = env(ENV_RESOURCE_GROUP);
        String siteName      = env(ENV_SITE_NAME);

        if (ownerName.isEmpty() || resourceGroup.isEmpty() || siteName.isEmpty()) {
            return; // not on a real Azure site
        }

        int plus = ownerName.indexOf('+');
        if (plus <= 0) {
            return;
        }
        String subscriptionId = ownerName.substring(0, plus);

        String resourceId = String.format(
                "/subscriptions/%s/resourceGroups/%s/providers/Microsoft.Web/sites/%s",
                subscriptionId, resourceGroup, siteName);

        builder.put(ATTR_CLOUD_RESOURCE_ID, resourceId);
    }

    private static void populateDeploymentEnvironment(AttributesBuilder builder) {
        String slotName = env(ENV_SLOT_NAME);
        if (slotName.isEmpty()) {
            slotName = "production";
        }
        builder.put(ATTR_DEPLOYMENT_ENVIRONMENT, slotName);
    }

    /* ===================================================================
       Tiny util
       =================================================================== */

    /** Returns the trimmed env-var value or the empty string if missing. */
    private static String env(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value.trim();
    }
}
