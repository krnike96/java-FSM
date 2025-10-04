package com.fsm.controllers;

import com.fsm.database.MongoManager;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.ComboBox;
import javafx.scene.Node;
import javafx.stage.Stage;
import javafx.event.ActionEvent;
import org.bson.Document;
import javafx.scene.Parent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import java.io.IOException;
import java.util.Arrays;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label statusLabel;
    @FXML private ComboBox<String> roleComboBox;

    @FXML
    public void initialize() {
        // Populate the ComboBox with the defined user roles
        roleComboBox.getItems().addAll(
                "Administrator",
                "Survey Creator",
                "Data Entry"
        );
    }

    /**
     * Handles the login button click event.
     * Since MongoManager.authenticateUser() is a static helper,
     * it now internally calls MongoManager.getInstance().getDatabase(),
     * ensuring the database connection is initialized and used efficiently.
     */
    @FXML
    private void handleLoginButton(ActionEvent event) {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String selectedRole = roleComboBox.getValue();

        if (selectedRole == null) {
            statusLabel.setText("Please select your role.");
            return;
        }

        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Please enter both username and password.");
            return;
        }

        // Call the static helper method. This is where the Singleton connection is
        // first accessed (or initialized if it's the very first call).
        Document authenticatedUser = MongoManager.authenticateUser(username, password, selectedRole);

        if (authenticatedUser != null) {
            // SUCCESS: Load the Main Dashboard
            try {
                // The role is already known (selectedRole), but we confirm it matches the document
                String userRole = authenticatedUser.getString("role");

                // 1. Get the current stage (login window)
                Stage loginStage = (Stage)((Node) event.getSource()).getScene().getWindow();

                // 2. Load the new FXML (Main Dashboard)
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/fsm/main-dashboard-view.fxml"));
                Parent root = loader.load();

                // 3. Get the controller instance
                MainDashboardController dashboardController = loader.getController();

                // Pass the credentials to the dashboard controller
                dashboardController.initData(username, userRole);

                // 4. Set up the new scene and stage
                Stage mainStage = new Stage();
                mainStage.setTitle("Field Survey Manager - Dashboard");
                mainStage.setScene(new Scene(root, 800, 600));

                // 5. Load the default Surveys view *after* role restrictions are applied.
                dashboardController.loadDefaultView();

                // 6. Show the main stage and close the login stage
                mainStage.show();
                loginStage.close();

            } catch (IOException e) {
                e.printStackTrace();
                statusLabel.setText("Error loading main application view.");
            }

        } else {
            // FAILURE: Login failed.
            statusLabel.setText("Invalid credentials or role mismatch. Please try again.");
            passwordField.clear();
        }
    }
}
