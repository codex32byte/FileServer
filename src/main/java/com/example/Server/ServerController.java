package com.example.Server;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Stack;

public class ServerController {

    @FXML
    private ListView<String> fileList;

    @FXML
    private TextField currentDirectory;

    private File currentDir;
    private Stack<File> directoryHistory;
    private FileServer fileServer;
    private File selectedFile;

    private static final String SERVER_HOSTNAME = "localhost"; // Server's hostname or IP
    private static final int START_PORT = 5000;
    private static final int MAX_PORT_ATTEMPTS = 100;

    /**
     * Initializes the controller with the reference to the main FileServer instance.
     *
     * @param fileServer the main FileServer instance
     */
    public void init(FileServer fileServer) {
        this.fileServer = fileServer;
        currentDir = new File(System.getProperty("user.dir") + "/server_directory");
        directoryHistory = new Stack<>();
        updateFileList();
    }

    /**
     * Updates the file list in the UI to reflect the current directory contents.
     */
    public void updateFileList() {
        Platform.runLater(() -> {
            fileList.getItems().clear();
            currentDirectory.setText(currentDir.getAbsolutePath());
            for (File file : currentDir.listFiles()) {
                fileList.getItems().add(file.getName());
            }
        });
    }

    /**
     * Opens a file chooser dialog for selecting a file to upload.
     */
    @FXML
    private void onChooseFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select File to Send");
        selectedFile = fileChooser.showOpenDialog(null);
    }

    /**
     * Initiates the file upload process if a file has been selected.
     */
    @FXML
    private void onSendFile() {
        if (selectedFile != null) {
            sendFile(selectedFile);
        }
    }

    /**
     * Sends the selected file to the server.
     *
     * @param file the file to be sent
     */
    private void sendFile(File file) {
        new Thread(() -> {
            boolean fileSent = false;

            try {
                InetAddress[] serverAddresses = InetAddress.getAllByName(SERVER_HOSTNAME);

                for (InetAddress address : serverAddresses) {
                    for (int port = START_PORT; port < START_PORT + MAX_PORT_ATTEMPTS; port++) {
                        try (Socket socket = new Socket(address, port);
                             DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
                             FileInputStream fileInputStream = new FileInputStream(file)) {

                            outputStream.writeUTF("UPLOAD");
                            outputStream.writeUTF(file.getName());
                            outputStream.flush();

                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                                outputStream.write(buffer, 0, bytesRead);
                            }
                            outputStream.flush();
                            fileSent = true;
                            System.out.println("File sent: " + file.getName());
                            break;
                        } catch (IOException e) {
                            System.out.println("Failed to connect to " + address.getHostAddress() + " on port " + port + ". Trying next...");
                        }
                    }
                    if (fileSent) {
                        break;
                    }
                }
            } catch (UnknownHostException e) {
                System.out.println("Unable to resolve hostname: " + SERVER_HOSTNAME);
            }

            if (!fileSent) {
                System.out.println("Unable to send file. All IP addresses and ports are busy.");
            }
        }).start();
    }


    private void showAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.showAndWait();
        });
    }
    /**
     * Initiates the file download process for the selected file.
     */
    @FXML
    private void onDownload() {
        String selectedFile = fileList.getSelectionModel().getSelectedItem();
        if (selectedFile != null) {
            Platform.runLater(() -> {
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("Save File");
                fileChooser.setInitialFileName(selectedFile);
                File saveFile = fileChooser.showSaveDialog(null);

                if (saveFile != null) {
                    // Include the relative path from currentDir for download
                    String relativeFilePath = currentDir.toPath().relativize(new File(currentDir, selectedFile).toPath()).toString();
                    new Thread(() -> downloadFile(relativeFilePath, saveFile)).start();
                }
            });
        }
    }

    /**
     * Downloads the specified file from the server and saves it to the specified location.
     *
     * @param fileName the name of the file to be downloaded
     * @param saveFile the file object representing the location to save the downloaded file
     */
    private void downloadFile(String fileName, File saveFile) {
        try {
            InetAddress[] serverAddresses = InetAddress.getAllByName(SERVER_HOSTNAME);

            for (InetAddress address : serverAddresses) {
                for (int port = START_PORT; port < START_PORT + MAX_PORT_ATTEMPTS; port++) {
                    try (Socket socket = new Socket(address, port);
                         DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream());
                         DataInputStream dataInputStream = new DataInputStream(socket.getInputStream());
                         FileOutputStream fileOutputStream = new FileOutputStream(saveFile)) {

                        dataOutputStream.writeUTF("DOWNLOAD");
                        dataOutputStream.writeUTF(fileName); // Ensure file name includes subdirectory if any
                        dataOutputStream.flush();

                        // Read file size sent by the server
                        long fileSize = dataInputStream.readLong();

                        byte[] buffer = new byte[4096]; // Larger buffer size for efficiency
                        int bytesRead;
                        long totalBytesRead = 0;
                        while (totalBytesRead < fileSize && (bytesRead = dataInputStream.read(buffer)) != -1) {
                            fileOutputStream.write(buffer, 0, bytesRead);
                            totalBytesRead += bytesRead;
                        }
                        System.out.println("File downloaded successfully: " + saveFile.getAbsolutePath());
                        return; // Exit the loop once the file is downloaded
                    } catch (IOException e) {
                        System.out.println("Failed to connect to " + address.getHostAddress() + " on port " + port + ". Trying next...");
                    }
                }
            }
        } catch (UnknownHostException e) {
            System.out.println("Unable to resolve hostname: " + SERVER_HOSTNAME);
        }
    }

    /**
     * Moves the selected file to a new directory chosen by the user.
     */
    @FXML
    private void onMove() {
        String selectedFile = fileList.getSelectionModel().getSelectedItem();
        if (selectedFile != null) {
            File fileToMove = new File(currentDir.getAbsolutePath() + File.separator + selectedFile);

            DirectoryChooser directoryChooser = new DirectoryChooser();
            directoryChooser.setTitle("Move File");
            File selectedDirectory = directoryChooser.showDialog(null);

            if (selectedDirectory != null) {
                new Thread(() -> {
                    boolean fileMoved = false;
                    InetAddress[] serverAddresses = new InetAddress[0];
                    try {
                        serverAddresses = InetAddress.getAllByName(SERVER_HOSTNAME);
                    } catch (UnknownHostException e) {
                        e.printStackTrace();
                    }

                    for (InetAddress address : serverAddresses) {
                        for (int port = START_PORT; port < START_PORT + MAX_PORT_ATTEMPTS; port++) {
                            try (Socket socket = new Socket(address, port);
                                 DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream())) {

                                outputStream.writeUTF("MOVE");
                                outputStream.writeUTF(fileToMove.getAbsolutePath());
                                outputStream.writeUTF(selectedDirectory.getAbsolutePath());
                                outputStream.flush();
                                fileMoved = true;
                                //System.out.println("File moved: " + fileToMove.getName() + " to " + selectedDirectory.getAbsolutePath());
                                break;
                            } catch (IOException e) {
                                System.out.println("Failed to connect to " + address.getHostAddress() + " on port " + port + ". Trying next...");
                            }
                        }
                        if (fileMoved) {
                            break;
                        }
                    }

                    if (!fileMoved) {
                        System.out.println("Unable to move file. All IP addresses and ports are busy.");
                    }
                }).start();
            }
        }
    }

    /**
     * Deletes the selected file from the server.
     */
    @FXML
    private void onDelete() {
        String selectedFile = fileList.getSelectionModel().getSelectedItem();
        if (selectedFile != null) {
            new Thread(() -> {
                boolean fileDeleted = false;
                InetAddress[] serverAddresses = new InetAddress[0];
                try {
                    serverAddresses = InetAddress.getAllByName(SERVER_HOSTNAME);
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }

                for (InetAddress address : serverAddresses) {
                    for (int port = START_PORT; port < START_PORT + MAX_PORT_ATTEMPTS; port++) {
                        try (Socket socket = new Socket(address, port);
                             DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream())) {

                            outputStream.writeUTF("DELETE");
                            outputStream.writeUTF(selectedFile);
                            outputStream.flush();
                            fileDeleted = true;
                            //System.out.println("File deleted: " + selectedFile);
                            break;
                        } catch (IOException e) {
                            System.out.println("Failed to connect to " + address.getHostAddress() + " on port " + port + ". Trying next...");
                        }
                    }
                    if (fileDeleted) {
                        break;
                    }
                }

                if (!fileDeleted) {
                    System.out.println("Unable to delete file. All IP addresses and ports are busy.");
                }
            }).start();
        }
    }

    /**
     * Navigates into the selected directory.
     */
    @FXML
    private void onDirectorySelection() {
        String selectedDirectory = fileList.getSelectionModel().getSelectedItem();
        if (selectedDirectory != null) {
            File selectedDir = new File(currentDir.getAbsolutePath() + File.separator + selectedDirectory);
            if (selectedDir.isDirectory()) {
                directoryHistory.push(currentDir);
                currentDir = selectedDir;
                updateFileList();
            }
        }
    }

    /**
     * Navigates back to the previous directory.
     */
    @FXML
    private void onBack() {
        if (!directoryHistory.isEmpty()) {
            currentDir = directoryHistory.pop();
            updateFileList();
        }
    }
}
