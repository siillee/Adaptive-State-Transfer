package thesis_main_code.utils;

import thesis_main_code.new_algorithm.Sender;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;

/**
 * This class is used to execute external commands,
 * most notably the tc commands used to control network traffic.
 */
public class CommandExecutor {

    /**
     * Executes a command on the Linux terminal.
     *
     * @param command The command to execute.
     * @return The output of the command.
     * @throws IOException If an I/O error occurs.
     * @throws InterruptedException If the execution is interrupted.
     */
    public static String executeCommand(String command, boolean sudo) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder();

        // TODO: Only needed locally!
        if (sudo) {
            processBuilder.command("sudo", "-S", "bash", "-c", command);
        } else {
            processBuilder.command("bash", "-c", command);
        }

        Process process = processBuilder.start();

        // Capture output
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        // Capture any errors
        StringBuilder errorOutput = new StringBuilder();
        try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = errorReader.readLine()) != null) {
                errorOutput.append(line).append("\n");
            }
        }

        // Wait for the process to finish and check the exit status
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Command execution failed with exit code " + exitCode + ":\n" + errorOutput);
        }

        return output.toString();
    }

    // TODO: Use this main for testing certain commands, if they are working, and if the output is correct
//    public static void main(String[] args) {
//        try {
////            String result = executeCommand("sudo -S tc class change dev lo parent 1:1 classid 1:20 htb rate 1mbit ceil 250mbit", true);
//
////            String result = executeCommand("tc class change dev lo parent 1:1 classid 1:10 htb rate 1mbit ceil 250mbit ; " +
////                    "tc class change dev lo parent 1:1 classid 1:20 htb rate 1mbit ceil 250mbit ; " +
////                    "tc class change dev lo parent 1:1 classid 1:30 htb rate 1mbit ceil 250mbit ; " +
////                    "tc class change dev lo parent 1:1 classid 1:40 htb rate 1mbit ceil 250mbit ; ", true);
//
//
//            String result = executeCommand("tc class change dev lo parent 1:1 classid 1:10 htb rate 1mbit ceil 250mbit ; " +
//                    "tc class change dev lo parent 1:1 classid 1:20 htb rate 1mbit ceil 250mbit ; ", true);
//            System.out.println("Command Output:\n" + result);
//        } catch (IOException | InterruptedException e) {
//            e.printStackTrace();
//        }
//    }
}

