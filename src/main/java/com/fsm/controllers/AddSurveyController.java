package com.fsm.controllers;

import com.fsm.database.MongoManager;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCollection;
import org.bson.Document;

import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Button;
import javafx.stage.Stage;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import static com.fsm.controllers.SurveyController.Survey;

public class AddSurveyController {

    @FXML private TextField txtSurveyName;
    @FXML private ComboBox<String> cmbStatus;
    @FXML private Button btnSave;
    @FXML private Button btnCancel;

    private SurveyController parentController;
    private String originalSurveyName;

    // CRITICAL FIX: Field to store the username of the person creating/editing the survey
    private String creatorUsername;

    @FXML
    public void initialize() {
        // Initialize the status ComboBox options
        cmbStatus.getItems().addAll("Draft", "Active", "Archived");
        cmbStatus.setValue("Draft"); // Set a default value

        btnSave.setOnAction(e -> handleSave());
        btnCancel.setOnAction(e -> handleCancel());
    }

    /**
     * FIX: Setter method to receive the current logged-in username from SurveyController.
     * This resolves the "cannot resolve method" error.
     */
    public void setCreatorUsername(String username) {
        this.creatorUsername = username;
        System.out.println("AddSurveyController received creator username: " + username);
    }

    public void initData(SurveyController parent, Survey surveyToEdit) {
        this.parentController = parent;

        // If a survey is passed, enter EDIT mode
        if (surveyToEdit != null) {
            this.originalSurveyName = surveyToEdit.getName();

            // Set the creatorUsername from the existing survey data
            // This ensures we don't accidentally overwrite the creator in EDIT mode
            this.creatorUsername = surveyToEdit.getCreator();

            // 1. Pre-fill the form fields
            txtSurveyName.setText(surveyToEdit.getName());
            cmbStatus.setValue(surveyToEdit.getStatus());

            // 2. Change the window title (in the next step's FXML loading)
            btnSave.setText("Update Survey");
        } else {
            // If null, it's ADD mode (existing logic still applies)
            this.parentController = parent;
        }
    }

    // Method to be called by SurveyController to set the parent reference
    public void setParentController(SurveyController controller) {
        this.parentController = controller;
    }

    private void handleSave() {
        String name = txtSurveyName.getText().trim();
        String status = cmbStatus.getValue();

        if (name.isEmpty()) {
            System.err.println("Error: Survey Name cannot be empty.");
            return;
        }

        // Check if we are in EDIT mode or ADD mode
        if (originalSurveyName != null) {
            // EDIT MODE
            // The original creatorUsername is retained from initData
            if (updateSurveyInMongo(name, status)) {
                System.out.println("Survey updated successfully: " + name);
            }
        } else {
            // ADD MODE
            int numQuestions = 0;
            Document surveyDoc = new Document()
                    .append("name", name)
                    .append("status", status)
                    .append("numQuestions", numQuestions)
                    // CRITICAL FIX: Append the creator's username when creating a new survey
                    .append("creator", creatorUsername)
                    .append("dateCreated", new java.util.Date());

            if (insertSurveyIntoMongo(surveyDoc)) {
                System.out.println("Survey saved successfully: " + name);
            }
        }

        // Final steps are the same for both
        if (parentController != null) {
            parentController.refreshTable();
        }
        closeWindow();
    }

    private boolean updateSurveyInMongo(String newName, String newStatus) {
        MongoDatabase db = MongoManager.connect();
        if (db == null) {
            System.err.println("FATAL: Cannot connect to DB to update survey.");
            return false;
        }
        try {
            MongoCollection<Document> collection = db.getCollection("surveys");

            // 1. Define the filter (find the document by its ORIGINAL name)
            org.bson.conversions.Bson filter = Filters.eq("name", originalSurveyName);

            // 2. Define the updates
            // We do NOT update the 'creator' field in EDIT mode.
            org.bson.conversions.Bson updates = Updates.combine(
                    Updates.set("name", newName),
                    Updates.set("status", newStatus)
            );

            // 3. Perform the update
            collection.updateOne(filter, updates);

            return true;
        } catch (Exception e) {
            System.err.println("MongoDB Update Error: " + e.getMessage());
            return false;
        }
    }

    private boolean insertSurveyIntoMongo(Document doc) {
        MongoDatabase db = MongoManager.connect();
        if (db == null) {
            System.err.println("FATAL: Cannot connect to DB to save survey.");
            return false;
        }
        try {
            MongoCollection<Document> collection = db.getCollection("surveys");
            collection.insertOne(doc);
            return true;
        } catch (Exception e) {
            System.err.println("MongoDB Insert Error: " + e.getMessage());
            return false;
        }
    }

    private void handleCancel() {
        closeWindow();
    }

    private void closeWindow() {
        // Get the stage (window) from the save button
        Stage stage = (Stage) btnCancel.getScene().getWindow();
        stage.close();
    }
}
