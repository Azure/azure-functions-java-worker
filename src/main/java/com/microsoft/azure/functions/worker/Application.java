package com.microsoft.azure.functions.worker;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.logging.*;
import javax.annotation.*;

import org.apache.commons.cli.*;
import org.apache.commons.lang3.exception.ExceptionUtils;

import static com.microsoft.azure.functions.worker.Constants.*;

/**
 * The entry point of the Java Language Worker. Every component could get the command line options from this singleton
 * Application instance, and typically that instance will be passed to your components as constructor arguments.
 */
public final class Application implements IApplication {
    private Application(String[] args) {
        this.parseCommandLine(args);
    }

    @PostConstruct
    private void parseCommandLine(String[] args) {
        CommandLineParser parser = new DefaultParser();
        try {
            CommandLine commands = parser.parse(this.OPTIONS, args, true);
            this.uri = this.parseUri(commands.getOptionValue(FUNCTIONS_URI_OPTION));
            this.workerId = this.parseWorkerId(commands.getOptionValue(FUNCTIONS_WORKER_ID_OPTION));
            this.requestId = this.parseRequestId(commands.getOptionValue(FUNCTIONS_REQUEST_ID_OPTION));
            this.logToConsole = commands.hasOption(FUNCTIONS_CONSOLE_LOG_OPTION);
            if (commands.hasOption(FUNCTIONS_GRPC_MAX_MESSAGE_LENGTH_OPTION)) {
                this.maxMessageSize = this.parseMaxMessageSize(commands.getOptionValue(FUNCTIONS_GRPC_MAX_MESSAGE_LENGTH_OPTION));
            }
            this.commandParseSucceeded = true;
        } catch (ParseException ex) {
            WorkerLogManager.getSystemLogger().severe(ex.toString());
            this.commandParseSucceeded = false;
        }
    }

    public static void main(String[] args) {
        WorkerLogManager.getSystemLogger().log(Level.INFO, "Azure Functions Java Worker  version [ " + version() + "]");
        Application app = new Application(args);
        if (!app.isCommandlineValid()) {
            app.printUsage();
            System.exit(1);
        } else {
            try (JavaWorkerClient client = new JavaWorkerClient(app)) {
                client.listen(app.getWorkerId(), app.getRequestId()).get();
            } catch (Exception ex) {
                WorkerLogManager.getSystemLogger().log(Level.SEVERE, ExceptionUtils.getRootCauseMessage(ex), ex);
                System.exit(-1);
            }
        }
    }

    private boolean commandParseSucceeded = false;
    private String uri, host, workerId, requestId;
    private int port;
    private boolean logToConsole;
    private Integer maxMessageSize = null;
    private final Options OPTIONS = new Options()
            .addOption(Option.builder("u").longOpt(FUNCTIONS_URI_OPTION)
                    .hasArg().argName("Uri")
                    .desc("The uri of the machine that the Azure Functions host is running on")
                    .required()
                    .build())
            .addOption(Option.builder("w").longOpt(FUNCTIONS_WORKER_ID_OPTION)
                    .hasArg().argName("WorkerId")
                    .desc("The ID of this running worker throughout communication session")
                    .required()
                    .build())
            .addOption(Option.builder("q").longOpt(FUNCTIONS_REQUEST_ID_OPTION)
                    .hasArg().argName("RequestId")
                    .desc("The startup request ID of this communication session")
                    .required()
                    .build())
            .addOption(Option.builder("l").longOpt(FUNCTIONS_GRPC_MAX_MESSAGE_LENGTH_OPTION)
                    .hasArg().argName("MessageSizeInBytes")
                    .desc("The maximum message size could be used by GRPC protocol")
                    .build())
            .addOption(Option.builder("m").longOpt(FUNCTIONS_CONSOLE_LOG_OPTION)
                    .desc("Whether to duplicate all host logs to console as well")
                    .build());

    @Override
    public String getHost() {
        return this.host;
    }

    @Override
    public int getPort() {
        return this.port;
    }

    public String getUri() {
        return this.uri;
    }

    @Override
    public boolean logToConsole() {
        return this.logToConsole;
    }

    @Override
    public Integer getMaxMessageSize() {
        return this.maxMessageSize;
    }

    private String getWorkerId() {
        return this.workerId;
    }

    private String getRequestId() {
        return this.requestId;
    }

    private boolean isCommandlineValid() {
        return this.commandParseSucceeded;
    }

    private String parseUri(String uri) throws ParseException {
        try {
            URL url = new URL(uri);
            url.toURI();
            this.host = url.getHost();
            this.port = url.getPort();
            if (port < 1 || port > 65535) {
                throw new IndexOutOfBoundsException("port number out of range");
            }
            return uri;
        } catch (MalformedURLException | URISyntaxException | IndexOutOfBoundsException e) {
            throw new ParseException(String.format(
                    "Error parsing URI \"%s\". Please provide a valid URI", uri));
        }
    }

    private String parseRequestId(String input) {
        return input;
    }

    private String parseWorkerId(String input) {
        return input;
    }

    private Integer parseMaxMessageSize(String input) {
        return Integer.parseInt(input);
    }

    public static String version() {
        String jarVersion = Application.class.getPackage().getImplementationVersion();
        return jarVersion != null && !jarVersion.isEmpty() ? jarVersion : "Unknown";
    }

    private void printUsage() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.setWidth(100);
        formatter.printHelp("Application", this.OPTIONS, true);
    }
}
