package com.fsm.controllers;

import com.fsm.database.MongoManager;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.bson.Document;

public class ProfileSettingsController {

    @FXML private Label lblCurrentUsername;
    @FXML private TextField txtNewUsername;
    @FXML private PasswordField pwdCurrentForUsername;
    @FXML private Button btnUpdateUsername;

    @FXML private PasswordField pwdCurrentForPassword;
    @FXML private PasswordField pwdNewPassword;
    @FXML private PasswordField pwdConfirmNewPassword;
    @FXML private Button btnUpdatePassword;

    @FXML private Label lblMessage;

    private String currentUsername;
    private String currentUserRole;

    // Original (read-only) user document, potentially holding the hashed password
    private Document userDocument;

    /**
     * Called by MainDashboardController to set the logged-in user context.
     * @param username The username of the logged-in user.
     * @param role The role of the logged-in user.
     */
    public void initData(String username, String role) {
        this.currentUsername = username;
        this.currentUserRole = role;
        lblCurrentUsername.setText("Logged in as: " + username + " (Role: " + role + ")");
        txtNewUsername.setText(username);

        // 1. Fetch the full user document from Mongo to get the stored password hash
        fetchUserDocument(username);
    }

    /**
     * Fetches the user's document from the database based on the username.
     * This is required to access the stored password hash for verification.
     */
    private void fetchUserDocument(String username) {
        System.out.println("DEBUG: Fetching user document for " + username + " to get password hash.");
        this.userDocument = MongoManager.findUserByUsername(username);

        if (this.userDocument == null) {
            setMessage("FATAL: Could not retrieve user profile data.", false);
            btnUpdateUsername.setDisable(true);
            btnUpdatePassword.setDisable(true);
        }
    }

    // Helper method to display status messages
    private void setMessage(String message, boolean isSuccess) {
        lblMessage.setText(message);
        lblMessage.setVisible(true);
        String color = isSuccess ? "-fx-text-fill: #4CAF50;" : "-fx-text-fill: #E53935;";
        lblMessage.setStyle(color + "-fx-font-size: 14px;");
    }

    @FXML
    private void handleUpdateUsername() {
        lblMessage.setVisible(false);

        String newUsername = txtNewUsername.getText().trim();
        String currentPassword = pwdCurrentForUsername.getText();

        if (newUsername.isEmpty() || newUsername.equals(currentUsername)) {
            setMessage("New username cannot be empty or the same as the current one.", false);
            return;
        }

        if (currentPassword.isEmpty()) {
            setMessage("You must confirm your current password to change your username.", false);
            return;
        }

        // Security Check 1: Verify current password hash
        if (userDocument == null || !MongoManager.checkPassword(currentPassword, userDocument.getString("password"))) {
            setMessage("Current password confirmation failed. Update aborted.", false);
            return;
        }

        // Security Check 2: Ensure the new username is not already taken
        if (MongoManager.findUserByUsername(newUsername) != null) {
            setMessage("Username '" + newUsername + "' is already taken. Please choose another.", false);
            return;
        }

        // Database Update Logic (Requires MongoManager.updateUserUsername)
        if (MongoManager.updateUserUsername(currentUsername, newUsername)) {
            setMessage("Username successfully updated! You will need to log in with your new username next time.", true);
            // Update local state and UI
            this.currentUsername = newUsername;
            lblCurrentUsername.setText("Logged in as: " + newUsername + " (Role: " + currentUserRole + ")");
            pwdCurrentForUsername.clear();

            // NOTE: The user will not be logged out immediately, but the change is in the DB.
        } else {
            setMessage("Failed to update username due to a database error.", false);
        }
    }

    @FXML
    private void handleUpdatePassword() {
        lblMessage.setVisible(false);

        String currentPassword = pwdCurrentForPassword.getText();
        String newPassword = pwdNewPassword.getText();
        String confirmNewPassword = pwdConfirmNewPassword.getText();

        if (currentPassword.isEmpty() || newPassword.isEmpty() || confirmNewPassword.isEmpty()) {
            setMessage("All password fields are required.", false);
            return;
        }

        if (newPassword.length() < 6) {
            setMessage("New password must be at least 6 characters long.", false);
            return;
        }

        if (!newPassword.equals(confirmNewPassword)) {
            setMessage("New password and confirmation do not match.", false);
            return;
        }

        if (newPassword.equals(currentPassword)) {
            setMessage("New password cannot be the same as the current password.", false);
            return;
        }

        // Security Check 1: Verify current password hash
        if (userDocument == null || !MongoManager.checkPassword(currentPassword, userDocument.getString("password"))) {
            setMessage("Current password confirmation failed. Update aborted.", false);
            return;
        }

        // Database Update Logic (Requires MongoManager.updateUserPassword)
        String newHashedPassword = MongoManager.hashPassword(newPassword); // Re-hash the new password

        if (MongoManager.updateUserPassword(currentUsername, newHashedPassword)) {
            setMessage("Password successfully updated!", true);
            // Clear all password fields upon success
            pwdCurrentForPassword.clear();
            pwdNewPassword.clear();
            pwdConfirmNewPassword.clear();

            // Update the local user document with the new hash for subsequent operations
            userDocument.put("password", newHashedPassword);
        } else {
            setMessage("Failed to update password due to a database error.", false);
        }
    }
}
