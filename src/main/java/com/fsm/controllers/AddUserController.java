package com.fsm.controllers;

import com.fsm.database.MongoManager;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCollection;
import org.bson.Document;

import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import com.mongodb.MongoWriteException; // For handling duplicate username errors

public class AddUserController {

    @FXML private TextField txtUsername;
    @FXML private PasswordField pwdPassword;
    @FXML private ComboBox<String> cmbRole;
    @FXML private Button btnSave;
    @FXML private Button btnCancel;

    private UserController parentController;
    private String originalUsername; // Used later for Edit mode

    @FXML
    public void initialize() {
        // Initialize the role ComboBox options
        cmbRole.getItems().addAll("Administrator", "Survey Creator", "Data Entry");
        cmbRole.setValue("Survey Creator"); // Set a sensible default

        btnSave.setOnAction(e -> handleSave());
        btnCancel.setOnAction(e -> handleCancel());
    }

    // Method to be called by UserController to set the parent reference
    public void setParentController(UserController controller) {
        this.parentController = controller;
    }

    // Placeholder for Edit mode initialization (we'll expand this later)
    public void initData(UserController parent, UserController.User userToEdit) {
        this.setParentController(parent);
        if (userToEdit != null) {
            // Logic for Edit mode goes here later
            this.originalUsername = userToEdit.getUsername();
            txtUsername.setText(userToEdit.getUsername());
            cmbRole.setValue(userToEdit.getRole());
            btnSave.setText("Update User");
            // NOTE: We typically don't pre-fill password fields for security
            pwdPassword.setPromptText("Enter new password (optional)");
        }
    }


    private void handleSave() {
        String username = txtUsername.getText().trim();
        String rawPassword = pwdPassword.getText();
        String role = cmbRole.getValue();

        // --- Validation ---
        if (username.isEmpty() || rawPassword.isEmpty()) {
            System.err.println("Error: Username and Password cannot be empty.");
            // In a real app, show an alert/label
            return;
        }

        // --- Hashing ---
        String hashedPassword = MongoManager.hashPassword(rawPassword);
        if (hashedPassword == null) {
            System.err.println("Error: Failed to hash password.");
            return;
        }

        // 1. Create the MongoDB Document
        Document userDoc = new Document()
                .append("username", username)
                .append("password", hashedPassword)
                .append("role", role);

        // 2. Insert into the Database
        if (insertUserIntoMongo(userDoc)) {
            System.out.println("User created successfully: " + username);

            // 3. Notify the parent to refresh the table
            if (parentController != null) {
                parentController.refreshTable();
            }

            // 4. Close the modal window
            closeWindow();
        }
    }

    private boolean insertUserIntoMongo(Document doc) {
        MongoDatabase db = MongoManager.connect();
        if (db == null) {
            System.err.println("FATAL: Cannot connect to DB to save user.");
            return false;
        }
        try {
            MongoCollection<Document> collection = db.getCollection("users");
            collection.insertOne(doc);
            return true;
        } catch (MongoWriteException e) {
            // Handles unique index violation (duplicate username)
            if (e.getError().getCode() == 11000) {
                System.err.println("MongoDB Insert Error: Duplicate username exists.");
                // In a real app, show an alert: "Username already taken."
            } else {
                System.err.println("MongoDB Insert Error: " + e.getMessage());
            }
            return false;
        } catch (Exception e) {
            System.err.println("General DB Insert Error: " + e.getMessage());
            return false;
        }
    }

    private void handleCancel() {
        closeWindow();
    }

    private void closeWindow() {
        Stage stage = (Stage) btnCancel.getScene().getWindow();
        stage.close();
    }
}