package com.fsm.database;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.bson.Document;

public class MongoManager {

    private static final String CONNECTION_STRING = "mongodb://localhost:27017/";
    private static final String DATABASE_NAME = "FieldSurveyDB";

    /**
     * Establishes a connection to MongoDB and returns the database object.
     * @return The MongoDatabase object, or null if connection fails.
     */
    public static MongoDatabase connect() {
        try {
            // Note: MongoClients.create() establishes the connection
            MongoClient mongoClient = MongoClients.create(CONNECTION_STRING);
            return mongoClient.getDatabase(DATABASE_NAME);
        } catch (Exception e) {
            System.err.println("❌ ERROR connecting to MongoDB: " + e.getMessage());
            return null;
        }
    }

    /**
     * Authenticates a user against the 'users' collection using exact match for username and password.
     * @param username The username entered by the user.
     * @param password The password entered by the user.
     * @return A Document representing the user if login is successful, or null otherwise.
     */
    public static Document authenticateUser(String username, String password) {
        MongoDatabase db = connect();

        if (db == null) {
            return null;
        }

        try {
            MongoCollection<Document> userCollection = db.getCollection("users");

            // Build a filter to find a user where both fields match
            Document user = userCollection.find(Filters.and(
                    Filters.eq("username", username),
                    Filters.eq("password", password)
            )).first(); // .first() returns the first match or null

            if (user != null) {
                System.out.println("✅ Login Successful for user: " + username);
            } else {
                System.out.println("❌ Login Failed: Invalid credentials for user: " + username);
            }
            return user;

        } catch (Exception e) {
            System.err.println("Database query error during authentication: " + e.getMessage());
            return null;
        }
    }
}