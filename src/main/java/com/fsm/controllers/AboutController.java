package com.fsm.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.stage.Stage;

public class AboutController {

    @FXML private Button btnClose;

    /**
     * Handles the Close button click to shut down the modal window.
     */
    @FXML
    private void handleCloseButton() {
        // Get the current stage using the button's scene
        Stage stage = (Stage) btnClose.getScene().getWindow();
        stage.close();
    }
}
