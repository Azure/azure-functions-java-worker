package com.microsoft.azure.functions.worker.test.utilities;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Generates ephemeral TLS material (a self-signed {@code CN=localhost} certificate,
 * its private key, and a matching truststore) for the gRPC transport tests.
 *
 * <p>The material is produced at runtime with the JDK's {@code keytool} so that no
 * private keys are committed to the repository. {@code keytool} ships with every
 * JDK (8 through 25), which keeps this portable across the CI Java matrix without
 * pulling in a certificate-generation dependency such as Bouncy Castle.</p>
 *
 * <p>A single instance is shared across the test server and client so that the
 * server's certificate is trusted by the client. All files are written to a
 * temporary directory that is deleted when the JVM exits.</p>
 */
public final class TestTlsMaterial {

    /** Fixed alias/password; this is throwaway, per-run material with no security value. */
    public static final String ALIAS = "localhost";
    public static final String PASSWORD = "changeit";
    public static final String STORE_TYPE = "PKCS12";

    private static final long KEYTOOL_TIMEOUT_SECONDS = 60;

    private static volatile TestTlsMaterial instance;

    private final Path serverKeyStore;
    private final Path trustStore;

    private TestTlsMaterial() {
        try {
            Path directory = Files.createTempDirectory("grpc-tls-test");
            directory.toFile().deleteOnExit();

            this.serverKeyStore = directory.resolve("server.p12");
            this.trustStore = directory.resolve("truststore.p12");
            Path certificate = directory.resolve("localhost-cert.pem");

            generateKeyPair(this.serverKeyStore);
            exportCertificate(this.serverKeyStore, certificate);
            importCertificate(certificate, this.trustStore);

            this.serverKeyStore.toFile().deleteOnExit();
            this.trustStore.toFile().deleteOnExit();
            certificate.toFile().deleteOnExit();
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Failed to generate test TLS material", ex);
        }
    }

    public static TestTlsMaterial getInstance() {
        TestTlsMaterial local = instance;
        if (local == null) {
            synchronized (TestTlsMaterial.class) {
                local = instance;
                if (local == null) {
                    local = new TestTlsMaterial();
                    instance = local;
                }
            }
        }
        return local;
    }

    /** Path to the PKCS12 keystore holding the server's private key and certificate. */
    public Path serverKeyStorePath() {
        return this.serverKeyStore;
    }

    /** Path to the PKCS12 truststore holding the server's certificate (for the client). */
    public Path trustStorePath() {
        return this.trustStore;
    }

    private static void generateKeyPair(Path keyStore) throws IOException, InterruptedException {
        runKeytool(
                "-genkeypair",
                "-alias", ALIAS,
                "-keyalg", "RSA",
                "-keysize", "2048",
                // Short-lived: the material only needs to outlive a single test run.
                "-validity", "2",
                "-dname", "CN=localhost",
                "-ext", "san=dns:localhost,ip:127.0.0.1",
                "-keystore", keyStore.toString(),
                "-storetype", STORE_TYPE,
                "-storepass", PASSWORD,
                "-keypass", PASSWORD);
    }

    private static void exportCertificate(Path keyStore, Path certificate) throws IOException, InterruptedException {
        runKeytool(
                "-exportcert",
                "-rfc",
                "-alias", ALIAS,
                "-keystore", keyStore.toString(),
                "-storetype", STORE_TYPE,
                "-storepass", PASSWORD,
                "-file", certificate.toString());
    }

    private static void importCertificate(Path certificate, Path trustStore) throws IOException, InterruptedException {
        runKeytool(
                "-importcert",
                "-noprompt",
                "-alias", ALIAS,
                "-file", certificate.toString(),
                "-keystore", trustStore.toString(),
                "-storetype", STORE_TYPE,
                "-storepass", PASSWORD);
    }

    private static void runKeytool(String... arguments) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(keytoolPath());
        for (String argument : arguments) {
            command.add(argument);
        }

        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();

        // Drain output on a background thread so a misbehaving keytool cannot block
        // us indefinitely; the timeout below then governs the overall wait.
        StringBuilder output = new StringBuilder();
        Thread drainer = new Thread(() -> {
            try (InputStream in = process.getInputStream()) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[4096];
                int read;
                while ((read = in.read(chunk)) != -1) {
                    buffer.write(chunk, 0, read);
                }
                synchronized (output) {
                    output.append(new String(buffer.toByteArray(), StandardCharsets.UTF_8));
                }
            } catch (IOException ignored) {
                // Output is best-effort; failures are surfaced via the exit code below.
            }
        });
        drainer.setDaemon(true);
        drainer.start();

        if (!process.waitFor(KEYTOOL_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("keytool timed out running: " + arguments[0]);
        }
        drainer.join(TimeUnit.SECONDS.toMillis(5));

        if (process.exitValue() != 0) {
            synchronized (output) {
                throw new IOException(
                        "keytool " + arguments[0] + " failed (exit " + process.exitValue() + "):\n" + output);
            }
        }
    }

    private static String keytoolPath() throws IOException {
        String javaHome = System.getProperty("java.home");
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        Path keytool = Paths.get(javaHome, "bin", windows ? "keytool.exe" : "keytool");
        if (!Files.isExecutable(keytool)) {
            throw new IOException("keytool not found or not executable at: " + keytool);
        }
        return keytool.toString();
    }
}
