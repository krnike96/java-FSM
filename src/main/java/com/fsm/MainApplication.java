package com.fsm;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Window; // Import Window

import java.io.IOException;

public class MainApplication extends Application {

    // Keep a static reference to the primary stage
    private static Stage primaryStage;

    // The FXML file path must match your package structure
    private static final String FXML_PATH = "/com/fsm/login-view.fxml";

    @Override
    public void start(Stage stage) throws IOException {
        primaryStage = stage; // Store the primary stage reference
        showLoginScreen(null); // Load the login screen initially
    }

    /**
     * Loads the Login View and displays it in a new stage.
     * If an old window is provided, it is closed first.
     * * @param currentWindow The window to close (usually the Dashboard). Can be null.
     */
    public static void showLoginScreen(Window currentWindow) {
        if (currentWindow != null) {
            ((Stage) currentWindow).close();
        }

        try {
            FXMLLoader fxmlLoader = new FXMLLoader(MainApplication.class.getResource(FXML_PATH));
            Scene scene = new Scene(fxmlLoader.load(), 400, 300);

            // If primaryStage doesn't exist (i.e., this is the first call), create a new one.
            // In our case, we always reuse primaryStage.
            if (primaryStage == null) {
                primaryStage = new Stage();
            }

            primaryStage.setTitle("Field Survey Manager - Login");
            primaryStage.setScene(scene);
            primaryStage.show();

        } catch (IOException e) {
            System.err.println("Error loading login screen: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch();
    }
}
