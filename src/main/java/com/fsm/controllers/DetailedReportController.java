package com.fsm.controllers;

import com.fsm.database.MongoManager;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import org.bson.Document;
import org.bson.types.ObjectId;
import javafx.event.ActionEvent; // <--- ADDED THIS IMPORT

import java.io.IOException;
import java.util.*;

public class DetailedReportController {

    @FXML private Label lblSurveyName;
    @FXML private TableView<Map<String, String>> responseTable;
    @FXML private Button btnBack;

    private String surveyId;
    private String surveyName;

    @FXML
    public void initialize() {
        // Initialization can be minimal since data loading depends on initData
    }

    /**
     * Called by ReportController to pass the necessary survey context.
     */
    public void initData(String surveyId, String surveyName) {
        this.surveyId = surveyId;
        this.surveyName = surveyName;
        lblSurveyName.setText("Detailed Responses for: " + surveyName);
        loadDetailedResponses();
    }

    /**
     * Loads the survey questions and then fetches the responses, dynamically building the table.
     */
    private void loadDetailedResponses() {
        MongoDatabase db = MongoManager.connect();
        if (db == null) return;

        responseTable.getColumns().clear();
        responseTable.getItems().clear();

        try {
            // 1. Fetch the survey structure to get the question list
            MongoCollection<Document> surveyCollection = db.getCollection("surveys");
            Document surveyDoc = surveyCollection.find(Filters.eq("_id", new ObjectId(surveyId))).first();

            if (surveyDoc == null) {
                lblSurveyName.setText("Error: Survey not found.");
                return;
            }

            List<Document> questions = surveyDoc.getList("questions", Document.class, new ArrayList<>());

            // Map: Key = Question ID (from DB), Value = Question Text (for Column Header)
            Map<String, String> questionMap = new LinkedHashMap<>();
            questionMap.put("Timestamp", "Submission Date"); // Always add a timestamp column

            for (Document q : questions) {
                questionMap.put(q.getString("id"), q.getString("text"));
            }

            // 2. Dynamically create TableColumns
            for (Map.Entry<String, String> entry : questionMap.entrySet()) {
                String colId = entry.getKey();
                String colHeader = entry.getValue();

                TableColumn<Map<String, String>, String> column = new TableColumn<>(colHeader);

                // IMPORTANT: Use the column ID (Question ID) to retrieve the value from the Map<String, String> row item
                column.setCellValueFactory(data -> {
                    String value = data.getValue().getOrDefault(colId, "N/A");
                    // Format arrays (multi-choice) into a readable string
                    if (value.startsWith("[") && value.endsWith("]")) {
                        value = value.substring(1, value.length() - 1);
                    }
                    return new SimpleStringProperty(value);
                });

                // Set column width based on whether it's the Timestamp or a question
                if (colId.equals("Timestamp")) {
                    column.setPrefWidth(150);
                } else {
                    column.setPrefWidth(250);
                }

                responseTable.getColumns().add(column);
            }

            // 3. Fetch all responses for this survey
            MongoCollection<Document> responseCollection = db.getCollection("responses");
            ObservableList<Map<String, String>> tableRows = FXCollections.observableArrayList();

            for (Document responseDoc : responseCollection.find(Filters.eq("survey_id", new ObjectId(surveyId)))) {
                Map<String, String> row = new HashMap<>();

                // Add timestamp first
                Date timestamp = responseDoc.getDate("timestamp");
                row.put("Timestamp", timestamp != null ? timestamp.toString() : "N/A");

                // Process answers array
                List<Document> answers = responseDoc.getList("answers", Document.class, new ArrayList<>());
                for (Document answerDoc : answers) {
                    String qId = answerDoc.getString("question_id");
                    Object answer = answerDoc.get("answer");

                    if (qId != null && answer != null) {
                        row.put(qId, answer.toString());
                    }
                }

                tableRows.add(row);
            }

            responseTable.setItems(tableRows);

        } catch (Exception e) {
            System.err.println("Error loading detailed report data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handleBack(ActionEvent event) { // <--- Event type is now recognized
        // Go back to the Survey Summary Report (ReportController)
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/fsm/report-view.fxml"));
            Parent summaryReportView = loader.load();

            // Get the parent container
            AnchorPane parent = (AnchorPane) responseTable.getParent().getParent();
            parent.getChildren().clear();
            parent.getChildren().add(summaryReportView);

            // Anchor the new view to fill the entire container
            AnchorPane.setTopAnchor(summaryReportView, 0.0);
            AnchorPane.setBottomAnchor(summaryReportView, 0.0);
            AnchorPane.setLeftAnchor(summaryReportView, 0.0);
            AnchorPane.setRightAnchor(summaryReportView, 0.0);

        } catch (IOException e) {
            System.err.println("Failed to load summary report view: " + e.getMessage());
        }
    }
}
