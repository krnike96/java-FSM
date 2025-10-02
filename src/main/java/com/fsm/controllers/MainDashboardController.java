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

    // You can use an initialize method to set up the initial view or load data
    @FXML
    public void initialize() {
        // 1. Load the WELCOME view as the initial content by using loadView's logic.
        loadWelcomeView(); // Call the updated method

        // 2. Setup button handlers.
        btnSurveys.setOnAction(event -> loadView("/com/fsm/survey-view.fxml"));
        btnUsers.setOnAction(event -> loadView("/com/fsm/user-view.fxml"));
        btnReports.setOnAction(e -> loadView("/com/fsm/report-view.fxml"));
        btnSettings.setOnAction(e -> loadView(null)); // Use null to show "Coming Soon"
    }

    private void loadWelcomeView() {
        // --- NO LONGER USES rootPane.setCenter() ---

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
        loadView("/com/fsm/survey-view.fxml");
    }

    /**
     * Placeholder method to demonstrate loading content dynamically.
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