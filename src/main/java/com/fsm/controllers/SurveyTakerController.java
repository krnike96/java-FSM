package com.fsm.controllers;

import com.fsm.database.MongoManager;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.VBox;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.mongodb.MongoException;
import org.bson.types.ObjectId;

public class SurveyTakerController {

    @FXML private Label lblUsername;
    @FXML private ComboBox<SurveyItem> cmbSurveySelector;
    @FXML private ScrollPane questionScrollPane;
    @FXML private VBox questionsContainer;
    @FXML private Button btnSubmit;

    // Key: Question ID, Value: The JavaFX control (TextField, ToggleGroup, CheckBox list)
    private final Map<String, Object> responseControls = new HashMap<>();

    // Store the question list from the currently loaded survey to check for mandatory status
    private List<Document> currentQuestionsMetadata = new ArrayList<>();

    private String currentUserRole;
    private String currentUsername;

    // A simple container class for the ComboBox to hold display name and ID
    private static class SurveyItem {
        String id;
        String name;
        public SurveyItem(String id, String name) { this.id = id; this.name = name; }
        public String toString() { return name; }
    }

    @FXML
    public void initialize() {
        loadActiveSurveys();

        cmbSurveySelector.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                // When a new survey is selected, load its questions
                loadSurveyQuestions(newVal.id);
                btnSubmit.setDisable(false);
            } else {
                questionsContainer.getChildren().clear();
                btnSubmit.setDisable(true);
            }
        });
    }

    /**
     * Initializes the controller with the logged-in user's data.
     * @param userRole The role of the current user.
     * @param username The username of the current user (used for logging responses).
     */
    public void initData(String userRole, String username) {
        this.currentUserRole = userRole;
        this.currentUsername = username;
        lblUsername.setText("Logged in as: " + currentUsername + " (" + currentUserRole + ")");
    }

    private void loadActiveSurveys() {
        MongoDatabase db = MongoManager.getInstance().getDatabase();
        if (db == null) return;

        ObservableList<SurveyItem> surveys = FXCollections.observableArrayList();

        try {
            MongoCollection<Document> surveyCollection = db.getCollection("surveys");

            // Filter the surveys collection to only include documents where status is "Active"
            Document filter = new Document("status", "Active");

            // Iterate over the filtered results
            for (Document doc : surveyCollection.find(filter)) {
                String id = doc.getObjectId("_id").toHexString();
                String name = doc.getString("name");
                surveys.add(new SurveyItem(id, name));
            }
            cmbSurveySelector.setItems(surveys);

        } catch (MongoException e) {
            System.err.println("Error loading surveys: " + e.getMessage());
        }
    }

    private void loadSurveyQuestions(String surveyId) {
        questionsContainer.getChildren().clear();
        responseControls.clear();
        currentQuestionsMetadata.clear(); // Clear old metadata

        MongoDatabase db = MongoManager.getInstance().getDatabase();
        if (db == null) return;

        try {
            MongoCollection<Document> surveyCollection = db.getCollection("surveys");
            Document surveyDoc = surveyCollection.find(Filters.eq("_id", new ObjectId(surveyId))).first();

            if (surveyDoc != null) {
                List<Document> questions = surveyDoc.getList("questions", Document.class, new ArrayList<>());
                currentQuestionsMetadata.addAll(questions); // Store the metadata

                for (int i = 0; i < questions.size(); i++) {
                    Document q = questions.get(i);
                    String questionId = q.getString("id");
                    String type = q.getString("type").toUpperCase();
                    String text = q.getString("text");
                    // Check if the question is marked as mandatory (default to false if key missing)
                    boolean isMandatory = q.getBoolean("isMandatory", false);

                    VBox questionBox = new VBox(5);

                    String mandatoryMark = isMandatory ? " (*)" : "";
                    Label questionLabel = new Label((i + 1) + ". " + text + mandatoryMark);
                    questionLabel.setStyle("-fx-font-weight: bold; -fx-padding: 5 0 0 0;");
                    questionBox.getChildren().add(questionLabel);

                    // Dynamically create the appropriate JavaFX control based on normalized type
                    switch (type) {
                        case "TEXT_INPUT":
                        case "TEXT":
                            TextField textField = new TextField();
                            textField.setPromptText("Enter response here...");
                            questionBox.getChildren().add(textField);
                            responseControls.put(questionId, textField);
                            break;

                        case "SINGLE_CHOICE":
                        case "RADIO":
                            ToggleGroup group = new ToggleGroup();
                            VBox radioOptions = new VBox(3);
                            List<String> radioList = q.getList("options", String.class, new ArrayList<>());
                            for (String option : radioList) {
                                RadioButton radio = new RadioButton(option);
                                radio.setToggleGroup(group);
                                radioOptions.getChildren().add(radio);
                            }
                            questionBox.getChildren().add(radioOptions);
                            responseControls.put(questionId, group);
                            break;

                        case "MULTI_CHOICE":
                        case "CHECKBOX":
                            VBox checkboxOptions = new VBox(3);
                            List<String> checkboxList = q.getList("options", String.class, new ArrayList<>());
                            List<CheckBox> checkBoxes = new ArrayList<>();
                            for (String option : checkboxList) {
                                CheckBox cb = new CheckBox(option);
                                checkboxOptions.getChildren().add(cb);
                                checkBoxes.add(cb);
                            }
                            questionBox.getChildren().add(checkboxOptions);
                            responseControls.put(questionId, checkBoxes);
                            break;

                        case "RATING":
                            Label ratingNote = new Label("Enter a rating (e.g., 1-5 or 1-10):");
                            TextField ratingField = new TextField();
                            ratingField.setMaxWidth(150);
                            questionBox.getChildren().addAll(ratingNote, ratingField);
                            responseControls.put(questionId, ratingField);
                            break;

                        default:
                            System.err.println("WARNING: Unrecognized question type: " + q.getString("type") + " for question ID: " + questionId);
                            Label errorLabel = new Label("--- ERROR: Unrecognized question type: " + q.getString("type") + " ---");
                            questionBox.getChildren().add(errorLabel);
                            break;
                    }

                    questionsContainer.getChildren().add(questionBox);
                }
            }
        } catch (MongoException e) {
            System.err.println("Error loading survey questions: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("General error loading survey questions: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Resets the input controls for the currently loaded questions, readying the form for a new entry.
     */
    private void resetForm() {
        for (Object control : responseControls.values()) {
            if (control instanceof TextField) {
                ((TextField) control).clear();
            } else if (control instanceof ToggleGroup) {
                ((ToggleGroup) control).selectToggle(null);
            } else if (control instanceof List) {
                // Handle Checkbox list
                for (Object obj : (List) control) {
                    if (obj instanceof CheckBox) {
                        ((CheckBox) obj).setSelected(false);
                    }
                }
            }
        }
    }

    /**
     * Core logic to extract responses and perform client-side validation.
     */
    @FXML
    private void handleSubmit(ActionEvent event) {
        SurveyItem selectedSurvey = cmbSurveySelector.getSelectionModel().getSelectedItem();
        if (selectedSurvey == null) {
            showAlert("Error", "Please select a survey.", AlertType.ERROR);
            return;
        }

        List<Document> responses = new ArrayList<>();
        List<String> missingAnswers = new ArrayList<>();

        // Loop through the stored question metadata to check for mandatory status
        for (Document qMetadata : currentQuestionsMetadata) {
            String questionId = qMetadata.getString("id");
            String questionText = qMetadata.getString("text");
            boolean isMandatory = qMetadata.getBoolean("isMandatory", false);

            Object control = responseControls.get(questionId);
            Object answer = null;
            boolean answered = false;

            // 1. Extract the answer based on the control type
            if (control instanceof TextField) {
                String text = ((TextField) control).getText().trim();
                answer = text;
                answered = !text.isEmpty();
            } else if (control instanceof ToggleGroup) {
                RadioButton selected = (RadioButton) ((ToggleGroup) control).getSelectedToggle();
                if (selected != null) {
                    answer = selected.getText();
                    answered = true;
                }
            } else if (control instanceof List) { // Multi-Choice (Checkboxes)
                List<String> selectedOptions = new ArrayList<>();
                for (Object obj : (List) control) {
                    if (obj instanceof CheckBox) {
                        CheckBox cb = (CheckBox) obj;
                        if (cb.isSelected()) {
                            selectedOptions.add(cb.getText());
                        }
                    }
                }
                answer = selectedOptions;
                answered = !selectedOptions.isEmpty();
            }

            // 2. Perform Validation Check
            if (isMandatory && !answered) {
                // If mandatory and not answered, record it as missing.
                missingAnswers.add(questionText);
            }

            // 3. Collect the response for submission (even if empty, unless validation fails)
            // Ensure answer is properly formatted for MongoDB (empty string or empty list)
            if (answer == null) {
                // If it was a mandatory field, it's already caught above. If optional, store empty string.
                answer = "";
            } else if (answer instanceof List && ((List)answer).isEmpty()) {
                // Keep empty list if it was a list type
                answer = new ArrayList<String>();
            }

            responses.add(new Document()
                    .append("question_id", questionId)
                    .append("answer", answer)
            );
        }

        // --- FINAL VALIDATION CHECK ---
        if (!missingAnswers.isEmpty()) {
            String missingList = String.join("\n- ", missingAnswers);
            showAlert("Validation Error",
                    "The following mandatory questions must be answered before submitting:\n- " + missingList,
                    AlertType.ERROR);
            return; // STOP submission
        }

        // --- SUBMISSION ---
        if (!responses.isEmpty()) {
            saveResponse(selectedSurvey.id, responses);
        } else {
            showAlert("Submission Error", "No responses were collected. Check console for question type errors.", AlertType.ERROR);
        }
    }

    private void saveResponse(String surveyId, List<Document> responses) {
        MongoDatabase db = MongoManager.getInstance().getDatabase();

        if (db == null) {
            showAlert("FATAL ERROR", "Database connection failed. Check MongoManager.", AlertType.ERROR);
            return;
        }

        try {
            MongoCollection<Document> responseCollection = db.getCollection("responses");

            Document responseDoc = new Document()
                    .append("survey_id", new ObjectId(surveyId))
                    // Use the dynamically set currentUsername
                    .append("user_id", this.currentUsername)
                    .append("timestamp", new java.util.Date())
                    .append("answers", responses);

            responseCollection.insertOne(responseDoc);

            showAlert("Success", "Survey response saved successfully! Form is now reset for the next entry.", AlertType.INFORMATION);

            resetForm();

        } catch (MongoException e) {
            System.err.println("MongoDB Error saving survey response: " + e.getMessage());
            showAlert("Database Error", "Failed to save response due to a database error: " + e.getMessage(), AlertType.ERROR);
        } catch (Exception e) {
            System.err.println("General Error saving survey response: " + e.getMessage());
            showAlert("General Error", "Failed to save response. See console for details.", AlertType.ERROR);
        }
    }

    private void showAlert(String title, String message, AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
