package com.fsm.controllers;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.MongoException;
import org.bson.Document;
import com.fsm.database.MongoManager;

import javafx.fxml.FXML;
import javafx.scene.control.TableView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.beans.property.SimpleStringProperty;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import java.io.IOException;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import com.mongodb.client.model.Filters;
import java.util.Optional;

public class UserController {

    public static class User {
        private String username;
        private String role;

        public User(String username, String role) {
            this.username = username;
            this.role = role;
        }

        // Getters and Setters (REQUIRED for TableView)
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
    }

    @FXML private TableView<User> userTable;
    @FXML private TableColumn<User, String> colUsername;
    @FXML private TableColumn<User, String> colRole;
    @FXML private Button btnAddUser;
    @FXML private Button btnEditUser;
    @FXML private Button btnDeleteUser;

    private final ObservableList<User> masterData = FXCollections.observableArrayList();

    private String currentUserRole;
    private String currentLoggedInUsername;

    /**
     * CRITICAL: Method called by MainDashboardController to set up RBAC
     * and load data. Requires both role and username.
     * @param userRole The role of the currently logged-in user.
     * @param username The username of the currently logged-in user.
     */
    public void initData(String userRole, String username) {
        this.currentUserRole = userRole;
        this.currentLoggedInUsername = username;

        applyRoleRestrictions();
        loadUserData();
    }

    /**
     * Hides or disables modification buttons for non-administrator users.
     */
    private void applyRoleRestrictions() {
        boolean isAdmin = "Administrator".equals(currentUserRole);

        if (!isAdmin) {
            // Disable all modification buttons for security
            btnAddUser.setDisable(true);
            btnEditUser.setDisable(true);
            btnDeleteUser.setDisable(true);

            System.out.println("User view: Modification buttons disabled for role: " + currentUserRole);
        }
    }

    @FXML
    public void initialize() {
        colUsername.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getUsername()));
        colRole.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getRole()));
    }

    public void refreshTable() {
        loadUserData();
    }

    /**
     * FIX: Loads user data using the efficient Singleton database connection.
     */
    private void loadUserData() {
        masterData.clear();
        // PERFORMANCE FIX: Use the Singleton instance to get the shared database connection
        MongoDatabase db = MongoManager.getInstance().getDatabase();
        userTable.setItems(masterData);

        if (db == null) {
            System.err.println("Database connection failed. Cannot load user data.");
            return;
        }

        try {
            MongoCollection<Document> userCollection = db.getCollection("users");

            for (Document doc : userCollection.find()) {
                String username = doc.getString("username");
                String role = doc.getString("role");

                masterData.add(new User(username, role));
            }

        } catch (MongoException e) {
            System.err.println("Error loading user data from MongoDB: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("General error loading user data: " + e.getMessage());
        }
    }

    // --- Button Handlers (MUST BE @FXML ANNOTATED) ---

    /**
     * Handles the Add User button action.
     */
    @FXML
    private void handleAddUser() {
        if (!"Administrator".equals(currentUserRole)) {
            showAlert(AlertType.ERROR, "Access Denied", "Only Administrators can add users.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/fsm/add-user-form.fxml"));
            Parent root = loader.load();

            AddUserController addController = loader.getController();
            addController.setParentController(this);
            addController.setCurrentLoggedInUsername(this.currentLoggedInUsername);

            Stage stage = new Stage();
            stage.setTitle("Add New User");
            stage.initModality(Modality.APPLICATION_MODAL);

            stage.setScene(new Scene(root));
            stage.showAndWait();

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Error loading Add User form: " + e.getMessage());
        }
    }

    /**
     * Handles the Edit User button action.
     */
    @FXML
    private void handleEditUser() {
        if (!"Administrator".equals(currentUserRole)) {
            showAlert(AlertType.ERROR, "Access Denied", "Only Administrators can edit users.");
            return;
        }

        UserController.User selectedUser = userTable.getSelectionModel().getSelectedItem();

        if (selectedUser == null) {
            showAlert(AlertType.WARNING, "No Selection", "Please select a user from the table to edit.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/fsm/add-user-form.fxml"));
            Parent root = loader.load();

            AddUserController editController = loader.getController();

            editController.initData(this, selectedUser);
            editController.setCurrentLoggedInUsername(this.currentLoggedInUsername);

            Stage stage = new Stage();
            stage.setTitle("Edit User: " + selectedUser.getUsername());
            stage.initModality(Modality.APPLICATION_MODAL);

            stage.setScene(new Scene(root));
            stage.showAndWait();

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Error loading Edit User form: " + e.getMessage());
        }
    }

    /**
     * Handles the Delete User button action.
     */
    @FXML
    private void handleDeleteUser() {
        if (!"Administrator".equals(currentUserRole)) {
            showAlert(AlertType.ERROR, "Access Denied", "Only Administrators can delete users.");
            return;
        }

        UserController.User selectedUser = userTable.getSelectionModel().getSelectedItem();

        if (selectedUser == null) {
            showAlert(AlertType.WARNING, "No Selection", "Please select a user from the table to delete.");
            return;
        }

        // Security Check 1: Prevent the logged-in user from deleting themselves
        if (selectedUser.getUsername().equals(currentLoggedInUsername)) {
            showAlert(AlertType.ERROR, "Deletion Denied", "You cannot delete your own user account.");
            return;
        }

        // Security Check 2: Prevent deleting the primary admin account
        if (selectedUser.getUsername().equalsIgnoreCase("admin")) {
            showAlert(AlertType.ERROR, "Deletion Denied", "The primary 'admin' account cannot be deleted.");
            return;
        }


        // Show Confirmation Dialog
        Alert confirmAlert = new Alert(AlertType.CONFIRMATION);
        confirmAlert.setTitle("Confirm Deletion");
        confirmAlert.setHeaderText("Permanently Delete User: " + selectedUser.getUsername() + "?");
        confirmAlert.setContentText("Are you sure you want to delete this user? This action cannot be undone.");

        Optional<ButtonType> result = confirmAlert.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.OK) {
            if (deleteUserFromMongo(selectedUser.getUsername())) {
                refreshTable();
                System.out.println("SUCCESS: User deleted: " + selectedUser.getUsername());
            } else {
                showAlert(AlertType.ERROR, "Deletion Failed", "Failed to delete user from the database.");
            }
        }
    }

    /**
     * Executes the MongoDB delete operation using the Singleton connection.
     */
    private boolean deleteUserFromMongo(String username) {
        // PERFORMANCE FIX: Use the Singleton instance to get the shared database connection
        MongoDatabase db = MongoManager.getInstance().getDatabase();
        if (db == null) {
            return false;
        }

        try {
            MongoCollection<Document> collection = db.getCollection("users");
            collection.deleteOne(Filters.eq("username", username));
            return true;
        } catch (MongoException e) {
            System.err.println("MongoDB Delete Error: " + e.getMessage());
            return false;
        }
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
