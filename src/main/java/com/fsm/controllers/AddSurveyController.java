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

public class AddSurveyController {

    @FXML private TextField txtSurveyName;
    @FXML private ComboBox<String> cmbStatus;
    @FXML private TextField txtNumQuestions;
    @FXML private Button btnSave;
    @FXML private Button btnCancel;

    // An interface to communicate back to the main SurveyController
    private SurveyController parentController;

    @FXML
    public void initialize() {
        // Initialize the status ComboBox options
        cmbStatus.getItems().addAll("Draft", "Active", "Archived");
        cmbStatus.setValue("Draft"); // Set a default value

        btnSave.setOnAction(e -> handleSave());
        btnCancel.setOnAction(e -> handleCancel());
    }

    // Method to be called by SurveyController to set the parent reference
    public void setParentController(SurveyController controller) {
        this.parentController = controller;
    }

    private void handleSave() {
        String name = txtSurveyName.getText().trim();
        String status = cmbStatus.getValue();

        // Basic validation for the name field
        if (name.isEmpty()) {
            // In a real app, display an error label on the form
            System.err.println("Error: Survey Name cannot be empty.");
            return;
        }

        // Attempt to parse the number of questions, defaulting to 0 on error
        int numQuestions = 0;
        try {
            numQuestions = Integer.parseInt(txtNumQuestions.getText().trim());
        } catch (NumberFormatException e) {
            System.err.println("Warning: Invalid number of questions entered. Defaulting to 0.");
        }

        // 1. Create the MongoDB Document
        Document surveyDoc = new Document()
                .append("name", name)
                .append("status", status)
                .append("numQuestions", numQuestions)
                .append("dateCreated", new java.util.Date());

        // 2. Insert into the Database
        if (insertSurveyIntoMongo(surveyDoc)) {
            System.out.println("Survey saved successfully: " + name);

            // 3. Notify the parent to refresh the table
            if (parentController != null) {
                parentController.refreshTable();
            }

            // 4. Close the modal window
            closeWindow();
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