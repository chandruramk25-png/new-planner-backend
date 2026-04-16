package com.newplanner;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class CreateDB {
    public static void main(String[] args) {
        try {
            Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/", "root", "Thaniyel#2003");
            Statement stmt = conn.createStatement();
            stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS newplanner");
            System.out.println("Database 'newplanner' created successfully.");
            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
