package com.fsm.controllers;

import com.fsm.database.MongoManager; // We may need this later for data fetching
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.control.Label;
import javafx.scene.text.Font;

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

    // You can use an initialize method to set up the initial view or load data
    @FXML
    public void initialize() {
        // Load the default view (e.g., a simple Welcome screen) into the center
        loadWelcomeView();
        // Load the Surveys view immediately when the dashboard opens
        loadView("/com/fsm/survey-view.fxml");

        // Set up button handlers to call the dynamic loader
        btnSurveys.setOnAction(event -> loadView("/com/fsm/survey-view.fxml"));
        btnUsers.setOnAction(event -> loadView("Users")); // Placeholder for now
        btnReports.setOnAction(event -> loadView("Reports")); // Placeholder for now
        // ... and so on
    }

    private void loadWelcomeView() {
        VBox welcomeBox = new VBox(20);
        welcomeBox.setStyle("-fx-alignment: center;");
        Label welcomeLabel = new Label("Select an option from the sidebar to begin managing data.");
        welcomeLabel.setFont(new Font("System", 18));
        welcomeBox.getChildren().add(welcomeLabel);

        // Set this VBox as the content of the center of the BorderPane
        rootPane.setCenter(welcomeBox);
    }

    /**
     * Placeholder method to demonstrate loading content dynamically.
     */
    private void loadView(String fxmlPath) {
        if (fxmlPath.equals("Users") || fxmlPath.equals("Reports")) {
            // Placeholder logic for non-existent FXML files
            Label contentLabel = new Label("Loading " + fxmlPath + " Screen...");
            rootPane.setCenter(new VBox(20, contentLabel));
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent view = loader.load();
            rootPane.setCenter(view);
        } catch (IOException e) {
            e.printStackTrace();
            // Display error in the center pane if the FXML can't be found
            rootPane.setCenter(new Label("Error: Could not load view from " + fxmlPath));
        }
    }
}