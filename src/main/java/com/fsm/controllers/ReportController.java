package com.fsm.controllers;

import com.fsm.database.MongoManager;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

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
    // -----------------------------------------------------------
    public static class ReportSurvey {
        private final String name;
        private final String status;
        private final int numQuestions;
        private final String dateCreated;
        private final int totalResponses;

        // Formatter for display
        private static final DateTimeFormatter DATE_FORMAT =
                DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

        public ReportSurvey(String name, String status, int numQuestions, Date dateCreated, int totalResponses) {
            this.name = name;
            this.status = status;
            this.numQuestions = numQuestions;
            this.dateCreated = (dateCreated != null) ? DATE_FORMAT.format(dateCreated.toInstant()) : "N/A";
            this.totalResponses = totalResponses;
        }

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

    private final ObservableList<ReportSurvey> reportData = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        // 1. Link table columns to the ReportSurvey model's getters
        colSurveyName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colQuestions.setCellValueFactory(new PropertyValueFactory<>("numQuestions"));
        colDateCreated.setCellValueFactory(new PropertyValueFactory<>("dateCreated"));
        colTotalResponses.setCellValueFactory(new PropertyValueFactory<>("totalResponses"));

        // 2. Set the data source
        surveyReportTable.setItems(reportData);

        // 3. Load data on initialization
        loadReportData();
    }

    /**
     * Executes a MongoDB aggregation to count the number of responses for each survey ID.
     * @return A map where key is the Survey ObjectId string and value is the response count.
     */
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
            // CRITICAL: Get the response counts before iterating through surveys
            Map<String, Integer> responseCounts = getSurveyResponseCounts(db);

            MongoCollection<Document> surveyCollection = db.getCollection("surveys");

            for (Document doc : surveyCollection.find()) {
                String surveyId = doc.getObjectId("_id").toHexString();

                // Get the total number of questions
                int questionCount = 0;
                if (doc.containsKey("questions") && doc.get("questions") instanceof List) {
                    questionCount = ((List) doc.get("questions")).size();
                }

                // Retrieve the response count from the map, defaulting to 0 if not found
                int totalResponses = responseCounts.getOrDefault(surveyId, 0);

                ReportSurvey report = new ReportSurvey(
                        doc.getString("name"),
                        doc.getString("status"),
                        questionCount,
                        doc.getDate("dateCreated"),
                        totalResponses // Now uses the actual count
                );
                reportData.add(report);
            }
        } catch (Exception e) {
            System.err.println("Error loading report data: " + e.getMessage());
        }
    }
}
