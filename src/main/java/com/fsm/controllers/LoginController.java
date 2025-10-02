package com.fsm.controllers;

import com.fsm.database.MongoManager;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.Node; // Required for getScene().getWindow()
import javafx.stage.Stage; // Required for close()
import javafx.event.ActionEvent; // Required for the method signature
import org.bson.Document;
import javafx.scene.Parent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import java.io.IOException;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label statusLabel;

    /**
     * Handles the login button click event.
     * We pass ActionEvent to get access to the scene/window.
     */
    @FXML
    private void handleLoginButton(ActionEvent event) {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Please enter both username and password.");
            return;
        }

        Document authenticatedUser = MongoManager.authenticateUser(username, password);

        if (authenticatedUser != null) {
            // SUCCESS: Load the Main Dashboard
            try {
                // 1. Get the current stage (login window)
                Stage loginStage = (Stage)((Node) event.getSource()).getScene().getWindow();

                // 2. Load the new FXML (Main Dashboard)
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/fsm/main-dashboard-view.fxml"));
                Parent root = loader.load();

                // 3. Get the controller instance to call a method on it
                MainDashboardController dashboardController = loader.getController();

                // 4. Set up the new scene and stage
                Stage mainStage = new Stage();
                mainStage.setTitle("Field Survey Manager - Dashboard");
                mainStage.setScene(new Scene(root, 800, 600)); // Larger size for dashboard

                // 5. CRITICAL: Load the default Surveys view *before* showing the stage.
                dashboardController.loadDefaultView();

                // 6. Show the main stage and close the login stage
                mainStage.show();
                loginStage.close();

            } catch (IOException e) {
                e.printStackTrace();
                statusLabel.setText("Error loading main application view.");
            }

        } else {
            // FAILURE: Login failed
            statusLabel.setText("Invalid username or password. Please try again.");
            passwordField.clear();
        }
    }
}