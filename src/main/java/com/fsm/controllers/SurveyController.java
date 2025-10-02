package com.fsm.controllers;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCollection;
import com.mongodb.MongoException;
import org.bson.Document;
import com.fsm.database.MongoManager;

import javafx.fxml.FXML;
import javafx.scene.control.TableView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.SimpleIntegerProperty;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import java.io.IOException;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import com.mongodb.client.model.Filters;
import java.util.Optional;

// Note: No class declaration here

public class SurveyController {

    // -----------------------------------------------------------
    // FIX: Nested Model Class
    // The model must be a static class nested *inside* the controller
    // to be placed in the same file without the "public class in separate file" error.
    // -----------------------------------------------------------
    public static class Survey {
        private String name;
        private String status;
        private int questions;

        public Survey(String name, String status, int questions) {
            this.name = name;
            this.status = status;
            this.questions = questions;
        }

        // Getters and Setters (REQUIRED for TableView)
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public int getQuestions() { return questions; }
        public void setQuestions(int questions) { this.questions = questions; }
    }
    // -----------------------------------------------------------


    @FXML private VBox surveyViewContainer;
    @FXML private TableView<Survey> surveyTable;
    @FXML private TableColumn<Survey, String> colName;
    @FXML private TableColumn<Survey, String> colStatus;
    @FXML private TableColumn<Survey, Integer> colQuestions;
    @FXML private Button btnAdd;
    @FXML private Button btnEdit;
    @FXML private Button btnDelete;
    @FXML private Button btnManageQuestions;

    // Use the nested Survey class
    private final ObservableList<Survey> masterData = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        // 1. Link the model properties to the TableView columns
        colName.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getName()));
        colStatus.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getStatus()));
        colQuestions.setCellValueFactory(cellData -> new SimpleIntegerProperty(cellData.getValue().getQuestions()).asObject());

        // 2. Load data from MongoDB
        loadSurveyData();

        // 3. Set up button actions
        btnAdd.setOnAction(event -> handleAddSurvey());
        btnEdit.setOnAction(event -> handleEditSurvey());
        btnDelete.setOnAction(event -> handleDeleteSurvey());
        btnManageQuestions.setOnAction(event -> handleManageQuestions());
    }

    private void loadSurveyData() {
        masterData.clear();
        MongoDatabase db = MongoManager.connect();

        if (db == null) {
            System.err.println("Database connection failed. Cannot load survey data.");
            return;
        }

        try {
            MongoCollection<Document> surveyCollection = db.getCollection("surveys");

            for (Document doc : surveyCollection.find()) {
                String name = doc.getString("name");
                String status = doc.getString("status");
                int numQuestions = doc.getInteger("numQuestions", 0);

                masterData.add(new Survey(name, status, numQuestions));
            }

            surveyTable.setItems(masterData);

        } catch (MongoException e) {
            System.err.println("Error loading survey data from MongoDB: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("General error loading survey data: " + e.getMessage());
        }
    }

    // --- Button Handlers ---

    private void handleAddSurvey() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/fsm/add-survey-form.fxml"));
            Parent root = loader.load();

            // Get the controller instance of the new form
            AddSurveyController addController = loader.getController();

            // Pass a reference to THIS controller, so the form can call refreshTable()
            addController.setParentController(this);

            // Create the new modal stage (window)
            Stage stage = new Stage();
            stage.setTitle("Add New Survey");

            // Modality ensures the user must interact with this window before returning to the dashboard
            stage.initModality(Modality.APPLICATION_MODAL);

            stage.setScene(new Scene(root));
            stage.showAndWait(); // Wait until the form is closed

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Error loading Add Survey form: " + e.getMessage());
        }
    }

    private void handleEditSurvey() {
        Survey selectedSurvey = surveyTable.getSelectionModel().getSelectedItem();

        if (selectedSurvey == null) {
            // Use the same warning as delete if nothing is selected
            Alert alert = new Alert(AlertType.WARNING);
            alert.setTitle("No Selection");
            alert.setHeaderText("No Survey Selected");
            alert.setContentText("Please select a survey from the table to edit.");
            alert.showAndWait();
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/fsm/add-survey-form.fxml"));
            Parent root = loader.load();

            AddSurveyController editController = loader.getController();

            // CRITICAL STEP: Pass the selected survey data to the controller
            editController.initData(this, selectedSurvey);

            // Create the new modal stage (window)
            Stage stage = new Stage();
            stage.setTitle("Edit Survey: " + selectedSurvey.getName()); // Set dynamic title
            stage.initModality(Modality.APPLICATION_MODAL);

            stage.setScene(new Scene(root));
            stage.showAndWait(); // Wait until the form is closed

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Error loading Edit Survey form: " + e.getMessage());
        }
    }

    private void handleDeleteSurvey() {
        Survey selectedSurvey = surveyTable.getSelectionModel().getSelectedItem();

        if (selectedSurvey == null) {
            // Show a simple warning if nothing is selected
            Alert alert = new Alert(AlertType.WARNING);
            alert.setTitle("No Selection");
            alert.setHeaderText("No Survey Selected");
            alert.setContentText("Please select a survey from the table to delete.");
            alert.showAndWait();
            return;
        }

        // 1. Show Confirmation Dialog
        Alert confirmAlert = new Alert(AlertType.CONFIRMATION);
        confirmAlert.setTitle("Confirm Deletion");
        confirmAlert.setHeaderText("Permanently Delete Survey?");
        confirmAlert.setContentText("Are you sure you want to delete the survey: " + selectedSurvey.getName() + "? This cannot be undone.");

        Optional<ButtonType> result = confirmAlert.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.OK) {
            // 2. Perform MongoDB Deletion
            if (deleteSurveyFromMongo(selectedSurvey.getName())) {
                // 3. Refresh the Table
                refreshTable();
                System.out.println("SUCCESS: Survey deleted: " + selectedSurvey.getName());
            } else {
                // Display error if DB operation fails
                Alert errorAlert = new Alert(AlertType.ERROR);
                errorAlert.setTitle("Deletion Failed");
                errorAlert.setHeaderText("Database Error");
                errorAlert.setContentText("Failed to delete survey from the database.");
                errorAlert.showAndWait();
            }
        }
    }

    /**
     * Executes the MongoDB delete operation.
     * @param surveyName The name (unique identifier) of the survey to delete.
     * @return true if deletion was successful, false otherwise.
     */
    private boolean deleteSurveyFromMongo(String surveyName) {
        MongoDatabase db = MongoManager.connect();
        if (db == null) {
            return false; // Connection failed
        }

        try {
            MongoCollection<Document> collection = db.getCollection("surveys");

            // Delete the document where the 'name' field matches the selected survey's name
            collection.deleteOne(Filters.eq("name", surveyName));

            // Note: For production, you would use a unique ID (like _id) for deletion,
            // but using 'name' works for now since it's unique in our test data.
            return true;

        } catch (MongoException e) {
            System.err.println("MongoDB Delete Error: " + e.getMessage());
            return false;
        }
    }

    private void handleManageQuestions() {
        SurveyController.Survey selectedSurvey = surveyTable.getSelectionModel().getSelectedItem();

        if (selectedSurvey == null) {
            Alert alert = new Alert(AlertType.WARNING);
            alert.setTitle("No Selection");
            alert.setHeaderText("No Survey Selected");
            alert.setContentText("Please select a survey to manage its questions.");
            alert.showAndWait();
            return;
        }

        // Pass the selected survey to the new Question Builder Controller
        // We will implement loadQuestionBuilderModal in the next step
        loadQuestionBuilderModal(selectedSurvey);
    }


    private void loadQuestionBuilderModal(SurveyController.Survey survey) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/fsm/question-builder-view.fxml"));
            Parent root = loader.load();

            QuestionBuilderController builderController = loader.getController();

            // Pass the selected survey object and the parent controller reference
            builderController.initData(this, survey);

            Stage stage = new Stage();
            stage.setTitle("Question Builder: " + survey.getName());
            stage.initModality(Modality.APPLICATION_MODAL);

            stage.setScene(new Scene(root));
            stage.showAndWait();

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Error loading Question Builder form: " + e.getMessage());
        }
    }

    public void refreshTable() {
        loadSurveyData();
    }
}