package com.fsm.controllers;

import com.fsm.database.MongoManager;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.AnchorPane;
import javafx.scene.Parent;
import javafx.event.ActionEvent;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReportController {

    // -----------------------------------------------------------
    // Nested Model Class: Read-only data model for the report table
    // ... (ReportSurvey class remains the same)
    // -----------------------------------------------------------
    public static class ReportSurvey {
        private final String id; // Added ID field
        private final String name;
        private final String status;
        private final int numQuestions;
        private final String dateCreated;
        private final int totalResponses;

        // Formatter for display
        private static final DateTimeFormatter DATE_FORMAT =
                DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

        // Constructor updated to take ID
        public ReportSurvey(String id, String name, String status, int numQuestions, Date dateCreated, int totalResponses) {
            this.id = id;
            this.name = name;
            this.status = status;
            this.numQuestions = numQuestions;
            this.dateCreated = (dateCreated != null) ? DATE_FORMAT.format(dateCreated.toInstant()) : "N/A";
            this.totalResponses = totalResponses;
        }

        // Getter for ID (CRITICAL for detail view)
        public String getId() { return id; }
        // Standard Getters (required for TableView PropertyValueFactory)
        public String getName() { return name; }
        public String getStatus() { return status; }
        public int getNumQuestions() { return numQuestions; }
        public String getDateCreated() { return dateCreated; }
        public int getTotalResponses() { return totalResponses; }
    }
    // -----------------------------------------------------------

    @FXML private TableView<ReportSurvey> surveyReportTable;
    @FXML private TableColumn<ReportSurvey, String> colSurveyName;
    @FXML private TableColumn<ReportSurvey, String> colStatus;
    @FXML private TableColumn<ReportSurvey, Integer> colQuestions;
    @FXML private TableColumn<ReportSurvey, String> colDateCreated;
    @FXML private TableColumn<ReportSurvey, Integer> colTotalResponses;
    @FXML private Button btnViewDetails; // New button for viewing details

    @FXML private AnchorPane reportContainer; // Assuming the ReportController is loaded into an AnchorPane

    private final ObservableList<ReportSurvey> reportData = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        // ... (Column setup remains the same)
        colSurveyName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colQuestions.setCellValueFactory(new PropertyValueFactory<>("numQuestions"));
        colDateCreated.setCellValueFactory(new PropertyValueFactory<>("dateCreated"));
        colTotalResponses.setCellValueFactory(new PropertyValueFactory<>("totalResponses"));

        // 2. Set the data source
        surveyReportTable.setItems(reportData);

        // Disable detail button until a survey is selected
        btnViewDetails.setDisable(true);

        surveyReportTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            btnViewDetails.setDisable(newVal == null);
        });

        // 3. Load data on initialization
        loadReportData();
    }

    // ... (getSurveyResponseCounts method remains the same)
    private Map<String, Integer> getSurveyResponseCounts(MongoDatabase db) {
        Map<String, Integer> counts = new HashMap<>();
        MongoCollection<Document> responseCollection = db.getCollection("responses");

        // Aggregation pipeline: [ {$group: {_id: "$survey_id", count: {$sum: 1}}} ]
        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(new Document("$group",
                new Document("_id", "$survey_id")
                        .append("count", new Document("$sum", 1))
        ));

        try {
            responseCollection.aggregate(pipeline).forEach(doc -> {
                ObjectId surveyId = doc.getObjectId("_id");
                Integer count = doc.getInteger("count");
                if (surveyId != null && count != null) {
                    counts.put(surveyId.toHexString(), count);
                }
            });
        } catch (Exception e) {
            System.err.println("Error calculating response counts: " + e.getMessage());
        }

        return counts;
    }


    private void loadReportData() {
        reportData.clear();
        MongoDatabase db = MongoManager.connect();

        if (db == null) {
            System.err.println("Database connection failed. Cannot load report data.");
            return;
        }

        try {
            Map<String, Integer> responseCounts = getSurveyResponseCounts(db);

            MongoCollection<Document> surveyCollection = db.getCollection("surveys");

            for (Document doc : surveyCollection.find()) {
                String surveyId = doc.getObjectId("_id").toHexString();

                int questionCount = 0;
                if (doc.containsKey("questions") && doc.get("questions") instanceof List) {
                    questionCount = ((List) doc.get("questions")).size();
                }

                int totalResponses = responseCounts.getOrDefault(surveyId, 0);

                // IMPORTANT: Pass the survey ID to the ReportSurvey model
                ReportSurvey report = new ReportSurvey(
                        surveyId, // Pass ID
                        doc.getString("name"),
                        doc.getString("status"),
                        questionCount,
                        doc.getDate("dateCreated"),
                        totalResponses
                );
                reportData.add(report);
            }
        } catch (Exception e) {
            System.err.println("Error loading report data: " + e.getMessage());
        }
    }

    @FXML
    private void handleViewDetails(ActionEvent event) {
        ReportSurvey selectedSurvey = surveyReportTable.getSelectionModel().getSelectedItem();

        if (selectedSurvey == null) {
            // Should be disabled, but good practice to check
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/fsm/detailed-report-view.fxml"));
            Parent detailedReportView = loader.load();

            // Pass the survey data to the new controller
            DetailedReportController controller = loader.getController();
            controller.initData(selectedSurvey.getId(), selectedSurvey.getName());

            // Replace the current content (ReportController's view) with the detailed view
            // NOTE: This assumes ReportController is loaded into an AnchorPane or similar container
            AnchorPane parent = (AnchorPane) surveyReportTable.getParent().getParent(); // Adjust based on your FXML nesting
            parent.getChildren().clear();
            parent.getChildren().add(detailedReportView);

            // Anchor the new view to fill the entire container
            AnchorPane.setTopAnchor(detailedReportView, 0.0);
            AnchorPane.setBottomAnchor(detailedReportView, 0.0);
            AnchorPane.setLeftAnchor(detailedReportView, 0.0);
            AnchorPane.setRightAnchor(detailedReportView, 0.0);

        } catch (IOException e) {
            System.err.println("Failed to load detailed report view: " + e.getMessage());
            e.printStackTrace();
        }
    }
}