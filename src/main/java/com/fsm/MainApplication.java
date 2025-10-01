package com.fsm;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class MainApplication extends Application {

    // The FXML file path must match your package structure
    private static final String FXML_PATH = "/com/fsm/login-view.fxml";

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(MainApplication.class.getResource(FXML_PATH));
        Scene scene = new Scene(fxmlLoader.load(), 400, 300);
        stage.setTitle("Field Survey Manager - Login");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}