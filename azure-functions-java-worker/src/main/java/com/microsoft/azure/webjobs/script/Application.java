package com.microsoft.azure.webjobs.script;

import java.io.*;
import java.util.logging.*;
import javax.annotation.*;

import org.apache.commons.cli.*;

/**
 * The entry point of the Java Language Worker. Every component could get the command line options from this singleton
 * Application instance, and typically that instance will be passed to your components as constructor arguments.
 */
public final class Application {
    private Application(String[] args) {
        this.parseCommandLine(args);
    }

    public String getHost() { return this.host; }
    int getPort() { return this.port; }
    private String getWorkerId() { return this.workerId; }
    private String getRequestId() { return this.requestId; }
    boolean logToConsole() { return this.logToConsole; }

    private void printUsage() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("Application", this.OPTIONS, true);
    }

    private boolean isCommandlineValid() { return this.commandParseSucceeded; }

    @PostConstruct
    private void parseCommandLine(String[] args) {
        CommandLineParser parser = new DefaultParser();
        try {
            CommandLine commands = parser.parse(this.OPTIONS, args);
            this.host = this.parseHost(commands.getOptionValue("h"));
            this.port = this.parsePort(commands.getOptionValue("p"));
            this.workerId = this.parseWorkerId(commands.getOptionValue("w"));
            this.requestId = this.parseRequestId(commands.getOptionValue("q"));
            this.logToConsole = commands.hasOption("l");
            this.commandParseSucceeded = true;
        } catch (ParseException ex) {
            WorkerLogManager.getSystemLogger().severe(ex.toString());
            this.commandParseSucceeded = false;
        }
    }

    private String parseHost(String input) { return input; }

    private int parsePort(String input) throws ParseException {
        try {
            int result = Integer.parseInt(input);
            if (result < 1 || result > 65535) {
                throw new IndexOutOfBoundsException("port number out of range");
            }
            return result;
        } catch (NumberFormatException | IndexOutOfBoundsException ex) {
            throw new ParseException(String.format(
                    "port number \"%s\" is not qualified. It must be an integer within range [1, 65535]", input));
        }
    }

    private String parseRequestId(String input) { return input; }

    private String parseWorkerId(String input) { return input; }

    private boolean commandParseSucceeded = false;
    private String host;
    private int port;
    private String workerId, requestId;
    private boolean logToConsole;

    private final Options OPTIONS = new Options()
            .addOption(Option.builder("h").longOpt("host")
                    .hasArg().argName("HostName")
                    .desc("The address of the machine that the Azure Functions host is running on")
                    .required()
                    .build())
            .addOption(Option.builder("p").longOpt("port")
                    .hasArg().argName("PortNumber")
                    .desc("The port number which the Azure Functions host is listening to")
                    .required()
                    .build())
            .addOption(Option.builder("w").longOpt("workerId")
                    .hasArg().argName("WorkerId")
                    .desc("The ID of this running worker throughout communication session")
                    .required()
                    .build())
            .addOption(Option.builder("q").longOpt("requestId")
                    .hasArg().argName("RequestId")
                    .desc("The startup request ID of this communication session")
                    .required()
                    .build())
            .addOption(Option.builder("l").longOpt("consoleLog")
                    .desc("Whether to duplicate all host logs to console as well")
                    .build());


    public static void main(String[] args) throws IOException {
        System.out.println("Microsoft Azure Functions Java Runtime [build " + version() + "]");
        Application app = new Application(args);
        if (!app.isCommandlineValid()) {
            app.printUsage();
            System.exit(1);
        } else {
            try (JavaWorkerClient client = new JavaWorkerClient(app)) {
                client.listen(app.getWorkerId(), app.getRequestId());
            } catch (Exception ex) {
                WorkerLogManager.getSystemLogger().log(Level.SEVERE, "Unexpected Exception causes system to exit", ex);
                System.exit(-1);
            }
        }
    }

    public static String version() {
        String jarVersion = Application.class.getPackage().getImplementationVersion();
        return jarVersion != null && !jarVersion.isEmpty() ? jarVersion : "Unknown";
    }
}
