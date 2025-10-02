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

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

public class ReportController {

    // -----------------------------------------------------------
    // Nested Model Class: Read-only data model for the report table
    // -----------------------------------------------------------
    public static class ReportSurvey {
        private final String name;
        private final String status;
        private final int numQuestions;
        private final String dateCreated;
        private final int totalResponses; // Placeholder for future data

        // Formatter for display
        private static final DateTimeFormatter DATE_FORMAT =
                DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

        public ReportSurvey(String name, String status, int numQuestions, Date dateCreated) {
            this.name = name;
            this.status = status;
            this.numQuestions = numQuestions;
            this.dateCreated = (dateCreated != null) ? DATE_FORMAT.format(dateCreated.toInstant()) : "N/A";
            this.totalResponses = 0; // Initialize to zero, will be populated later
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

    private void loadReportData() {
        reportData.clear();
        MongoDatabase db = MongoManager.connect();

        if (db == null) {
            System.err.println("Database connection failed. Cannot load report data.");
            return;
        }

        try {
            MongoCollection<Document> surveyCollection = db.getCollection("surveys");

            for (Document doc : surveyCollection.find()) {
                // Get the total number of questions by checking the size of the 'questions' array
                int questionCount = 0;
                if (doc.containsKey("questions") && doc.get("questions") instanceof List) {
                    questionCount = ((List) doc.get("questions")).size();
                }

                ReportSurvey report = new ReportSurvey(
                        doc.getString("name"),
                        doc.getString("status"),
                        questionCount,
                        doc.getDate("dateCreated")
                );
                reportData.add(report);
            }
        } catch (Exception e) {
            System.err.println("Error loading report data: " + e.getMessage());
        }
    }
}