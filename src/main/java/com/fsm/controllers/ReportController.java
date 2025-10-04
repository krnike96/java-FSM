package com.fsm.controllers;

import com.fsm.database.MongoManager;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane; // Added for chart container
import javafx.scene.layout.VBox; // Added VBox for visualization panel
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
import java.util.*;
import java.util.stream.Collectors;

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

        private static final DateTimeFormatter DATE_FORMAT =
                DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

        public ReportSurvey(String id, String name, String status, int numQuestions, Date dateCreated, int totalResponses) {
            this.id = id;
            this.name = name;
            this.status = status;
            this.numQuestions = numQuestions;
            this.dateCreated = (dateCreated != null) ? DATE_FORMAT.format(dateCreated.toInstant()) : "N/A";
            this.totalResponses = totalResponses;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getStatus() { return status; }
        public int getNumQuestions() { return numQuestions; }
        public String getDateCreated() { return dateCreated; }
        public int getTotalResponses() { return totalResponses; }
    }
    // -----------------------------------------------------------

    // -----------------------------------------------------------
    // Nested Model Class: To hold Question Metadata
    // -----------------------------------------------------------
    private static class QuestionMetadata {
        final String id;
        final String text;
        final String type; // e.g., TEXT_INPUT, MULTI_CHOICE, RATING

        public QuestionMetadata(String id, String text, String type) {
            this.id = id;
            this.text = text;
            this.type = type;
        }

        @Override
        public String toString() {
            // This is what appears in the ComboBox
            return text;
        }
    }
    // -----------------------------------------------------------

    @FXML private TableView<ReportSurvey> surveyReportTable;
    @FXML private TableColumn<ReportSurvey, String> colSurveyName;
    @FXML private TableColumn<ReportSurvey, String> colStatus;
    @FXML private TableColumn<ReportSurvey, Integer> colQuestions;
    @FXML private TableColumn<ReportSurvey, String> colDateCreated;
    @FXML private TableColumn<ReportSurvey, Integer> colTotalResponses;
    @FXML private Button btnViewDetails;
    @FXML private Button btnExportCSV;

    // --- Visualization FXML Bindings (NEW) ---
    @FXML private VBox visualizationPanel;
    @FXML private ComboBox<QuestionMetadata> cbxQuestions;
    @FXML private StackPane chartContainer;
    @FXML private Label lblChartMessage;

    private List<QuestionMetadata> currentSurveyQuestions = new ArrayList<>(); // Questions for the selected survey
    // -----------------------------------------

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

        // Disable buttons initially
        btnViewDetails.setDisable(true);
        if (btnExportCSV != null) {
            btnExportCSV.setDisable(true);
        }
        visualizationPanel.setVisible(false);
        visualizationPanel.setManaged(false);

        // 3. Listener for survey selection (CRITICAL for visualization)
        surveyReportTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            boolean isSelected = newVal != null;
            btnViewDetails.setDisable(!isSelected);

            // Show visualization panel and load questions when a survey is selected
            if (isSelected) {
                loadQuestionsForVisualization(newVal.getId());
                visualizationPanel.setVisible(true);
                visualizationPanel.setManaged(true);
            } else {
                visualizationPanel.setVisible(false);
                visualizationPanel.setManaged(false);
                cbxQuestions.getItems().clear();
                chartContainer.getChildren().clear();
            }
        });
    }

    // --- Visualization Logic START ---

    /**
     * Loads the questions for the selected survey to populate the ComboBox.
     */
    private void loadQuestionsForVisualization(String surveyId) {
        currentSurveyQuestions.clear();
        cbxQuestions.getItems().clear();
        chartContainer.getChildren().clear();
        lblChartMessage.setText("Select a question from the dropdown to see the chart.");

        MongoDatabase db = MongoManager.connect();
        if (db == null) return;

        try {
            MongoCollection<Document> surveyCollection = db.getCollection("surveys");
            Document surveyDoc = surveyCollection.find(Filters.eq("_id", new ObjectId(surveyId))).first();

            if (surveyDoc != null) {
                List<Document> questions = surveyDoc.getList("questions", Document.class, new ArrayList<>());

                // Only visualize questions that are of type MULTI_CHOICE, RATING, or TEXT_INPUT (if we count unique answers)
                for (Document q : questions) {
                    String type = q.getString("type");
                    // We only provide visualization for categorical/rating types initially
                    if ("MULTI_CHOICE".equals(type) || "SINGLE_CHOICE".equals(type) || "RATING".equals(type)) {
                        QuestionMetadata metadata = new QuestionMetadata(
                                q.getString("id"),
                                q.getString("text"),
                                type
                        );
                        currentSurveyQuestions.add(metadata);
                    }
                }
            }

            cbxQuestions.getItems().setAll(currentSurveyQuestions);
            cbxQuestions.getSelectionModel().clearSelection();

        } catch (Exception e) {
            System.err.println("Error loading questions for visualization: " + e.getMessage());
        }
    }

    /**
     * Handles the selection of a question from the ComboBox and generates the chart.
     */
    @FXML
    private void handleQuestionSelected(ActionEvent event) {
        QuestionMetadata selectedQuestion = cbxQuestions.getSelectionModel().getSelectedItem();
        ReportSurvey selectedSurvey = surveyReportTable.getSelectionModel().getSelectedItem();

        if (selectedQuestion == null || selectedSurvey == null) {
            return;
        }

        generateReportChart(selectedSurvey.getId(), selectedQuestion);
    }

    /**
     * Fetches responses for the selected question and generates a PieChart or BarChart.
     */
    private void generateReportChart(String surveyId, QuestionMetadata question) {
        MongoDatabase db = MongoManager.connect();
        if (db == null) return;

        // 1. Fetch responses for the selected survey
        MongoCollection<Document> responseCollection = db.getCollection("responses");
        Map<String, Integer> answerCounts = new HashMap<>();
        int totalResponsesWithAnswer = 0;

        try {
            for (Document responseDoc : responseCollection.find(Filters.eq("survey_id", new ObjectId(surveyId)))) {
                List<Document> answers = responseDoc.getList("answers", Document.class, new ArrayList<>());

                // Find the answer corresponding to the selected question ID
                Optional<Document> answerDocOpt = answers.stream()
                        .filter(a -> question.id.equals(a.getString("question_id")))
                        .findFirst();

                if (answerDocOpt.isPresent()) {
                    Object answerObj = answerDocOpt.get().get("answer");
                    if (answerObj != null) {
                        String answer = answerObj.toString();

                        // Handle MULTI_CHOICE (stored as list/array)
                        if (answer.startsWith("[") && answer.endsWith("]")) {
                            // Simple parsing to split array elements
                            String innerAnswers = answer.substring(1, answer.length() - 1);
                            String[] individualAnswers = innerAnswers.split(",\\s*");
                            for (String item : individualAnswers) {
                                String cleanItem = item.trim().replace("\"", "");
                                if (!cleanItem.isEmpty()) {
                                    answerCounts.merge(cleanItem, 1, Integer::sum);
                                    totalResponsesWithAnswer++; // Count each selected item
                                }
                            }
                        } else {
                            // Handle SINGLE_CHOICE or RATING
                            answerCounts.merge(answer, 1, Integer::sum);
                            totalResponsesWithAnswer++;
                        }
                    }
                }
            }

            // 2. Clear container and display chart
            chartContainer.getChildren().clear();

            if (totalResponsesWithAnswer == 0) {
                lblChartMessage.setText("No responses found for this question.");
                chartContainer.getChildren().add(lblChartMessage);
                return;
            }

            // Determine chart type based on question type
            if ("RATING".equals(question.type)) {
                // RATING: Use BarChart for better comparison of ordered categories
                BarChart<String, Number> barChart = createBarChart(question.text, answerCounts);
                chartContainer.getChildren().add(barChart);
            } else {
                // MULTI_CHOICE/SINGLE_CHOICE: Use PieChart for part-to-whole view
                PieChart pieChart = createPieChart(question.text, answerCounts, totalResponsesWithAnswer);
                chartContainer.getChildren().add(pieChart);
            }

        } catch (Exception e) {
            System.err.println("Error generating report chart: " + e.getMessage());
            lblChartMessage.setText("Error generating chart: " + e.getMessage());
            chartContainer.getChildren().add(lblChartMessage);
        }
    }

    /**
     * Creates a PieChart for categorical data (e.g., Single/Multi Choice).
     */
    private PieChart createPieChart(String title, Map<String, Integer> counts, int total) {
        ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList();

        counts.forEach((label, count) -> {
            double percentage = (double) count / total * 100;
            String displayLabel = String.format("%s (%.1f%%)", label, percentage);
            pieChartData.add(new PieChart.Data(displayLabel, count));
        });

        PieChart chart = new PieChart(pieChartData);
        chart.setTitle(title);
        chart.setLegendVisible(true);
        chart.setClockwise(false);
        chart.setLabelsVisible(false);
        return chart;
    }

    /**
     * Creates a BarChart for ordered data (e.g., Rating).
     */
    private BarChart<String, Number> createBarChart(String title, Map<String, Integer> counts) {
        final CategoryAxis xAxis = new CategoryAxis();
        final NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Count of Responses");

        BarChart<String, Number> barChart = new BarChart<>(xAxis, yAxis);
        barChart.setTitle(title);
        barChart.setLegendVisible(false);

        XYChart.Series<String, Number> series = new XYChart.Series<>();

        // Sort rating keys numerically before adding to the series for proper display order
        List<String> sortedKeys = counts.keySet().stream()
                .sorted(Comparator.comparing(s -> {
                    try {
                        return Integer.parseInt(s);
                    } catch (NumberFormatException e) {
                        return 0; // Fallback for non-numeric keys
                    }
                }))
                .collect(Collectors.toList());

        for (String key : sortedKeys) {
            series.getData().add(new XYChart.Data<>(key, counts.get(key)));
        }

        barChart.getData().add(series);
        return barChart;
    }

    // --- Visualization Logic END ---


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

    // --- CSV Export Implementation (Existing Code) ---
    @FXML
    private void handleExportToCSV(ActionEvent event) {
        if (reportData.isEmpty()) {
            showAlert(Alert.AlertType.INFORMATION, "Export Failed", "The report table is empty. Nothing to export.");
            return;
        }

        Stage stage = (Stage) ((Button) event.getSource()).getScene().getWindow();
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Survey Summary Report");
        fileChooser.setInitialFileName("Survey_Summary_Report.csv");
        fileChooser.getExtensionFilters().add(
                new ExtensionFilter("CSV Files (*.csv)", "*.csv"));

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

    private boolean writeDataToCSV(File file) {
        String header = "Survey ID,Survey Name,Status,Number of Questions,Date Created,Total Responses\n";

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(header);

            for (ReportSurvey survey : reportData) {
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
    // --- End CSV Export Implementation ---


    private Map<String, Integer> getSurveyResponseCounts(MongoDatabase db) {
        Map<String, Integer> counts = new HashMap<>();
        MongoCollection<Document> responseCollection = db.getCollection("responses");

        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(new Document("$group",
                new Document("_id", "$survey_id")
                        .append("count", new Document("$sum", 1))
        ));

        try {
            responseCollection.aggregate(pipeline).forEach(doc -> {
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
            if (btnExportCSV != null) btnExportCSV.setDisable(true);
            return;
        }

        System.out.println("INFO: Database connection established successfully for reports.");

        Bson filter;

        if ("Survey Creator".equals(currentUserRole) && currentUsername != null) {
            filter = Filters.regex("creator", "^" + currentUsername + "$", "i");
            System.out.println("✅ Report RBAC: Creator Filter Applied. User: " + currentUsername);
        } else {
            filter = new Document();
            System.out.println("✅ Report RBAC: Showing ALL surveys. Role: " + currentUserRole);
        }

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
                        surveyId,
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

            DetailedReportController controller = loader.getController();
            controller.initData(selectedSurvey.getId(), selectedSurvey.getName());

            // Replace the current content (ReportController's view) with the detailed view
            // NOTE: This assumes the ReportController's view is contained within an AnchorPane
            // which is itself a child of the main VBox dashboard content container.
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
