package com.fsm.database;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.mindrot.jbcrypt.BCrypt;

// New imports for combining filters
import org.bson.conversions.Bson;
import io.github.cdimascio.dotenv.Dotenv; // ADDED: Import for reading .env file

public class MongoManager {

    // 1. Singleton Instance Holder
    private static MongoManager instance;

    // 2. The Singleton MongoClient
    private final MongoClient mongoClient;
    private final MongoDatabase database;

    // Will hold the name of the database loaded from .env
    private static String databaseName;

    /**
     * Private constructor to prevent instantiation from outside the class.
     * Establishes the single MongoClient connection by reading configurations from the .env file.
     */
    private MongoManager() {
        MongoClient client = null;
        MongoDatabase db = null;
        String connectionString = null;

        try {
            // Load environment variables from .env file (requires dotenv-java dependency)
            Dotenv dotenv = Dotenv.load();

            connectionString = dotenv.get("MONGO_CONNECTION_STRING");
            String dbName = dotenv.get("DATABASE_NAME");

            if (connectionString == null || connectionString.isEmpty()) {
                throw new IllegalStateException("FATAL ERROR: MONGO_URI is not set in the .env file.");
            }
            if (dbName == null || dbName.isEmpty()) {
                // Use a sensible default if the DB name isn't specified, but warn
                dbName = "FieldSurveyDB";
                System.out.println("⚠️ WARNING: DATABASE_NAME not set in .env. Defaulting to: " + dbName);
            }

            databaseName = dbName; // Set static field for logging/reference

            // Establish the single connection pool instance
            client = MongoClients.create(connectionString);
            db = client.getDatabase(databaseName);
            System.out.println("✅ MongoManager: Singleton MongoClient initialized successfully for DB: " + databaseName);

        } catch (IllegalStateException e) {
            System.err.println("❌ " + e.getMessage());
            client = null;
            db = null;
        } catch (Exception e) {
            System.err.println("❌ ERROR: Failed to initialize Singleton MongoDB client with URI: " + (connectionString != null ? connectionString : "N/A") + " - " + e.getMessage());
            e.printStackTrace();
            client = null;
            db = null;
        }
        this.mongoClient = client;
        this.database = db;
    }

    /**
     * Public static method to get the single instance of MongoManager.
     * @return The single instance of MongoManager.
     */
    public static synchronized MongoManager getInstance() {
        if (instance == null) {
            instance = new MongoManager();
        }
        return instance;
    }

    /**
     * NEW: Returns the MongoDatabase object from the single, shared connection.
     * Controllers should now call MongoManager.getInstance().getDatabase().
     * @return The MongoDatabase object, or null if connection failed during initialization.
     */
    public MongoDatabase getDatabase() {
        return this.database;
    }


    // The following helper methods must now use getDatabase() instead of connect().

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
     * Compares a plain text password against a stored BCrypt hash.
     * @param plainPassword The password entered by the user (plain text).
     * @param storedHash The hashed password stored in the database.
     * @return true if the password matches the hash, false otherwise.
     */
    public static boolean checkPassword(String plainPassword, String storedHash) {
        if (plainPassword == null || storedHash == null) {
            return false;
        }
        try {
            return BCrypt.checkpw(plainPassword, storedHash);
        } catch (IllegalArgumentException e) {
            System.err.println("BCrypt hash format error: " + e.getMessage());
            return false; // Hash is likely corrupt or not a BCrypt hash
        }
    }

    /**
     * Finds a single user document by username. Used for profile updates and verification.
     * @param username The username to search for.
     * @return The user Document, or null if not found or connection failed.
     */
    public static Document findUserByUsername(String username) {
        // Updated to use the Singleton pattern
        MongoDatabase db = MongoManager.getInstance().getDatabase();
        if (db == null) return null;

        try {
            MongoCollection<Document> userCollection = db.getCollection("users");
            return userCollection.find(Filters.eq("username", username)).first();
        } catch (Exception e) {
            System.err.println("Database query error while finding user: " + e.getMessage());
            return null;
        }
    }

    /**
     * Updates a user's username in the database.
     * @param oldUsername The current username.
     * @param newUsername The new username.
     * @return true if the update was successful, false otherwise.
     */
    public static boolean updateUserUsername(String oldUsername, String newUsername) {
        // Updated to use the Singleton pattern
        MongoDatabase db = MongoManager.getInstance().getDatabase();
        if (db == null) return false;

        try {
            MongoCollection<Document> userCollection = db.getCollection("users");

            // Find user by old username
            Document filter = new Document("username", oldUsername);
            // Update the username field
            Document update = new Document("$set", new Document("username", newUsername));

            UpdateResult result = userCollection.updateOne(filter, update);

            if (result.getModifiedCount() > 0) {
                System.out.println("✅ Username update successful for: " + oldUsername + " -> " + newUsername);
                return true;
            } else {
                System.out.println("❌ Username update failed: User not found or no change.");
                return false;
            }
        } catch (Exception e) {
            System.err.println("Database update error (Username): " + e.getMessage());
            return false;
        }
    }

    /**
     * Updates a user's hashed password in the database.
     * @param username The username of the user to update.
     * @param newHashedPassword The new BCrypt hashed password.
     * @return true if the update was successful, false otherwise.
     */
    public static boolean updateUserPassword(String username, String newHashedPassword) {
        // Updated to use the Singleton pattern
        MongoDatabase db = MongoManager.getInstance().getDatabase();
        if (db == null) return false;

        try {
            MongoCollection<Document> userCollection = db.getCollection("users");

            // Filter to find the user
            Document filter = new Document("username", username);
            // Update the password field
            Document update = new Document("$set", new Document("password", newHashedPassword));

            UpdateResult result = userCollection.updateOne(filter, update);

            if (result.getModifiedCount() > 0) {
                System.out.println("✅ Password update successful for user: " + username);
                return true;
            } else {
                System.out.println("❌ Password update failed: User not found.");
                return false;
            }
        } catch (Exception e) {
            System.err.println("Database update error (Password): " + e.getMessage());
            return false;
        }
    }


    /**
     * UPDATED: Authenticates a user against the 'users' collection by verifying the plain password,
     * username, AND the specified role against the stored BCrypt hash.
     * @param username The username entered by the user.
     * @param password The password entered by the user (plain text).
     * @param role The role selected by the user (Administrator, Survey Creator, or Data Entry).
     * @return A Document representing the user if login is successful, or null otherwise.
     */
    public static Document authenticateUser(String username, String password, String role) {
        // Updated to use the Singleton pattern
        MongoDatabase db = MongoManager.getInstance().getDatabase();

        if (db == null) {
            return null;
        }

        try {
            MongoCollection<Document> userCollection = db.getCollection("users");

            // STEP 1: Find user by combined criteria: username AND role
            Bson filter = Filters.and(
                    Filters.eq("username", username),
                    Filters.eq("role", role)
            );

            Document userDoc = userCollection.find(filter).first();

            if (userDoc != null) {
                String storedHashedPassword = userDoc.getString("password");

                // STEP 2: Use BCrypt.checkpw to safely compare the plain password
                // with the stored hash.
                if (BCrypt.checkpw(password, storedHashedPassword)) {
                    System.out.println("✅ Login Successful for user: " + username + " with role: " + role);
                    return userDoc; // Authentication successful
                }
            }

            // If user is null (no user found with that username AND role) or password verification failed:
            System.out.println("❌ Login Failed: Invalid credentials or role for user: " + username);
            return null;

        } catch (Exception e) {
            System.err.println("Database query error during authentication: " + e.getMessage());
            return null;
        }
    }
}
