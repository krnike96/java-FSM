package com.fsm.controllers;

import com.fsm.database.MongoManager;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.MongoException;
import org.bson.Document;

import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Button;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
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
    private String originalUsername;      // The username before editing (null in add mode)
    private String originalRole;          // The role before editing (null in add mode)
    private String currentLoggedInUsername; // The username of the currently logged-in admin

    private static final int MAX_ADMINS = 3; // Enforces the admin limit

    @FXML
    public void initialize() {
        cmbRole.getItems().addAll("Administrator", "Survey Creator", "Data Entry");
        cmbRole.setValue("Survey Creator");

        btnSave.setOnAction(e -> handleSave());
        btnCancel.setOnAction(e -> handleCancel());
    }

    // Setter for the user performing the action (the logged-in admin)
    public void setCurrentLoggedInUsername(String username) {
        this.currentLoggedInUsername = username;
    }

    public void setParentController(UserController controller) {
        this.parentController = controller;
    }

    // Method for Edit mode initialization
    public void initData(UserController parent, UserController.User userToEdit) {
        this.setParentController(parent);

        // If a user is passed, enter EDIT mode
        if (userToEdit != null) {
            this.originalUsername = userToEdit.getUsername();
            this.originalRole = userToEdit.getRole(); // <-- FIX: Store original role for demotion check

            txtUsername.setText(userToEdit.getUsername());
            cmbRole.setValue(userToEdit.getRole());

            btnSave.setText("Update User");
            pwdPassword.setPromptText("Enter new password (optional change)");
        }
    }

    private void handleSave() {
        String newUsername = txtUsername.getText().trim();
        String rawPassword = pwdPassword.getText();
        String newRole = cmbRole.getValue();
        boolean isEditMode = originalUsername != null;

        // --- Validation ---
        if (newUsername.isEmpty()) {
            showAlert(AlertType.ERROR, "Input Error", "Username cannot be empty.");
            return;
        }

        if (isEditMode) {
            // ================== EDIT MODE ==================

            // --- 1. Security Check: Self-Demotion Prevention (Bug Fix) ---
            if (originalUsername.equals(currentLoggedInUsername) &&
                    "Administrator".equals(originalRole) &&
                    !"Administrator".equals(newRole)) {

                showAlert(AlertType.ERROR, "Security Violation",
                        "You cannot demote yourself from the Administrator role. Another Administrator must perform this action.");
                return;
            }

            // --- 2. Security Check: Admin Limit (if promoting) ---
            if (!"Administrator".equals(originalRole) && "Administrator".equals(newRole)) {
                if (countAdministrators() >= MAX_ADMINS) {
                    showAlert(AlertType.ERROR, "Security Policy",
                            "The maximum limit of " + MAX_ADMINS + " Administrators has been reached. Cannot promote this user.");
                    return;
                }
            }

            // --- 3. Security Check: Duplicate Username on Rename ---
            // The isUsernameTaken check is now case-insensitive.
            if (!originalUsername.equalsIgnoreCase(newUsername) && isUsernameTaken(newUsername)) {
                showAlert(AlertType.ERROR, "Update Failed",
                        "Update Failed: The new username '" + newUsername + "' is already in use by another user.");
                return;
            }


            if (updateUserInMongo(newUsername, rawPassword, newRole)) {
                System.out.println("User updated successfully: " + newUsername);
            } else {
                return; // Error handled within update method
            }

        } else {
            // =================== ADD MODE ===================
            if (rawPassword.isEmpty()) {
                showAlert(AlertType.ERROR, "Input Error", "Password cannot be empty when adding a new user.");
                return;
            }

            // --- 0. Security Check: Duplicate Username Pre-Check (FIX) ---
            // The isUsernameTaken check is now case-insensitive.
            if (isUsernameTaken(newUsername)) {
                showAlert(AlertType.ERROR, "User Creation Failed",
                        "The username '" + newUsername + "' is already taken. Please choose a different one.");
                return;
            }

            // --- 1. Security Check: Admin Limit (if creating an Admin) ---
            if ("Administrator".equals(newRole)) {
                if (countAdministrators() >= MAX_ADMINS) {
                    showAlert(AlertType.ERROR, "Security Policy",
                            "The maximum limit of " + MAX_ADMINS + " Administrators has been reached. Cannot create a new Administrator.");
                    return;
                }
            }

            String hashedPassword = MongoManager.hashPassword(rawPassword);
            if (hashedPassword == null) return;

            Document userDoc = new Document()
                    .append("username", newUsername)
                    .append("password", hashedPassword)
                    .append("role", newRole);

            if (!insertUserIntoMongo(userDoc, newUsername)) { // <-- Passing newUsername for alert
                return; // Error handled within insert method
            }
            System.out.println("User created successfully: " + newUsername);
        }

        // Final steps
        if (parentController != null) {
            parentController.refreshTable();
        }
        closeWindow();
    }

    private boolean updateUserInMongo(String newUsername, String rawPassword, String newRole) {
        MongoDatabase db = MongoManager.connect();
        if (db == null) {
            showAlert(AlertType.ERROR, "Database Error", "FATAL: Cannot connect to DB to update user.");
            return false;
        }

        try {
            MongoCollection<Document> collection = db.getCollection("users");

            Bson filter = Filters.eq("username", originalUsername);

            // Start with username and role updates
            Bson updateOps = Updates.combine(
                    Updates.set("username", newUsername),
                    Updates.set("role", newRole)
            );

            // Conditional Password Update
            if (rawPassword != null && !rawPassword.trim().isEmpty()) {
                String newHashedPassword = MongoManager.hashPassword(rawPassword);
                if (newHashedPassword != null) {
                    updateOps = Updates.combine(updateOps, Updates.set("password", newHashedPassword));
                }
            }

            UpdateResult result = collection.updateOne(filter, updateOps);

            if (result.getMatchedCount() == 0) {
                showAlert(AlertType.ERROR, "Update Failed", "Update failed: User not found with username " + originalUsername);
                return false;
            }

            return true;
        } catch (MongoWriteException e) {
            // NOTE: We still catch 11000 here just in case the check fails, or we have an index!
            if (e.getError().getCode() == 11000) {
                // Catches if the user tries to rename their username to an existing one
                showAlert(AlertType.ERROR, "Update Failed",
                        "Update Failed: The new username '" + newUsername + "' is already in use by another user.");
            } else {
                showAlert(AlertType.ERROR, "Database Error", "MongoDB Update Error: " + e.getMessage());
            }
            return false;
        } catch (Exception e) {
            showAlert(AlertType.ERROR, "General Error", "General DB Update Error: " + e.getMessage());
            return false;
        }
    }

    private boolean insertUserIntoMongo(Document doc, String newUsername) {
        MongoDatabase db = MongoManager.connect();
        if (db == null) {
            showAlert(AlertType.ERROR, "Database Error", "FATAL: Cannot connect to DB to save user.");
            return false;
        }
        try {
            MongoCollection<Document> collection = db.getCollection("users");
            collection.insertOne(doc);
            return true;
        } catch (MongoWriteException e) {
            // Handles unique index violation (duplicate username) - Kept as a fallback
            if (e.getError().getCode() == 11000) {
                showAlert(AlertType.ERROR, "User Creation Failed",
                        "The username '" + newUsername + "' is already taken. Please choose a different one.");
            } else {
                System.err.println("MongoDB Insert Error: " + e.getMessage());
                showAlert(AlertType.ERROR, "Database Error", "Failed to create user due to a database issue.");
            }
            return false;
        } catch (Exception e) {
            System.err.println("General DB Insert Error: " + e.getMessage());
            showAlert(AlertType.ERROR, "General Error", "An unexpected error occurred during user creation.");
            return false;
        }
    }

    /**
     * Helper to count existing Administrators.
     */
    private int countAdministrators() {
        MongoDatabase db = MongoManager.connect();
        if (db == null) return MAX_ADMINS; // Assume max reached if connection fails

        try {
            MongoCollection<Document> collection = db.getCollection("users");
            long count = collection.countDocuments(Filters.eq("role", "Administrator"));
            return (int) count;
        } catch (MongoException e) {
            System.err.println("Error counting admins: " + e.getMessage());
            return MAX_ADMINS;
        }
    }

    /**
     * Checks if a given username already exists in the database.
     * This is a pre-check to prevent duplicate entry issues.
     * FIX: Uses a case-insensitive regex match to catch duplicates like "admin" and "Admin".
     */
    private boolean isUsernameTaken(String username) {
        MongoDatabase db = MongoManager.connect();
        // If connection fails, assume it's taken to prevent accidental creation
        if (db == null) {
            System.err.println("Database connection failed during username check.");
            return true;
        }

        try {
            MongoCollection<Document> collection = db.getCollection("users");

            // Use case-insensitive regex query to find any matching username
            Bson filter = Filters.regex("username", "^" + java.util.regex.Pattern.quote(username) + "$", "i");

            long count = collection.countDocuments(filter);
            return count > 0;
        } catch (MongoException e) {
            System.err.println("Error checking for duplicate username: " + e.getMessage());
            // Conservative approach on error: block the creation
            return true;
        }
    }

    private void handleCancel() {
        closeWindow();
    }

    private void closeWindow() {
        Stage stage = (Stage) btnCancel.getScene().getWindow();
        stage.close();
    }

    /**
     * Helper method to show JavaFX alerts easily.
     */
    private void showAlert(AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
