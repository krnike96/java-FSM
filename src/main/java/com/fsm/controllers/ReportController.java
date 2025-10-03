package com.fsm.controllers;

import com.fsm.database.MongoManager;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.AnchorPane;
import javafx.scene.Parent;
import javafx.event.ActionEvent;

// Imports for CSV Export
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;
import java.io.File;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.io.IOException;

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
import java.util.Optional;

public class ReportController {

    // -----------------------------------------------------------
    // Nested Model Class: Read-only data model for the report table
    // -----------------------------------------------------------
    public static class ReportSurvey {
        private final String id;
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
    @FXML private Button btnViewDetails;
    // NEW: Inject the Export button
    @FXML private Button btnExportCSV;

    @FXML private AnchorPane reportContainer;

    private String currentUserRole;
    private String currentUsername;

    private final ObservableList<ReportSurvey> reportData = FXCollections.observableArrayList();

    /**
     * Initializes the controller with the logged-in user's role and username.
     */
    public void initData(String userRole, String username) {
        this.currentUserRole = userRole;
        this.currentUsername = username;
        System.out.println("INIT_DATA: Received Role: " + userRole + ", Username: " + username);
        // Load data only after user information is set
        loadReportData();
    }

    @FXML
    public void initialize() {
        // 1. Column setup
        colSurveyName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colQuestions.setCellValueFactory(new PropertyValueFactory<>("numQuestions"));
        colDateCreated.setCellValueFactory(new PropertyValueFactory<>("dateCreated"));
        colTotalResponses.setCellValueFactory(new PropertyValueFactory<>("totalResponses"));

        // 2. Set the data source
        surveyReportTable.setItems(reportData);

        // Disable detail button until a survey is selected
        btnViewDetails.setDisable(true);
        // NEW: Disable export button until data is loaded
        // It will be re-enabled in loadReportData if data exists
        if (btnExportCSV != null) {
            btnExportCSV.setDisable(true);
        }

        surveyReportTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            btnViewDetails.setDisable(newVal == null);
        });
    }

    /**
     * Helper method to show an alert dialog.
     */
    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // --- NEW: CSV Export Implementation ---

    /**
     * Handles the 'Export to CSV' button click.
     * Prompts the user for a save location and exports the current table data.
     */
    @FXML
    private void handleExportToCSV(ActionEvent event) {
        if (reportData.isEmpty()) {
            showAlert(Alert.AlertType.INFORMATION, "Export Failed", "The report table is empty. Nothing to export.");
            return;
        }

        // Use the event source to get the primary Stage
        Stage stage = (Stage) ((Button) event.getSource()).getScene().getWindow();

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Survey Summary Report");
        // Set default filename
        fileChooser.setInitialFileName("Survey_Summary_Report.csv");
        // Set extension filter
        fileChooser.getExtensionFilters().add(
                new ExtensionFilter("CSV Files (*.csv)", "*.csv"));

        // Show save file dialog
        File file = fileChooser.showSaveDialog(stage);

        if (file != null) {
            boolean success = writeDataToCSV(file);
            if (success) {
                showAlert(Alert.AlertType.INFORMATION, "Export Successful",
                        "Report successfully saved to:\n" + file.getAbsolutePath());
            } else {
                showAlert(Alert.AlertType.ERROR, "Export Failed",
                        "An error occurred while writing the file. Check console for details.");
            }
        }
    }

    /**
     * Writes the ReportSurvey data into a CSV file.
     * @param file The file handle to write to.
     * @return true if successful, false otherwise.
     */
    private boolean writeDataToCSV(File file) {
        // Headers corresponding to the ReportSurvey properties
        String header = "Survey ID,Survey Name,Status,Number of Questions,Date Created,Total Responses\n";

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(header);

            for (ReportSurvey survey : reportData) {
                // Manually construct the CSV line, ensuring double quotes around string fields for safety
                String line = String.format("\"%s\",\"%s\",\"%s\",%d,%s,%d\n",
                        survey.getId(),
                        survey.getName(),
                        survey.getStatus(),
                        survey.getNumQuestions(),
                        survey.getDateCreated(),
                        survey.getTotalResponses()
                );
                writer.write(line);
            }
            return true;

        } catch (IOException e) {
            System.err.println("Error writing CSV file: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // --- End NEW: CSV Export Implementation ---


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
                // Ensure ObjectId is handled correctly when coming from aggregation
                Object idObject = doc.get("_id");
                if (idObject instanceof ObjectId) {
                    ObjectId surveyId = (ObjectId) idObject;
                    Integer count = doc.getInteger("count");
                    if (surveyId != null && count != null) {
                        counts.put(surveyId.toHexString(), count);
                    }
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
            if (btnExportCSV != null) btnExportCSV.setDisable(true); // Ensure disabled on failure
            return;
        }

        System.out.println("INFO: Database connection established successfully for reports.");

        // --- RBAC Logic for Reports ---
        Bson filter; // Default: empty filter (shows all)

        // Only restrict access if the user is explicitly a Survey Creator
        if ("Survey Creator".equals(currentUserRole) && currentUsername != null) {
            // Creators can only see surveys they created.
            filter = Filters.regex("creator", "^" + currentUsername + "$", "i");
            System.out.println("✅ Report RBAC: Creator Filter Applied (Case-Insensitive Regex). User: " + currentUsername + ", Filter: " + filter.toString());
        } else {
            // If the role is NOT "Survey Creator" (i.e., Administrator, Data Entry), show all surveys.
            filter = new Document();
            System.out.println("✅ Report RBAC: Showing ALL surveys. Role: " + currentUserRole);
        }
        // --- End RBAC Logic ---

        try {
            Map<String, Integer> responseCounts = getSurveyResponseCounts(db);

            MongoCollection<Document> surveyCollection = db.getCollection("surveys");

            int documentsFound = 0;
            for (Document doc : surveyCollection.find(filter)) {
                documentsFound++;

                String surveyId = doc.getObjectId("_id").toHexString();

                int questionCount = 0;
                if (doc.containsKey("questions") && doc.get("questions") instanceof List) {
                    questionCount = ((List) doc.get("questions")).size();
                }

                int totalResponses = responseCounts.getOrDefault(surveyId, 0);

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

            if (documentsFound == 0) {
                System.out.println("⚠️ WARNING: MongoDB query executed successfully but returned zero survey documents.");
            }

            // Enable or disable the export button based on the loaded data
            if (btnExportCSV != null) {
                btnExportCSV.setDisable(reportData.isEmpty());
            }
            System.out.println("INFO: Loaded " + reportData.size() + " reports for user '" + currentUsername + "'.");

        } catch (Exception e) {
            System.err.println("Error loading report data: " + e.getMessage());
            if (btnExportCSV != null) {
                btnExportCSV.setDisable(true);
            }
        }
    }

    @FXML
    private void handleViewDetails(ActionEvent event) {
        ReportSurvey selectedSurvey = surveyReportTable.getSelectionModel().getSelectedItem();

        if (selectedSurvey == null) {
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/fsm/detailed-report-view.fxml"));
            Parent detailedReportView = loader.load();

            // Pass the survey data to the new controller
            DetailedReportController controller = loader.getController();
            // NOTE: We may need to update DetailedReportController later to include RBAC logic too
            controller.initData(selectedSurvey.getId(), selectedSurvey.getName());

            // Replace the current content (ReportController's view) with the detailed view
            AnchorPane parent = (AnchorPane) surveyReportTable.getParent().getParent();
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
            showAlert(Alert.AlertType.ERROR, "View Error", "Failed to load the detailed report screen.");
        }
    }
}
