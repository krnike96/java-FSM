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

        // Note: We avoid passing the password here for security

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

    @FXML
    public void initialize() {
        // 1. Link the model properties to the TableView columns
        colUsername.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getUsername()));
        colRole.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getRole()));

        // 2. Load data from MongoDB
        loadUserData();

        // 3. Set up button handlers (placeholders for now)
        btnAddUser.setOnAction(event -> handleAddUser());
        btnEditUser.setOnAction(event -> handleEditUser());
        btnDeleteUser.setOnAction(event -> handleDeleteUser());
    }

    public void refreshTable() {
        loadUserData();
    }

    private void loadUserData() {
        masterData.clear();
        MongoDatabase db = MongoManager.connect();

        if (db == null) {
            System.err.println("Database connection failed. Cannot load user data.");
            return;
        }

        try {
            MongoCollection<Document> userCollection = db.getCollection("users");

            // Iterate over all users in the collection
            for (Document doc : userCollection.find()) {
                String username = doc.getString("username");
                String role = doc.getString("role");

                masterData.add(new User(username, role));
            }

            userTable.setItems(masterData);

        } catch (MongoException e) {
            System.err.println("Error loading user data from MongoDB: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("General error loading user data: " + e.getMessage());
        }
    }

    // --- Placeholder Button Handlers ---

    private void handleAddUser() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/fsm/add-user-form.fxml"));
            Parent root = loader.load();

            // Get the controller and pass a reference to THIS controller
            AddUserController addController = loader.getController();
            addController.setParentController(this);

            // Create the new modal stage (window)
            Stage stage = new Stage();
            stage.setTitle("Add New User");
            stage.initModality(Modality.APPLICATION_MODAL);

            stage.setScene(new Scene(root));
            stage.showAndWait(); // Wait until the form is closed

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Error loading Add User form: " + e.getMessage());
        }
    }

    private void handleEditUser() {
        // Note: Use the fully qualified name for the inner class
        UserController.User selectedUser = userTable.getSelectionModel().getSelectedItem();

        if (selectedUser == null) {
            // Use the same warning as delete if nothing is selected
            Alert alert = new Alert(AlertType.WARNING);
            alert.setTitle("No Selection");
            alert.setHeaderText("No User Selected");
            alert.setContentText("Please select a user from the table to edit.");
            alert.showAndWait();
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/fsm/add-user-form.fxml"));
            Parent root = loader.load();

            AddUserController editController = loader.getController();

            // CRITICAL STEP: Pass the selected user data to the controller for editing
            // The initData method will handle pre-filling the form.
            editController.initData(this, selectedUser);

            // Create the new modal stage (window)
            Stage stage = new Stage();
            stage.setTitle("Edit User: " + selectedUser.getUsername()); // Set dynamic title
            stage.initModality(Modality.APPLICATION_MODAL);

            stage.setScene(new Scene(root));
            stage.showAndWait(); // Wait until the form is closed

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Error loading Edit User form: " + e.getMessage());
        }
    }

    private void handleDeleteUser() {
        // Note: We must use the fully qualified name for the inner class
        UserController.User selectedUser = userTable.getSelectionModel().getSelectedItem();

        if (selectedUser == null) {
            Alert alert = new Alert(AlertType.WARNING);
            alert.setTitle("No Selection");
            alert.setHeaderText("No User Selected");
            alert.setContentText("Please select a user from the table to delete.");
            alert.showAndWait();
            return;
        }

        // Safety Check: Prevent deleting the primary admin account (or logged-in user)
        if (selectedUser.getUsername().equalsIgnoreCase("admin")) {
            Alert alert = new Alert(AlertType.ERROR);
            alert.setTitle("Deletion Denied");
            alert.setHeaderText("Cannot Delete Administrator");
            alert.setContentText("The primary 'admin' account cannot be deleted.");
            alert.showAndWait();
            return;
        }

        // 1. Show Confirmation Dialog
        Alert confirmAlert = new Alert(AlertType.CONFIRMATION);
        confirmAlert.setTitle("Confirm Deletion");
        confirmAlert.setHeaderText("Permanently Delete User: " + selectedUser.getUsername() + "?");
        confirmAlert.setContentText("Are you sure you want to delete this user? This action cannot be undone.");

        Optional<ButtonType> result = confirmAlert.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.OK) {
            // 2. Perform MongoDB Deletion
            if (deleteUserFromMongo(selectedUser.getUsername())) {
                // 3. Refresh the Table
                refreshTable();
                System.out.println("SUCCESS: User deleted: " + selectedUser.getUsername());
            } else {
                // Display error if DB operation fails
                Alert errorAlert = new Alert(AlertType.ERROR);
                errorAlert.setTitle("Deletion Failed");
                errorAlert.setHeaderText("Database Error");
                errorAlert.setContentText("Failed to delete user from the database.");
                errorAlert.showAndWait();
            }
        }
    }

    /**
     * Executes the MongoDB delete operation.
     * @param username The username of the user to delete.
     * @return true if deletion was successful, false otherwise.
     */
    private boolean deleteUserFromMongo(String username) {
        MongoDatabase db = MongoManager.connect();
        if (db == null) {
            return false; // Connection failed
        }

        try {
            MongoCollection<Document> collection = db.getCollection("users");

            // Delete the document where the 'username' field matches
            collection.deleteOne(Filters.eq("username", username));

            return true;

        } catch (MongoException e) {
            System.err.println("MongoDB Delete Error: " + e.getMessage());
            return false;
        }
    }
}