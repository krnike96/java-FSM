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
import com.mongodb.MongoWriteException;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;
import com.mongodb.client.result.UpdateResult;

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

        // If a user is passed, enter EDIT mode
        if (userToEdit != null) {
            this.originalUsername = userToEdit.getUsername();

            // 1. Pre-fill the form fields
            txtUsername.setText(userToEdit.getUsername());
            cmbRole.setValue(userToEdit.getRole());

            // 2. Adjust fields/button for EDIT mode
            btnSave.setText("Update User");
            // Passwords are NOT pre-filled. Prompt for optional change.
            pwdPassword.setPromptText("Enter new password (optional change)");
        }
    }

    private void handleSave() {
        String newUsername = txtUsername.getText().trim();
        String rawPassword = pwdPassword.getText(); // Might be empty/null in edit mode
        String newRole = cmbRole.getValue();

        // --- Validation ---
        if (newUsername.isEmpty()) {
            System.err.println("Error: Username cannot be empty.");
            return;
        }

        // Check if we are in EDIT mode or ADD mode
        if (originalUsername != null) {
            // ================== EDIT MODE ==================
            if (updateUserInMongo(newUsername, rawPassword, newRole)) {
                System.out.println("User updated successfully: " + newUsername);
            } else {
                // Error handled within update method, return
                return;
            }
        } else {
            // =================== ADD MODE ===================
            if (rawPassword.isEmpty()) {
                System.err.println("Error: Password cannot be empty when adding a new user.");
                return;
            }

            // CRITICAL FIX: Call the secure hashPassword method
            String hashedPassword = MongoManager.hashPassword(rawPassword);
            if (hashedPassword == null) return;

            Document userDoc = new Document()
                    .append("username", newUsername)
                    .append("password", hashedPassword) // Store the BCrypt hash
                    .append("role", newRole);

            if (!insertUserIntoMongo(userDoc)) {
                // Error handled within insert method, return
                return;
            }
            System.out.println("User created successfully: " + newUsername);
        }

        // Final steps are the same for both
        if (parentController != null) {
            parentController.refreshTable();
        }
        closeWindow();
    }

    private boolean updateUserInMongo(String newUsername, String rawPassword, String newRole) {
        MongoDatabase db = MongoManager.connect();
        if (db == null) {
            System.err.println("FATAL: Cannot connect to DB to update user.");
            return false;
        }

        try {
            MongoCollection<Document> collection = db.getCollection("users");

            // 1. Define the filter (find the document by its ORIGINAL username)
            Bson filter = Filters.eq("username", originalUsername);

            // 2. Define the updates
            // Start with username and role updates
            Bson updateOps = Updates.combine(
                    Updates.set("username", newUsername),
                    Updates.set("role", newRole)
            );

            // 3. Conditional Password Update
            // ONLY update password if the rawPassword field is NOT empty
            if (rawPassword != null && !rawPassword.trim().isEmpty()) {
                // CRITICAL FIX: Hash the new password before updating
                String newHashedPassword = MongoManager.hashPassword(rawPassword);
                if (newHashedPassword != null) {
                    // Combine the existing updates with the password update
                    updateOps = Updates.combine(updateOps, Updates.set("password", newHashedPassword));
                }
            }

            // 4. Perform the update
            UpdateResult result = collection.updateOne(filter, updateOps);

            if (result.getMatchedCount() == 0) {
                System.err.println("Update failed: User not found with username " + originalUsername);
                return false;
            }

            return true;
        } catch (MongoWriteException e) {
            if (e.getError().getCode() == 11000) {
                // This catches if the user tries to rename their username to an existing one
                System.err.println("MongoDB Update Error: New username '" + newUsername + "' already exists.");
            } else {
                System.err.println("MongoDB Update Error: " + e.getMessage());
            }
            return false;
        } catch (Exception e) {
            System.err.println("General DB Update Error: " + e.getMessage());
            return false;
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
