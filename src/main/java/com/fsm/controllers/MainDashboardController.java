package com.fsm.controllers;

import com.fsm.database.MongoManager;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.control.Label;
import javafx.scene.text.Font;
import javafx.scene.layout.AnchorPane;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import java.io.IOException;

public class MainDashboardController {

    // Inject the main BorderPane so we can load new content into its center
    @FXML
    private BorderPane rootPane;

    // Inject the navigation buttons
    @FXML private Button btnSurveys;
    @FXML private Button btnUsers;
    @FXML private Button btnReports;
    @FXML private Button btnSettings;
    @FXML private AnchorPane mainContentArea;

    private String currentUserRole;

    // You can use an initialize method to set up the initial view or load data
    @FXML
    public void initialize() {
        // 2. Setup button handlers.
        // CRITICAL FIX: Use the dedicated decision method to load the correct view based on role
        btnSurveys.setOnAction(event -> loadSurveyDecisionView());

        // We will call the dedicated loadUserView to apply restrictions inside that controller
        btnUsers.setOnAction(event -> loadUserView());

        btnReports.setOnAction(e -> loadView("/com/fsm/report-view.fxml"));
        btnSettings.setOnAction(e -> loadView(null)); // Use null to show "Coming Soon"
    }

    public void initData(String userRole) {
        this.currentUserRole = userRole;
        applyRoleRestrictions();
    }

    /**
     * Checks the user's role and disables unauthorized navigation buttons.
     */
    private void applyRoleRestrictions() {
        // We will use visibility to hide unauthorized buttons for a cleaner UI
        boolean isAdmin = "Administrator".equals(currentUserRole);

        // USERS, REPORTS, and SETTINGS are restricted to Administrator
        btnUsers.setVisible(isAdmin);
        btnUsers.setDisable(!isAdmin);

        btnReports.setVisible(isAdmin);
        btnReports.setDisable(!isAdmin); // Disable just in case it's still visible

        btnSettings.setVisible(isAdmin);
        btnSettings.setDisable(!isAdmin);
    }

    /**
     * Determines which 'Surveys' view to load based on the user's role.
     * This replaces the old loadSurveyView() method.
     */
    private void loadSurveyDecisionView() {
        // Data Entry users get the survey taking interface.
        if ("Data Entry".equals(currentUserRole)) {
            // We load the SurveyTakerController, which will also need the user's role
            // for the submission logic (tracking who submitted the response).
            loadViewWithRole("/com/fsm/survey-taker-view.fxml", "SurveyTakerController");
        } else {
            // Admins and Survey Creators get the management interface.
            loadViewWithRole("/com/fsm/survey-view.fxml", "SurveyController");
        }
    }


    // NEW METHOD: Loads User view and initializes it with the user's role
    private void loadUserView() {
        // Only proceed with loading the view if the button is NOT disabled by RBAC
        if (btnUsers.isDisable()) {
            System.out.println("Access Denied: Non-administrator attempted to access User Management.");
            return;
        }

        loadViewWithRole("/com/fsm/user-view.fxml", "UserController");
    }

    /**
     * A unified method for loading FXML views and initializing their controllers
     * with the current user role.
     * @param fxmlPath The path to the FXML file.
     * @param controllerTypeHint Optional hint for the controller class name (e.g., "SurveyController").
     */
    private void loadViewWithRole(String fxmlPath, String controllerTypeHint) {
        try {
            // 1. Load the FXML resource
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent view = loader.load();

            // 2. Pass the role to the controller if it's a known type
            Object controller = loader.getController();

            if (controller != null && currentUserRole != null) {
                if ("SurveyController".equals(controllerTypeHint)) {
                    ((SurveyController) controller).initData(this.currentUserRole);
                } else if ("UserController".equals(controllerTypeHint)) {
                    ((UserController) controller).initData(this.currentUserRole);
                } else if ("SurveyTakerController".equals(controllerTypeHint)) {
                    // We need to pass the role and potentially the username to the new controller
                    // for tracking who submits the response.
                    ((SurveyTakerController) controller).initData(this.currentUserRole);
                }
            }

            // 3. Clear previous content and add the new view
            mainContentArea.getChildren().clear();
            mainContentArea.getChildren().add(view);

            // 4. Anchor the new view to fill the entire AnchorPane (important!)
            AnchorPane.setTopAnchor(view, 0.0);
            AnchorPane.setBottomAnchor(view, 0.0);
            AnchorPane.setLeftAnchor(view, 0.0);
            AnchorPane.setRightAnchor(view, 0.0);

        } catch (IOException e) {
            System.err.println("Error loading FXML view: " + fxmlPath + ". " + e.getMessage());
            e.printStackTrace();
            mainContentArea.getChildren().clear();
            mainContentArea.getChildren().add(new Label("Error: Could not load screen from " + fxmlPath));
        }
    }

    // The legacy loadSurveyView is now replaced by loadSurveyDecisionView
    private void loadSurveyView() {
        loadSurveyDecisionView();
    }


    private void loadWelcomeView() {
        VBox welcomeBox = new VBox(20);
        welcomeBox.setStyle("-fx-alignment: center;");
        Label welcomeLabel = new Label("Select an option from the sidebar to begin managing data.");
        welcomeLabel.setFont(new Font("System", 18));
        welcomeBox.getChildren().add(welcomeLabel);

        // CRITICAL: Load the VBox content directly into the AnchorPane
        mainContentArea.getChildren().clear();
        mainContentArea.getChildren().add(welcomeBox);

        // Ensure the welcome content is anchored correctly within the AnchorPane
        AnchorPane.setTopAnchor(welcomeBox, 0.0);
        AnchorPane.setBottomAnchor(welcomeBox, 0.0);
        AnchorPane.setLeftAnchor(welcomeBox, 0.0);
        AnchorPane.setRightAnchor(welcomeBox, 0.0);
    }

    public void loadDefaultView() {
        // Load the appropriate survey view on startup based on the role
        loadSurveyDecisionView();
    }

    /**
     * Placeholder method to demonstrate loading content dynamically.
     * Used for Reports and Settings views, and as a fallback for Users.
     */
    private void loadView(String fxmlPath) {
        if (fxmlPath == null) {
            // Clear content if a null path is passed (e.g., for a TO-DO button)
            mainContentArea.getChildren().clear();
            mainContentArea.getChildren().add(new Label("Feature Coming Soon!"));
            return;
        }

        // Use the unified loading method for generic paths
        loadViewWithRole(fxmlPath, null);
    }
}
