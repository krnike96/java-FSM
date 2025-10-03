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
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.AnchorPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.bson.Document;
import org.bson.types.ObjectId;
import javafx.event.ActionEvent;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

public class DetailedReportController {

    @FXML private Label lblSurveyName;
    @FXML private TableView<Map<String, String>> responseTable;
    @FXML private Button btnBack;
    @FXML private Button btnExport; // New FXML binding for the export button

    private String surveyId;
    private String surveyName;

    // Data storage for the export functionality
    private List<String> columnKeys = new ArrayList<>(); // Stores the Question IDs/Keys ("Timestamp", "q1", etc.)
    private ObservableList<Map<String, String>> tableData = FXCollections.observableArrayList();

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
        columnKeys.clear();
        tableData.clear();

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

            // Store the list of keys (IDs) for CSV generation
            columnKeys.addAll(questionMap.keySet());


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
                        // Remove brackets and trim whitespace for CSV friendly format
                        value = value.substring(1, value.length() - 1).trim();
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

            // Store data for export and set to table
            tableData = tableRows;
            responseTable.setItems(tableData);

        } catch (Exception e) {
            System.err.println("Error loading detailed report data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handles the action to export the detailed response data to a CSV file.
     */
    @FXML
    private void handleExportCsv() {
        if (tableData.isEmpty()) {
            showAlert(AlertType.INFORMATION, "Export Failed", "No responses available to export.");
            return;
        }

        // 1. Get the current Stage for the file chooser
        Stage stage = (Stage) btnExport.getScene().getWindow();

        // 2. Configure and show the FileChooser
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Survey Responses");
        fileChooser.setInitialFileName(surveyName.replaceAll("[^a-zA-Z0-9\\s]", "") + "_Responses.csv");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV files (*.csv)", "*.csv")
        );

        File file = fileChooser.showSaveDialog(stage);

        if (file != null) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {

                // --- 3. Write CSV Headers ---
                StringBuilder headerLine = new StringBuilder();
                List<String> headers = new ArrayList<>();
                // Use columnKeys to ensure correct order
                for (String key : columnKeys) {
                    // Find the corresponding header text from the TableColumns
                    for (TableColumn<Map<String, String>, ?> col : responseTable.getColumns()) {
                        if (col.getText().equals("Submission Date") && key.equals("Timestamp")) {
                            headers.add("\"Submission Date\"");
                            break;
                        }
                        // This uses the actual question text as the header
                        if (col.getText() != null && col.getText().length() > 0) {
                            // Find the column that uses the current key/ID
                            // Since we didn't store a key->header map, we rely on the order/text comparison.
                            // However, the column keys (qIds) are unique. Let's rely on the questionMap logic
                            // or simply use the column names from the TableView itself for guaranteed display accuracy.
                            // A safer approach is to reconstruct the header list using the visible columns:
                            if (col.getText() != null) {
                                headers.add("\"" + col.getText().replace("\"", "\"\"") + "\"");
                            }
                        }
                    }
                }

                // Re-creating header list correctly based on columnKeys and questionMap logic
                // A simpler, more robust approach is to iterate over the columns' display names.
                List<String> displayHeaders = new ArrayList<>();
                for (TableColumn<Map<String, String>, ?> col : responseTable.getColumns()) {
                    // Ensure the header text itself is properly quoted for CSV
                    displayHeaders.add("\"" + col.getText().replace("\"", "\"\"") + "\"");
                }

                writer.println(String.join(",", displayHeaders));

                // --- 4. Write CSV Data Rows ---
                for (Map<String, String> rowData : tableData) {
                    StringBuilder dataLine = new StringBuilder();
                    boolean first = true;

                    // Iterate using the columnKeys list to maintain column order
                    for (String key : columnKeys) {
                        if (!first) {
                            dataLine.append(",");
                        }
                        String value = rowData.getOrDefault(key, "");

                        // Sanitize value: escape double quotes and enclose in quotes
                        String quotedValue = "\"" + value.replace("\"", "\"\"") + "\"";
                        dataLine.append(quotedValue);
                        first = false;
                    }
                    writer.println(dataLine.toString());
                }

                showAlert(AlertType.INFORMATION, "Export Successful",
                        "Responses exported successfully to:\n" + file.getAbsolutePath());

            } catch (IOException e) {
                System.err.println("Error writing CSV file: " + e.getMessage());
                showAlert(AlertType.ERROR, "Export Failed",
                        "Could not write the file: " + e.getMessage());
            }
        }
    }

    /**
     * Utility method to display alerts.
     */
    private void showAlert(AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    @FXML
    private void handleBack(ActionEvent event) {
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
