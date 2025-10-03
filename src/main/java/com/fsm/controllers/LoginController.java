package com.fsm.controllers;

import com.fsm.database.MongoManager;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.Node;
import javafx.stage.Stage;
import javafx.event.ActionEvent;
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
                // CRITICAL STEP 1: Get the user's role from the authenticated document
                String userRole = authenticatedUser.getString("role");

                // 1. Get the current stage (login window)
                Stage loginStage = (Stage)((Node) event.getSource()).getScene().getWindow();

                // 2. Load the new FXML (Main Dashboard)
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/fsm/main-dashboard-view.fxml"));
                Parent root = loader.load();

                // 3. Get the controller instance
                MainDashboardController dashboardController = loader.getController();

                // CRITICAL FIX: Now passing BOTH username and role to initData (Resolves Compilation Error 1)
                dashboardController.initData(username, userRole);

                // 4. Set up the new scene and stage
                Stage mainStage = new Stage();
                mainStage.setTitle("Field Survey Manager - Dashboard");
                mainStage.setScene(new Scene(root, 800, 600));

                // 5. Load the default Surveys view *after* role restrictions are applied.
                // Note: initData() applies restrictions, but we still need to load the first view.
                // This call relies on loadDefaultView() being PUBLIC in MainDashboardController.
                dashboardController.loadDefaultView(); // (Resolves Compilation Error 2)

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
