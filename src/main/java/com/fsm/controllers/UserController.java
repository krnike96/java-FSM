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
        System.out.println("Add User button clicked. (TODO)");
    }

    private void handleEditUser() {
        User selectedUser = userTable.getSelectionModel().getSelectedItem();
        if (selectedUser != null) {
            System.out.println("Edit User: " + selectedUser.getUsername());
        } else {
            System.out.println("No user selected for editing.");
        }
    }

    private void handleDeleteUser() {
        User selectedUser = userTable.getSelectionModel().getSelectedItem();
        if (selectedUser != null) {
            System.out.println("Delete User: " + selectedUser.getUsername());
        } else {
            System.out.println("No user selected for deletion.");
        }
    }
}