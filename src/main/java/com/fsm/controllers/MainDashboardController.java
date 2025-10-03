package com.fsm.controllers;

import com.fsm.database.MongoManager; // We may need this later for data fetching
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
        // CRITICAL FIX: Use the dedicated loadSurveyView to pass the role
        btnSurveys.setOnAction(event -> loadSurveyView());

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

    // NEW METHOD: Loads User view and initializes it with the user's role
    private void loadUserView() {
        try {
            // Only proceed with loading the view if the button is NOT disabled by RBAC
            if (btnUsers.isDisable()) {
                System.out.println("Access Denied: Non-administrator attempted to access User Management.");
                return;
            }

            // 1. Load the FXML resource
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/fsm/user-view.fxml"));
            Parent view = loader.load();

            // Get the UserController instance
            UserController userController = loader.getController();

            // CRITICAL STEP: Pass the role to the user controller for *internal* button restriction
            if (userController != null && currentUserRole != null) {
                // Assuming UserController has an initData method like SurveyController
                userController.initData(this.currentUserRole);
            }

            // 2. Clear previous content
            mainContentArea.getChildren().clear();

            // 3. Add the new view
            mainContentArea.getChildren().add(view);

            // 4. Anchor the new view to fill the entire AnchorPane (important!)
            AnchorPane.setTopAnchor(view, 0.0);
            AnchorPane.setBottomAnchor(view, 0.0);
            AnchorPane.setLeftAnchor(view, 0.0);
            AnchorPane.setRightAnchor(view, 0.0);

        } catch (IOException e) {
            System.err.println("Error loading User view: " + e.getMessage());
            e.printStackTrace();

            // Display user-friendly error
            mainContentArea.getChildren().clear();
            mainContentArea.getChildren().add(new Label("Error: Could not load User Management screen."));
        } catch (Exception e) {
            // Handle potential issues if UserController is missing or initData is not implemented yet
            System.err.println("General error during User View load: " + e.getMessage());
            loadView("/com/fsm/user-view.fxml"); // Fallback to generic load
        }
    }

    // CRITICAL NEW METHOD: Loads Survey view and initializes it with the user's role
    private void loadSurveyView() {
        try {
            // 1. Load the FXML resource
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/fsm/survey-view.fxml"));
            Parent view = loader.load();

            // Get the SurveyController instance
            SurveyController surveyController = loader.getController();

            // CRITICAL STEP: Pass the role to the survey controller for button restriction
            if (surveyController != null && currentUserRole != null) {
                surveyController.initData(this.currentUserRole);
            }

            // 2. Clear previous content
            mainContentArea.getChildren().clear();

            // 3. Add the new view
            mainContentArea.getChildren().add(view);

            // 4. Anchor the new view to fill the entire AnchorPane (important!)
            AnchorPane.setTopAnchor(view, 0.0);
            AnchorPane.setBottomAnchor(view, 0.0);
            AnchorPane.setLeftAnchor(view, 0.0);
            AnchorPane.setRightAnchor(view, 0.0);

        } catch (IOException e) {
            System.err.println("Error loading Survey view: " + e.getMessage());
            e.printStackTrace();

            // Display user-friendly error
            mainContentArea.getChildren().clear();
            mainContentArea.getChildren().add(new Label("Error: Could not load Survey Management screen."));
        }
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
        // We want the Surveys view to load first, not the welcome message.
        loadSurveyView();
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

        try {
            // 1. Load the FXML resource
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent view = loader.load();

            // NOTE: If this loadView is called for UserController, it won't pass the role.
            // We use the dedicated loadUserView() now for better control.

            // 2. Clear previous content
            mainContentArea.getChildren().clear();

            // 3. Add the new view
            mainContentArea.getChildren().add(view);

            // 4. Anchor the new view to fill the entire AnchorPane (important!)
            AnchorPane.setTopAnchor(view, 0.0);
            AnchorPane.setBottomAnchor(view, 0.0);
            AnchorPane.setLeftAnchor(view, 0.0);
            AnchorPane.setRightAnchor(view, 0.0);

        } catch (IOException e) {
            System.err.println("Error loading FXML view: " + fxmlPath);
            e.printStackTrace();

            // Display user-friendly error
            mainContentArea.getChildren().clear();
            mainContentArea.getChildren().add(new Label("Error: Could not load screen from " + fxmlPath));
        }
    }
}
