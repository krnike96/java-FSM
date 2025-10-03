package com.fsm.controllers;

import com.fsm.MainApplication;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.control.Label;
import javafx.scene.text.Font;
import javafx.scene.layout.AnchorPane;
import javafx.scene.control.MenuItem;
import javafx.application.Platform;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Modality;
import java.io.IOException;

// Import specific controllers we need to cast to
// NOTE: These controllers must exist with an 'initData(String role, String username)' or similar method.
// We import them here so we can cast and call their methods.

public class MainDashboardController {

    @FXML private BorderPane rootPane;
    @FXML private Button btnSurveys;
    @FXML private Button btnUsers;
    @FXML private Button btnReports;
    @FXML private Button btnSettings;
    @FXML private AnchorPane mainContentArea;

    // Inject MenuItems from FXML
    @FXML private MenuItem menuItemLogout;
    @FXML private MenuItem menuItemExit;
    @FXML private MenuItem menuItemAbout;

    // --- Fields to store logged-in user data ---
    private String currentUserRole;
    private String currentLoggedInUsername;

    @FXML
    public void initialize() {
        // 1. Surveys: Use the decision method to load the correct view based on role
        btnSurveys.setOnAction(event -> loadSurveyDecisionView());

        // 2. Users: Use the dedicated loading method for Admin-only view
        btnUsers.setOnAction(event -> loadUserView());

        // 3. Reports: Use the unified loader, hint: ReportController
        btnReports.setOnAction(event -> loadViewWithData("/com/fsm/report-view.fxml", "ReportController"));

        // 4. Settings: Use the dedicated loading method for profile settings
        btnSettings.setOnAction(event -> loadProfileSettingsView());
    }

    // --- Handle Logout and Exit ---

    /**
     * Handles the 'Logout' menu item click. Closes the current dashboard stage
     * and triggers the MainApplication to reload the login view.
     */
    @FXML
    private void handleLogout() {
        System.out.println("User " + currentLoggedInUsername + " logging out.");
        MainApplication.showLoginScreen(rootPane.getScene().getWindow());
    }

    /**
     * Handles the 'Exit' menu item click. Shuts down the JavaFX application.
     */
    @FXML
    private void handleExit() {
        System.out.println("Exiting application.");
        Platform.exit();
    }

    // --- Handle About Dialog ---
    /**
     * Handles the 'About' menu item click. Displays a simple modal dialog
     * with application information.
     */
    @FXML
    private void handleAbout() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/fsm/about-view.fxml"));
            Parent root = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("About Field Survey Manager");
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.initOwner(rootPane.getScene().getWindow());

            Scene scene = new Scene(root);
            dialogStage.setScene(scene);

            dialogStage.showAndWait();

        } catch (IOException e) {
            System.err.println("Error loading About dialog: " + e.getMessage());
            e.printStackTrace();
            loadErrorView("Could not load About screen.");
        }
    }
    // --- End Handle About Dialog ---


    /**
     * Initializes the dashboard with the logged-in user's credentials.
     * This signature now matches the two-parameter call from LoginController.
     */
    public void initData(String username, String userRole) {
        this.currentLoggedInUsername = username;
        this.currentUserRole = userRole;

        System.out.println("Dashboard initialized for User: " + username + " with Role: " + userRole);

        applyRoleRestrictions();
        // loadDefaultView() will be called next by LoginController.
    }

    /**
     * Checks the user's role and disables unauthorized navigation buttons.
     */
    private void applyRoleRestrictions() {
        boolean isAdmin = "Administrator".equals(currentUserRole);
        boolean isReportUser = isAdmin || "Survey Creator".equals(currentUserRole);

        // Settings button should be visible for ALL users for self-service profile management.
        boolean isSettingsUser = true;

        // USERS is restricted to Administrator
        btnUsers.setManaged(isAdmin);
        btnUsers.setVisible(isAdmin);

        // REPORTS are visible for Administrator and Survey Creator
        btnReports.setManaged(isReportUser);
        btnReports.setVisible(isReportUser);

        // Settings are now visible for everyone
        btnSettings.setManaged(isSettingsUser);
        btnSettings.setVisible(isSettingsUser);
    }

    /**
     * Public method called by LoginController to display the initial view.
     */
    public void loadDefaultView() {
        loadSurveyDecisionView();
    }

    /**
     * Determines which 'Surveys' view to load based on the user's role.
     */
    private void loadSurveyDecisionView() {
        if ("Data Entry".equals(currentUserRole)) {
            // Data Entry users need both role and username for the survey taking interface.
            loadViewWithData("/com/fsm/survey-taker-view.fxml", "SurveyTakerController");
        } else {
            // Admins and Survey Creators get the management interface.
            loadViewWithData("/com/fsm/survey-view.fxml", "SurveyController");
        }
    }


    /**
     * Loads the User view and initializes it with the user's role and username.
     */
    private void loadUserView() {
        if (!btnUsers.isVisible()) {
            loadErrorView("Access Denied: Only Administrators can access User Management.");
            return;
        }
        // Pass both role and username to the UserController for security checks
        loadViewWithData("/com/fsm/user-view.fxml", "UserController");
    }

    /**
     * Loads the Profile Settings view for users to change their own password/username.
     */
    private void loadProfileSettingsView() {
        if (!btnSettings.isVisible()) return; // Should not happen but good safeguard
        // The path to the new FXML file is assumed to be 'profile-settings-view.fxml'
        loadViewWithData("/com/fsm/profile-settings-view.fxml", "ProfileSettingsController");
    }

    /**
     * A unified method for loading FXML views and initializing their controllers
     * with the current user's role AND username.
     * @param fxmlPath The path to the FXML file.
     * @param controllerTypeHint Optional hint for the controller class name (e.g., "SurveyController").
     */
    private void loadViewWithData(String fxmlPath, String controllerTypeHint) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent view = loader.load();

            Object controller = loader.getController();

            // CRITICAL FIX: Implement the actual controller initialization call
            if (controller != null) {
                // All controllers are expected to have a method like:
                // initData(String userRole, String username)

                System.out.println("DEBUG: Initializing " + controllerTypeHint + " with Role: " + currentUserRole + ", Username: " + currentLoggedInUsername);

                // Use a switch-like structure or if-else chain to safely cast and call initData
                switch (controllerTypeHint) {
                    case "ReportController":
                        // Assuming ReportController has initData(String userRole, String username)
                        ((ReportController) controller).initData(this.currentUserRole, this.currentLoggedInUsername);
                        break;
                    case "SurveyController":
                        // Assuming SurveyController has initData(String userRole, String username)
                        ((SurveyController) controller).initData(this.currentUserRole, this.currentLoggedInUsername);
                        break;
                    case "UserController":
                        // Assuming UserController has initData(String userRole, String username)
                        ((UserController) controller).initData(this.currentUserRole, this.currentLoggedInUsername);
                        break;
                    case "SurveyTakerController":
                        // Assuming SurveyTakerController has initData(String userRole, String username)
                        ((SurveyTakerController) controller).initData(this.currentUserRole, this.currentLoggedInUsername);
                        break;
                    case "ProfileSettingsController":
                        // Assuming ProfileSettingsController has initData(String username, String userRole)
                        ((ProfileSettingsController) controller).initData(this.currentLoggedInUsername, this.currentUserRole);
                        break;
                    default:
                        System.err.println("WARNING: Unknown controller type or missing initData implementation: " + controllerTypeHint);
                        break;
                }
            }

            mainContentArea.getChildren().clear();
            mainContentArea.getChildren().add(view);

            AnchorPane.setTopAnchor(view, 0.0);
            AnchorPane.setBottomAnchor(view, 0.0);
            AnchorPane.setLeftAnchor(view, 0.0);
            AnchorPane.setRightAnchor(view, 0.0);

        } catch (IOException e) {
            System.err.println("Error loading FXML view: " + fxmlPath + ". " + e.getMessage());
            e.printStackTrace();
            loadErrorView("Could not load screen from " + fxmlPath);
        } catch (ClassCastException e) {
            System.err.println("Controller Type Mismatch: Failed to cast controller for " + controllerTypeHint + ". Ensure the controller is imported and linked correctly.");
            e.printStackTrace();
            loadErrorView("Controller initialization failed (Type Mismatch) for " + controllerTypeHint);
        } catch (Exception e) {
            System.err.println("Error initializing controller for view: " + fxmlPath + ". " + e.getMessage());
            e.printStackTrace();
            loadErrorView("Controller initialization failed for " + controllerTypeHint);
        }
    }

    /**
     * Placeholder method for deprecated paths. Now redirects to loadErrorView.
     */
    private void loadView(String fxmlPath, String controllerTypeHint) {
        // This method is now effectively deprecated and redirects to an error or the proper method.
        // It was causing issues when called by old button handlers.
        loadErrorView("Deprecated method 'loadView' called. Check button action handlers.");
    }

    /**
     * Displays a generic error message in the main content area.
     */
    private void loadErrorView(String message) {
        VBox errorBox = new VBox(20);
        errorBox.setStyle("-fx-alignment: center;");
        Label errorLabel = new Label("System Error: " + message);
        errorLabel.setFont(new Font("System", 18));
        errorBox.getChildren().add(errorLabel);

        mainContentArea.getChildren().add(errorBox);
        AnchorPane.setTopAnchor(errorBox, 0.0);
        AnchorPane.setBottomAnchor(errorBox, 0.0);
        AnchorPane.setLeftAnchor(errorBox, 0.0);
        AnchorPane.setRightAnchor(errorBox, 0.0);
    }
}
