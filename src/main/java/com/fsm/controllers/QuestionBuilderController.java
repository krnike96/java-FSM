package com.fsm.controllers;

import com.fsm.database.MongoManager;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.stage.Stage;

public class QuestionBuilderController {

    // FXML elements will go here later

    // Reference to the parent controller and the survey being edited
    private SurveyController parentController;
    private SurveyController.Survey currentSurvey;

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
    }

    // Placeholder for saving the questions
    @FXML
    private void handleSaveQuestions() {
        // Logic to save the array of questions to MongoDB goes here

        // After saving, close the window and optionally refresh parent
        if (parentController != null) {
            parentController.refreshTable(); // Refresh the main table
        }
        closeWindow();
    }

    private void closeWindow() {
        // Get the stage (window) from any FXML element once they are defined
        // For now, we'll assume a button exists to get the stage.
        // Stage stage = (Stage) btnSave.getScene().getWindow();
        // stage.close();
    }
}