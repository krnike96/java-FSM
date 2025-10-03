package com.fsm.database;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.mindrot.jbcrypt.BCrypt;

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
     * Hashes a plain text password using BCrypt (Salted and slow for security).
     * @param password The plain text password.
     * @return The BCrypt hashed password string.
     */
    public static String hashPassword(String password) {
        if (password == null) return null;
        // Generate a salt and hash the password in one step
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }

    /**
     * Authenticates a user against the 'users' collection by verifying the plain password
     * against the stored BCrypt hash.
     * @param username The username entered by the user.
     * @param password The password entered by the user (plain text).
     * @return A Document representing the user if login is successful, or null otherwise.
     */
    public static Document authenticateUser(String username, String password) {
        MongoDatabase db = connect();

        if (db == null) {
            return null;
        }

        try {
            MongoCollection<Document> userCollection = db.getCollection("users");

            // STEP 1: Find user by username only
            Document userDoc = userCollection.find(Filters.eq("username", username)).first();

            if (userDoc != null) {
                String storedHashedPassword = userDoc.getString("password");

                // STEP 2: CRITICAL FIX: Use BCrypt.checkpw to safely compare the plain password
                // with the stored hash.
                if (BCrypt.checkpw(password, storedHashedPassword)) {
                    System.out.println("✅ Login Successful for user: " + username);
                    return userDoc; // Authentication successful
                }
            }

            // If user is null or password verification failed:
            System.out.println("❌ Login Failed: Invalid credentials for user: " + username);
            return null;

        } catch (Exception e) {
            System.err.println("Database query error during authentication: " + e.getMessage());
            return null;
        }
    }
}
