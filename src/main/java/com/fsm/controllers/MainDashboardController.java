package com.fsm.controllers;

import com.fsm.MainApplication; // Import MainApplication
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.control.Label;
import javafx.scene.text.Font;
import javafx.scene.layout.AnchorPane;
import javafx.scene.control.MenuItem; // Import MenuItem
import javafx.application.Platform; // Import Platform for Exit

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import java.io.IOException;

public class MainDashboardController {

    @FXML private BorderPane rootPane;
    @FXML private Button btnSurveys;
    @FXML private Button btnUsers;
    @FXML private Button btnReports;
    @FXML private Button btnSettings;
    @FXML private AnchorPane mainContentArea;

    // NEW: Inject MenuItems from FXML
    @FXML private MenuItem menuItemLogout;
    @FXML private MenuItem menuItemExit;

    // --- Fields to store logged-in user data ---
    private String currentUserRole;
    private String currentLoggedInUsername;

    @FXML
    public void initialize() {
        btnSurveys.setOnAction(event -> loadSurveyDecisionView());
        btnUsers.setOnAction(event -> loadUserView());
        btnReports.setOnAction(e -> loadView("/com/fsm/report-view.fxml", "ReportController"));
        // Update: Call dedicated loadProfileSettingsView instead of generic loadView
        btnSettings.setOnAction(e -> loadProfileSettingsView());
    }

    // --- NEW: Handle Logout and Exit ---

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

    // --- End NEW: Handle Logout and Exit ---


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

        // FIX 1: Settings button should be visible for ALL users for self-service profile management.
        boolean isSettingsUser = true; // All users can access settings

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
        if (!btnUsers.isVisible()) return;
        // Pass both role and username to the UserController for security checks
        loadViewWithData("/com/fsm/user-view.fxml", "UserController");
    }

    /**
     * NEW: Loads the Profile Settings view for users to change their own password/username.
     */
    private void loadProfileSettingsView() {
        if (!btnSettings.isVisible()) return;
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

            if (controller != null) {
                if ("ReportController".equals(controllerTypeHint)) {
                    // Initialize ReportController
                    System.out.println("DEBUG: Initializing ReportController with Role: " + currentUserRole + ", Username: " + currentLoggedInUsername);
                    ((ReportController) controller).initData(this.currentUserRole, this.currentLoggedInUsername);
                } else if ("SurveyController".equals(controllerTypeHint)) {
                    // Pass BOTH role AND username to the SurveyController
                    ((SurveyController) controller).initData(this.currentUserRole, this.currentLoggedInUsername);
                } else if ("UserController".equals(controllerTypeHint)) {
                    // Pass BOTH role AND username to UserController
                    ((UserController) controller).initData(this.currentUserRole, this.currentLoggedInUsername);
                } else if ("SurveyTakerController".equals(controllerTypeHint)) {
                    // Pass BOTH role AND username to SurveyTakerController
                    ((SurveyTakerController) controller).initData(this.currentUserRole, this.currentLoggedInUsername);
                } else if ("ProfileSettingsController".equals(controllerTypeHint)) {
                    // NEW: Pass BOTH role AND username to the ProfileSettingsController
                    ((ProfileSettingsController) controller).initData(this.currentLoggedInUsername, this.currentUserRole);
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
        } catch (Exception e) {
            System.err.println("Error initializing controller for view: " + fxmlPath + ". " + e.getMessage());
            e.printStackTrace();
            loadErrorView("Controller initialization failed for " + controllerTypeHint);
        }
    }

    /**
     * Placeholder method used for Reports and Settings views (handled as 'coming soon').
     */
    private void loadView(String fxmlPath, String controllerTypeHint) {
        if (fxmlPath.contains("settings-view.fxml")) {
            // The functionality has been moved to loadProfileSettingsView()
            loadErrorView("The old settings-view.fxml path is deprecated. Please use btnSettings.");
            return;
        }

        // Use the unified loading method for generic paths like Reports
        loadViewWithData(fxmlPath, controllerTypeHint);
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
