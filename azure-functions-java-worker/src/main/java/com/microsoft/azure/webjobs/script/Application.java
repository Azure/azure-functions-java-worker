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
    public int getPort() { return this.port; }
    public String getRequestId() { return this.requestId; }


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
            this.requestId = this.parseRequestId(commands.getOptionValue("q"));
            this.commandParseSucceeded = true;
        } catch (ParseException ex) {
            LOGGER.severe(ex.toString());
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

    private boolean commandParseSucceeded = false;
    private String host;
    private int port;
    private String requestId;

    private final Options OPTIONS = new Options()
            .addOption(Option.builder("h")
                    .longOpt("host")
                    .hasArg()
                    .argName("HostName")
                    .desc("The address of the machine that the webjobs host is running on")
                    .required()
                    .build())
            .addOption(Option.builder("p")
                    .longOpt("port")
                    .hasArg()
                    .argName("PortNumber")
                    .desc("The port number which the webjobs host is listening to")
                    .required()
                    .build())
            .addOption(Option.builder("q")
                    .longOpt("requestId")
                    .hasArg()
                    .argName("RequestId")
                    .desc("The request ID of this communication session")
                    .required()
                    .build());


    public static void main(String[] args) throws IOException {
        Application app = new Application(args);
        if (!app.isCommandlineValid()) {
            app.printUsage();
            System.exit(1);
        } else {
            try (JavaWorkerClient client = new JavaWorkerClient(app)) {
                client.listen(app.getRequestId());
            } catch (Exception ex) {
                LOGGER.severe(stackTraceToString(ex));
                System.exit(-1);
            }
        }
    }

    public static String version() {
        return Application.class.getPackage().getImplementationVersion();
    }

    public static String stackTraceToString(Throwable t) {
        try (StringWriter writer = new StringWriter();
             PrintWriter printer = new PrintWriter(writer)) {
            t.printStackTrace(printer);
            return writer.toString();
        } catch (IOException nestedException) {
            nestedException.printStackTrace();
            return t.toString();
        }
    }

    // Get an anonymous logger so that the client code won't pollute it through the LogManager
    public static final Logger LOGGER = Logger.getAnonymousLogger();
}
