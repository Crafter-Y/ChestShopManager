package de.craftery.chestshopmanager.db;

import org.hibernate.Session;

public class TestDatabaseConnection {
    public TestDatabaseConnection() {
        Session session = HibernateConfigurator.getSessionFactory().openSession();
        session.close();
        HibernateConfigurator.shutdown();
    }
}