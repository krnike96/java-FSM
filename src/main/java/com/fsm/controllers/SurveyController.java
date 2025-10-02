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
        if (selectedSurvey != null) {
            System.out.println("Edit Survey: " + selectedSurvey.getName());
        } else {
            System.out.println("No survey selected for editing.");
        }
    }

    private void handleDeleteSurvey() {
        Survey selectedSurvey = surveyTable.getSelectionModel().getSelectedItem();
        if (selectedSurvey != null) {
            System.out.println("Delete Survey: " + selectedSurvey.getName());
        } else {
            System.out.println("No survey selected for deletion.");
        }
    }

    public void refreshTable() {
        loadSurveyData();
    }
}