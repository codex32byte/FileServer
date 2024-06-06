package com.example.Server;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicBoolean;

public class FileServer extends Application {

    private static final int START_PORT = 5000;
    private static final int MAX_PORT_ATTEMPTS = 100;
    private ServerSocket serverSocket;
    private ServerController serverController;

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("ServerUI.fxml"));
        Parent root = loader.load();
        primaryStage.setTitle("File Server");
        primaryStage.setScene(new Scene(root, 600, 400));
        primaryStage.show();
        // Check if the server directory exists, if not, create it
        File serverDirectory = new File("server_directory");
        if (!serverDirectory.exists()) {
            if (serverDirectory.mkdir()) {
                System.out.println("Server directory created.");
            } else {
                System.err.println("Failed to create server directory.");
            }
        }

        serverController = loader.getController();
        serverController.init(this);

        startServer();
        // Add event handler for close request
        primaryStage.setOnCloseRequest(event -> {

            Platform.exit();
            System.exit(0);
        });
    }

    private void startServer() {
        AtomicBoolean serverStarted = new AtomicBoolean(false);

        // Attempt to start the server on a port within the specified range
        for (int port = START_PORT; port < START_PORT + MAX_PORT_ATTEMPTS; port++) {
            try {
                serverSocket = new ServerSocket(port, 0, InetAddress.getByName("0.0.0.0"));
                System.out.println("Server started on port " + port);
                serverStarted.set(true);
                break;
            } catch (IOException e) {
                System.out.println("Port " + port + " is busy.");
            }
        }

        // Check if the server started successfully
        if (!serverStarted.get()) {
            System.out.println("Unable to start server. All ports are busy.");
            return;
        }

        // Start a thread to accept client connections
        new Thread(() -> {
            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    new Thread(() -> handleClient(clientSocket)).start();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void handleClient(Socket clientSocket) {
        try (DataInputStream inputStream = new DataInputStream(clientSocket.getInputStream())) {
            String command = inputStream.readUTF();
            String fileName = inputStream.readUTF();

            // Handle different commands from the client
            switch (command) {
                case "UPLOAD":
                    handleFileUpload(inputStream, fileName);
                    System.out.println("File uploaded: " + fileName);
                    break;
                case "DOWNLOAD":
                    handleFileDownload(clientSocket, fileName);
                    System.out.println("File downloaded: " + fileName);
                    break;
                case "MOVE":
                    String targetDirectory = inputStream.readUTF();
                    handleFileMove(fileName, targetDirectory);
                    //System.out.println("File moved: " + fileName + " to " + targetDirectory);
                    break;
                case "DELETE":
                    handleFileDelete(fileName);
                    //System.out.println("File deleted: " + fileName);
                    break;
                default:
                    System.out.println("Unknown command: " + command);
                    break;
            }

            inputStream.close();
            clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleFileUpload(DataInputStream inputStream, String fileName) throws IOException {
        try (FileOutputStream fileOutputStream = new FileOutputStream("server_directory/" + fileName)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                fileOutputStream.write(buffer, 0, bytesRead);
            }
        }

        // Notify controller to update the file list
        Platform.runLater(serverController::updateFileList);
    }

    private void handleFileDownload(Socket clientSocket, String fileName) throws IOException {
        File file = findFileRecursively(new File("server_directory"), fileName);
        if (file != null && file.exists() && file.isFile()) {
            try (FileInputStream fileInputStream = new FileInputStream(file);
                 DataOutputStream dataOutputStream = new DataOutputStream(clientSocket.getOutputStream())) {

                // Send file size
                dataOutputStream.writeLong(file.length());

                // Send file data
                byte[] buffer = new byte[4096]; // Larger buffer size for efficiency
                int bytesRead;
                while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                    dataOutputStream.write(buffer, 0, bytesRead);
                }
                dataOutputStream.flush();
            }
        } else {
            System.out.println("File not found or is not a file: " + fileName);
        }
    }

    private void handleFileMove(String sourcePath, String targetDirectory) {
        File fileToMove = new File(sourcePath);
        if (fileToMove.exists()) {
            try {
                Files.move(fileToMove.toPath(), Paths.get(targetDirectory, fileToMove.getName()), StandardCopyOption.REPLACE_EXISTING);
                System.out.println("File " + fileToMove.getName() + " moved to " + targetDirectory);
                // Notify controller to update the file list
                Platform.runLater(serverController::updateFileList);
            } catch (IOException e) {
                System.out.println("Failed to move the file.");
                e.printStackTrace();
            }
        } else {
            System.out.println("File " + fileToMove.getName() + " not found.");
        }
    }

    private void handleFileDelete(String fileName) {
        File fileToDelete = findFileRecursively(new File("server_directory"), fileName);
        if (fileToDelete != null && fileToDelete.exists()) {
            if (fileToDelete.delete()) {
                System.out.println("File deleted: " + fileName);
                // Notify controller to update the file list
                Platform.runLater(serverController::updateFileList);
            } else {
                System.out.println("Failed to delete the file.");
            }
        } else {
            System.out.println("File " + fileName + " not found.");
        }
    }

    /**
     * Recursively searches for a file within the specified directory and its subdirectories.
     *
     * @param directory The root directory to start the search from.
     * @param fileName  The name of the file to search for.
     * @return The File object representing the found file, or null if the file is not found.
     */
    private File findFileRecursively(File directory, String fileName) {
        // Check if the provided directory is actually a directory
        if (directory.isDirectory()) {
            // Iterate over each file and subdirectory in the current directory
            for (File file : directory.listFiles()) {
                // If the current file is a directory, recursively search within it
                if (file.isDirectory()) {
                    File found = findFileRecursively(file, fileName);
                    // If the file is found in the subdirectory, return it
                    if (found != null) {
                        return found;
                    }
                    // If the current file is not a directory, check if it matches the file name
                } else if (file.getName().equals(fileName)) {
                    return file;
                }
            }
        }
        // Return null if the file was not found in the directory or its subdirectories
        return null;
    }


    public static void main(String[] args) {
        launch(args);
    }
}
