package com.fsm.controllers;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCollection;
import com.mongodb.MongoException;
import org.bson.Document;
import com.fsm.database.MongoManager; // Keep this import

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
import org.bson.conversions.Bson;

public class SurveyController {

    // -----------------------------------------------------------
    // Nested Model Class
    // -----------------------------------------------------------
    public static class Survey {
        private String name;
        private String status;
        private int questions;
        private String creator; // New field to hold the creator's username

        public Survey(String name, String status, int questions, String creator) {
            this.name = name;
            this.status = status;
            this.questions = questions;
            this.creator = creator;
        }

        // Getters and Setters (REQUIRED for TableView)
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.setStatus(status); }
        public int getQuestions() { return questions; }
        public void setQuestions(int questions) { this.questions = questions; }
        public String getCreator() { return creator; } // New Getter
        public void setCreator(String creator) { this.creator = creator; } // New Setter
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

    private String currentUserRole;         // Stored for permission checks
    private String currentLoggedInUsername; // Stored for filtering surveys

    private final ObservableList<Survey> masterData = FXCollections.observableArrayList();

    /**
     * CRITICAL: Updated method to accept and store the logged-in username.
     * @param userRole The role of the currently logged-in user.
     * @param username The username of the currently logged-in user.
     */
    public void initData(String userRole, String username) {
        this.currentUserRole = userRole;
        this.currentLoggedInUsername = username;

        // 1. Apply Role-Based Access Control
        applyRoleRestrictions(userRole);

        // 2. Load the data (now filtered based on role/username)
        loadSurveyData();
    }

    /**
     * Hides or disables modification buttons for unauthorized users.
     */
    private void applyRoleRestrictions(String userRole) {
        // Only Administrators and Survey Creators can modify surveys.
        boolean canModify = "Administrator".equals(userRole) || "Survey Creator".equals(userRole);

        if (!canModify) {
            // Non-admin/non-creator roles (like Data Entry) lose all modification rights.
            btnAdd.setDisable(true);
            btnEdit.setDisable(true);
            btnDelete.setDisable(true);
            btnManageQuestions.setDisable(true);

            System.out.println("Survey view: Modification buttons disabled for role: " + userRole);
        }
    }


    @FXML
    public void initialize() {
        // 1. Link the model properties to the TableView columns
        colName.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getName()));
        colStatus.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getStatus()));
        colQuestions.setCellValueFactory(cellData -> new SimpleIntegerProperty(cellData.getValue().getQuestions()).asObject());

        // 2. Set up button actions
        btnAdd.setOnAction(event -> handleAddSurvey());
        btnEdit.setOnAction(event -> handleEditSurvey());
        btnDelete.setOnAction(event -> handleDeleteSurvey());
        btnManageQuestions.setOnAction(event -> handleManageQuestions());

        // NOTE: loadSurveyData() is removed from here and moved to initData()
    }

    /**
     * FIX: Loads survey data, applying a filter if the user is a Survey Creator.
     */
    private void loadSurveyData() {
        masterData.clear();
        // PERFORMANCE FIX: Use the Singleton instance to get the shared database connection
        MongoDatabase db = MongoManager.getInstance().getDatabase();
        surveyTable.setItems(masterData); // Set the empty list immediately

        if (db == null) {
            System.err.println("Database connection failed. Cannot load survey data.");
            return;
        }

        try {
            MongoCollection<Document> surveyCollection = db.getCollection("surveys");

            // --- CRITICAL FILTERING LOGIC ---
            Bson filter = new Document(); // Start with an empty filter (shows all)

            if ("Survey Creator".equals(currentUserRole)) {
                // If user is a Survey Creator, filter by the 'creator' field matching the logged-in username
                filter = Filters.eq("creator", currentLoggedInUsername);
                System.out.println("Survey Creator view: Filtering surveys for creator: " + currentLoggedInUsername);
            } else if ("Administrator".equals(currentUserRole)) {
                System.out.println("Admin view: Showing all surveys.");
                // No filter needed, already an empty Document()
            } else {
                // Default/Data Entry: Show nothing, or only public surveys (assuming Data Entry sees none here)
                filter = Filters.eq("_id", null); // Filter that matches nothing
                System.out.println("Data Entry view: Showing no surveys.");
            }
            // --- END FILTERING LOGIC ---


            for (Document doc : surveyCollection.find(filter)) {
                String name = doc.getString("name");
                String status = doc.getString("status");
                int numQuestions = doc.getInteger("numQuestions", 0);
                String creator = doc.getString("creator"); // Ensure this field exists in your DB documents

                masterData.add(new Survey(name, status, numQuestions, creator));
            }

        } catch (MongoException e) {
            System.err.println("Error loading survey data from MongoDB: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("General error loading survey data: " + e.getMessage());
        }
    }

    // --- Button Handlers (Needs access control for edit/delete) ---

    private void handleAddSurvey() {
        if (!("Administrator".equals(currentUserRole) || "Survey Creator".equals(currentUserRole))) {
            showAlert(AlertType.ERROR, "Permission Denied", "Your role does not permit creating new surveys.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/fsm/add-survey-form.fxml"));
            Parent root = loader.load();

            AddSurveyController addController = loader.getController();

            // Pass the current logged-in user to stamp the survey creator
            addController.setCreatorUsername(currentLoggedInUsername);

            addController.setParentController(this);

            Stage stage = new Stage();
            stage.setTitle("Add New Survey");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Error loading Add Survey form: " + e.getMessage());
        }
    }

    private void handleEditSurvey() {
        Survey selectedSurvey = surveyTable.getSelectionModel().getSelectedItem();

        if (selectedSurvey == null) {
            showAlert(AlertType.WARNING, "No Selection", "Please select a survey from the table to edit.");
            return;
        }

        // --- Access Control Check for Edit ---
        if ("Survey Creator".equals(currentUserRole) && !selectedSurvey.getCreator().equals(currentLoggedInUsername)) {
            showAlert(AlertType.ERROR, "Permission Denied", "You can only edit surveys that you have created.");
            return;
        }


        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/fsm/add-survey-form.fxml"));
            Parent root = loader.load();

            AddSurveyController editController = loader.getController();

            // Pass the current logged-in user to prevent editing other people's surveys
            editController.setCreatorUsername(currentLoggedInUsername);

            editController.initData(this, selectedSurvey);

            Stage stage = new Stage();
            stage.setTitle("Edit Survey: " + selectedSurvey.getName());
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Error loading Edit Survey form: " + e.getMessage());
        }
    }

    private void handleDeleteSurvey() {
        Survey selectedSurvey = surveyTable.getSelectionModel().getSelectedItem();

        if (selectedSurvey == null) {
            showAlert(AlertType.WARNING, "No Selection", "Please select a survey from the table to delete.");
            return;
        }

        // --- Access Control Check for Delete ---
        if ("Survey Creator".equals(currentUserRole) && !selectedSurvey.getCreator().equals(currentLoggedInUsername)) {
            showAlert(AlertType.ERROR, "Permission Denied", "You can only delete surveys that you have created.");
            return;
        }

        Alert confirmAlert = new Alert(AlertType.CONFIRMATION);
        confirmAlert.setTitle("Confirm Deletion");
        confirmAlert.setHeaderText("Permanently Delete Survey?");
        confirmAlert.setContentText("Are you sure you want to delete the survey: " + selectedSurvey.getName() + "? This cannot be undone.");

        Optional<ButtonType> result = confirmAlert.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.OK) {
            if (deleteSurveyFromMongo(selectedSurvey.getName())) {
                refreshTable();
                System.out.println("SUCCESS: Survey deleted: " + selectedSurvey.getName());
            } else {
                showAlert(AlertType.ERROR, "Deletion Failed", "Failed to delete survey from the database.");
            }
        }
    }

    // The handleManageQuestions should also check permission
    private void handleManageQuestions() {
        Survey selectedSurvey = surveyTable.getSelectionModel().getSelectedItem();

        if (selectedSurvey == null) {
            showAlert(AlertType.WARNING, "No Selection", "Please select a survey to manage its questions.");
            return;
        }

        // --- Access Control Check for Question Management ---
        if ("Survey Creator".equals(currentUserRole) && !selectedSurvey.getCreator().equals(currentLoggedInUsername)) {
            showAlert(AlertType.ERROR, "Permission Denied", "You can only manage questions for surveys that you have created.");
            return;
        }

        loadQuestionBuilderModal(selectedSurvey);
    }


    /**
     * Executes the MongoDB delete operation.
     */
    private boolean deleteSurveyFromMongo(String surveyName) {
        // PERFORMANCE FIX: Use the Singleton instance to get the shared database connection
        MongoDatabase db = MongoManager.getInstance().getDatabase();
        if (db == null) {
            return false; // Connection failed
        }

        try {
            MongoCollection<Document> collection = db.getCollection("surveys");
            collection.deleteOne(Filters.eq("name", surveyName));
            return true;
        } catch (MongoException e) {
            System.err.println("MongoDB Delete Error: " + e.getMessage());
            return false;
        }
    }

    private void loadQuestionBuilderModal(Survey survey) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/fsm/question-builder-view.fxml"));
            Parent root = loader.load();

            // NOTE: Assuming you have a QuestionBuilderController class
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

    private void showAlert(AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
