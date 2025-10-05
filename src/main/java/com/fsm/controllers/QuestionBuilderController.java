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

import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.ComboBox;
import javafx.scene.control.CheckBox; // ADDED: Import CheckBox
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import java.util.UUID;

public class QuestionBuilderController {
    public static class Question {
        private String id;
        private String text;
        private String type; // e.g., "TEXT", "SINGLE_CHOICE", "RATING"
        private List<String> options; // Used for MULTIPLE_CHOICE type
        private boolean isMandatory; // ADDED: Mandatory status

        public Question(String id, String text, String type, List<String> options, boolean isMandatory) { // UPDATED Constructor
            this.id = id;
            this.text = text;
            this.type = type;
            this.options = (options != null) ? options : new ArrayList<>();
            this.isMandatory = isMandatory; // Initialize new field
        }

        // Simple Getters (no setters needed for this transient model)
        public String getId() { return id; }
        public String getText() { return text; }
        public String getType() { return type; }
        public List<String> getOptions() { return options; }
        public boolean isMandatory() { return isMandatory; } // ADDED Getter
    }
    // FXML elements
    @FXML private Label lblSurveyName;
    @FXML private TextArea txtQuestionText;
    @FXML private ComboBox<String> cmbQuestionType;
    @FXML private CheckBox chkMandatory; // ADDED: Checkbox for mandatory status
    @FXML private VBox vboxDynamicOptions;
    @FXML private VBox vboxQuestionsList;
    @FXML private Button btnAddQuestion;
    @FXML private Button btnSave;
    @FXML private Button btnCancel;

    // Reference to the parent controller and the survey being edited
    private SurveyController parentController;
    private SurveyController.Survey currentSurvey;
    private List<Question> currentQuestions = new ArrayList<>();
    private int questionCounter = 0;

    // Use the fully qualified name for the inner class
    // This is the method called by SurveyController to pass data
    public void initData(SurveyController parent, SurveyController.Survey survey) {
        this.parentController = parent;
        this.currentSurvey = survey;

        lblSurveyName.setText("Question Builder: " + survey.getName());

        // 1. Load existing questions from DB when opening
        loadExistingQuestions(survey.getName());

        // 2. Populate the UI with the loaded questions
        redrawQuestionsList();
    }

    @FXML
    public void initialize() {
        // Initialize the Question Type ComboBox
        cmbQuestionType.getItems().addAll("TEXT_INPUT", "SINGLE_CHOICE", "RATING_SCALE");
        cmbQuestionType.setValue("TEXT_INPUT");

        // Setup Button Handlers
        btnCancel.setOnAction(e -> closeWindow());
        btnAddQuestion.setOnAction(e -> handleAddQuestion());

        // Setup listener to dynamically change input based on selected type
        cmbQuestionType.valueProperty().addListener((obs, oldVal, newVal) -> updateDynamicOptions(newVal));

        // Initialize dynamic options area (default to TEXT_INPUT)
        updateDynamicOptions("TEXT_INPUT");
    }

    //Handles the logic for dynamic UI based on question type
    private void updateDynamicOptions(String type) {
        vboxDynamicOptions.getChildren().clear();

        if ("SINGLE_CHOICE".equals(type)) {
            Label label = new Label("Options (comma-separated):");
            TextArea optionsArea = new TextArea();
            optionsArea.setPromptText("Option 1, Option 2, Option 3...");
            optionsArea.setPrefHeight(50);
            optionsArea.setId("optionsInput"); // Use an ID to easily retrieve the value later
            vboxDynamicOptions.getChildren().addAll(label, optionsArea);
        }
    }

    //Handles adding a question from the input panel to the currentQuestions list
    private void handleAddQuestion() {
        String text = txtQuestionText.getText().trim();
        String type = cmbQuestionType.getValue();
        boolean isMandatory = chkMandatory.isSelected(); // NEW: Get mandatory status

        if (text.isEmpty()) {
            System.err.println("Question text cannot be empty.");
            return;
        }

        List<String> options = new ArrayList<>();
        if ("SINGLE_CHOICE".equals(type)) {
            TextArea optionsArea = (TextArea) vboxDynamicOptions.lookup("#optionsInput");
            if (optionsArea != null) {
                String optionsText = optionsArea.getText().trim();
                if (!optionsText.isEmpty()) {
                    // Split by comma and trim whitespace
                    options = List.of(optionsText.split(",")).stream()
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(java.util.stream.Collectors.toList());
                }
            }
        }

        // Generate a simple unique ID
        String questionId = "Q" + (++questionCounter);

        // Create new Question object and add to list (UPDATED: Added isMandatory)
        Question newQuestion = new Question(questionId, text, type, options, isMandatory);
        currentQuestions.add(newQuestion);

        // Redraw the list to show the new question
        redrawQuestionsList();

        // Clear the input fields for the next question
        txtQuestionText.clear();
        chkMandatory.setSelected(false); // NEW: Reset mandatory status
        updateDynamicOptions(type);
    }

    private void loadExistingQuestions(String surveyName) {
        currentQuestions.clear();

        MongoDatabase db = MongoManager.getInstance().getDatabase();
        if (db == null) return;

        try {
            MongoCollection<Document> collection = db.getCollection("surveys");
            Document surveyDoc = collection.find(Filters.eq("name", surveyName)).first();

            if (surveyDoc != null && surveyDoc.containsKey("questions")) {
                @SuppressWarnings("unchecked")
                List<Document> questionDocs = (List<Document>) surveyDoc.get("questions");

                for (Document qDoc : questionDocs) {
                    // Reconstruct the Question object from the MongoDB Document
                    List<String> options = qDoc.containsKey("options") ? (List<String>) qDoc.get("options") : new ArrayList<>();
                    // NEW: Retrieve isMandatory status (default to false if not present)
                    boolean isMandatory = qDoc.getBoolean("isMandatory", false);

                    Question q = new Question(
                            qDoc.getString("id"),
                            qDoc.getString("text"),
                            qDoc.getString("type"),
                            options,
                            isMandatory // Pass mandatory status
                    );
                    currentQuestions.add(q);

                    // Update the counter to ensure new IDs don't conflict
                    if (qDoc.getString("id") != null && qDoc.getString("id").startsWith("Q")) {
                        try {
                            int num = Integer.parseInt(qDoc.getString("id").substring(1));
                            if (num > questionCounter) {
                                questionCounter = num;
                            }
                        } catch (NumberFormatException e) {
                            // Ignore non-numeric IDs, rely on UUID fallback later if needed
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error loading existing questions: " + e.getMessage());
        }
    }

    private void redrawQuestionsList() {
        vboxQuestionsList.getChildren().clear();

        if (currentQuestions.isEmpty()) {
            vboxQuestionsList.getChildren().add(new Label("No questions defined yet. Use the panel above to add one."));
            return;
        }

        for (int i = 0; i < currentQuestions.size(); i++) {
            Question q = currentQuestions.get(i);

            // Create a styled VBox for each question
            VBox questionBox = new VBox(5);
            questionBox.setStyle("-fx-border-color: #ccc; -fx-padding: 10px; -fx-background-color: #f9f9f9;");

            // Question Header (Q#, Text, Type, and MANDATORY indicator)
            String mandatoryIndicator = q.isMandatory() ? " (MANDATORY)" : ""; // NEW Indicator
            Label header = new Label(String.format("%d. %s (%s)%s", (i + 1), q.getText(), q.getType(), mandatoryIndicator));
            header.setStyle("-fx-font-weight: bold;");

            // Options Display (if applicable)
            VBox optionsBox = new VBox(2);
            if (!q.getOptions().isEmpty()) {
                optionsBox.getChildren().add(new Label("Options:"));
                for (String option : q.getOptions()) {
                    optionsBox.getChildren().add(new Label("  - " + option));
                }
            }

            // Control Buttons (Delete)
            Button btnDelete = new Button("Delete");
            btnDelete.getStyleClass().add("delete-button"); // For potential CSS styling later
            btnDelete.setOnAction(e -> handleDeleteQuestion(q)); // Pass the question object to delete

            HBox controlBox = new HBox(10, btnDelete);
            controlBox.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

            questionBox.getChildren().addAll(header, optionsBox, controlBox);
            vboxQuestionsList.getChildren().add(questionBox);
        }
    }

    // Placeholder for saving the questions
    @FXML
    private void handleSaveQuestions() {
        // 1. Convert the Java List<Question> into a List<Document> for MongoDB
        List<Document> questionDocs = new ArrayList<>();
        for (Question q : currentQuestions) {
            Document qDoc = new Document("id", q.getId())
                    .append("text", q.getText())
                    .append("type", q.getType())
                    .append("options", q.getOptions())
                    .append("isMandatory", q.isMandatory()); // CRITICAL: Save the mandatory status
            questionDocs.add(qDoc);
        }

        // 2. Perform the MongoDB update
        int newQuestionCount = currentQuestions.size();
        if (saveQuestionsToMongo(questionDocs, newQuestionCount)) {
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
    private boolean saveQuestionsToMongo(List<Document> questionDocs, int questionCount) {
        MongoDatabase db = MongoManager.getInstance().getDatabase();
        if (db == null) return false;

        try {
            MongoCollection<Document> collection = db.getCollection("surveys");

            // Filter: Find the survey using its unique name
            org.bson.conversions.Bson filter = Filters.eq("name", currentSurvey.getName());

            // Update 1: Set the 'questions' array
            org.bson.conversions.Bson updateQuestions = Updates.set("questions", questionDocs);

            // Update 2: Set the CRITICAL 'numQuestions' integer field
            org.bson.conversions.Bson updateCount = Updates.set("numQuestions", questionCount);

            // Combine updates into a single list
            List<org.bson.conversions.Bson> updatesList = List.of(updateQuestions, updateCount);

            // Apply both updates to the document
            collection.updateOne(filter, Updates.combine(updatesList));

            return true;

        } catch (Exception e) {
            System.err.println("MongoDB Question Save Error: " + e.getMessage());
            return false;
        }
    }

    private void handleDeleteQuestion(Question q) {
        currentQuestions.remove(q);
        redrawQuestionsList(); // Refresh the UI
        System.out.println("Deleted question: " + q.getText());
    }

    private void closeWindow() {
        Stage stage = (Stage) ((Button) btnCancel).getScene().getWindow(); // Assuming btnCancel is defined
        stage.close();
    }
}
