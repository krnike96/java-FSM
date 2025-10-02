package com.fsm.controllers;

import com.fsm.database.MongoManager;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.stage.Stage;

import org.bson.Document;
import java.util.ArrayList;
import java.util.List;

public class QuestionBuilderController {
    public static class Question {
        private String id;
        private String text;
        private String type; // e.g., "TEXT", "SINGLE_CHOICE", "RATING"
        private List<String> options; // Used for MULTIPLE_CHOICE type

        public Question(String id, String text, String type, List<String> options) {
            this.id = id;
            this.text = text;
            this.type = type;
            this.options = (options != null) ? options : new ArrayList<>();
        }

        // Simple Getters (no setters needed for this transient model)
        public String getId() { return id; }
        public String getText() { return text; }
        public String getType() { return type; }
        public List<String> getOptions() { return options; }
    }
    // FXML elements will go here
    @FXML private Button btnSave;
    @FXML private Button btnCancel;

    // Reference to the parent controller and the survey being edited
    private SurveyController parentController;
    private SurveyController.Survey currentSurvey;
    private List<Question> currentQuestions = new ArrayList<>();

    // Use the fully qualified name for the inner class
    // This is the method called by SurveyController to pass data
    public void initData(SurveyController parent, SurveyController.Survey survey) {
        this.parentController = parent;
        this.currentSurvey = survey;

        System.out.println("Question Builder opened for survey: " + survey.getName());
        // Logic to load existing questions will go here
    }

    @FXML
    public void initialize() {
        // Initialization for FXML components goes here
        btnCancel.setOnAction(e -> closeWindow());
    }

    // Placeholder for saving the questions
    @FXML
    private void handleSaveQuestions() {
        currentQuestions.add(new Question("Q1", "How satisfied are you?", "RATING", null));
        currentQuestions.add(new Question("Q2", "What is your gender?", "SINGLE_CHOICE", List.of("Male", "Female", "Other")));
        // 1. Convert the Java List<Question> into a List<Document> for MongoDB
        List<Document> questionDocs = new ArrayList<>();
        for (Question q : currentQuestions) {
            Document qDoc = new Document("id", q.getId())
                    .append("text", q.getText())
                    .append("type", q.getType())
                    .append("options", q.getOptions());
            questionDocs.add(qDoc);
        }

        // 2. Perform the MongoDB update
        if (saveQuestionsToMongo(questionDocs)) {
            System.out.println("SUCCESS: Questions saved for survey: " + currentSurvey.getName());
        } else {
            System.err.println("ERROR: Failed to save questions to DB.");
        }

        // 3. Update the parent controller's table (optional, but good practice)
        if (parentController != null) {
            parentController.refreshTable();
        }
        closeWindow();
    }

    /**
     * Saves the list of questions as an array in the current survey document.
     * @param questionDocs List of BSON Documents representing questions.
     * @return true if update was successful.
     */
    private boolean saveQuestionsToMongo(List<Document> questionDocs) {
        MongoDatabase db = MongoManager.connect();
        if (db == null) return false;

        try {
            MongoCollection<Document> collection = db.getCollection("surveys");

            // Filter: Find the survey using its unique name (or _id in a production app)
            org.bson.conversions.Bson filter = Filters.eq("name", currentSurvey.getName());

            // Update: Set the 'questions' field to the new array of question documents
            org.bson.conversions.Bson update = Updates.set("questions", questionDocs);

            collection.updateOne(filter, update);
            return true;

        } catch (Exception e) {
            System.err.println("MongoDB Question Save Error: " + e.getMessage());
            return false;
        }
    }

    private void closeWindow() {
        Stage stage = (Stage) ((Button) btnCancel).getScene().getWindow(); // Assuming btnCancel is defined
        stage.close();
    }
}