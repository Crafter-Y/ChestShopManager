package de.craftery.chestshopmanager.db;

import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;

import javax.imageio.spi.ServiceRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.ServiceConfigurationError;

public class HibernateConfigurator {
    private static SessionFactory sessionFactory;

    private static final List<Class<?>> entities = new ArrayList<>();

    public static void addEntity(Class<?> entity) {
        entities.add(entity);
    }
    public static SessionFactory getSessionFactory() {
        if (sessionFactory == null) {
            try {
                Configuration configuration = new Configuration();

                Properties settings = new Properties();
                settings.put(Environment.JAKARTA_JDBC_URL, "jdbc:sqlite:./config/chestshopdb.db");

                settings.put(Environment.JAKARTA_JDBC_USER, "");
                settings.put(Environment.JAKARTA_JDBC_PASSWORD, "");

                settings.put(Environment.DIALECT, "org.hibernate.community.dialect.SQLiteDialect");

                //settings.put(Environment.CONNECTION_PROVIDER, "org.sqlite.JDBC");
                settings.put(Environment.JAKARTA_JDBC_DRIVER, "org.sqlite.JDBC");
                settings.put(Environment.CURRENT_SESSION_CONTEXT_CLASS, "thread");

                settings.put(Environment.SHOW_SQL, "true");

                settings.put(Environment.HBM2DDL_AUTO, "update");
                settings.put(Environment.USE_SECOND_LEVEL_CACHE, "false");

                configuration.setProperties(settings);

                // Register Database Models
                for (Class<?> entity : entities) {
                    configuration.addAnnotatedClass(entity);
                }

                StandardServiceRegistry serviceRegistry = new StandardServiceRegistryBuilder()
                        .applySettings(configuration.getProperties()).build();

                sessionFactory = configuration.buildSessionFactory(serviceRegistry);
            } catch (ServiceConfigurationError error) {
                System.out.println("[ServiceConfigurationError] " + error.getMessage());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return sessionFactory;
    }

    public static void shutdown() {
        if (sessionFactory == null) return;

        if (sessionFactory.getCurrentSession().getTransaction().isActive()) {
            sessionFactory.getCurrentSession().flush();
        }

        sessionFactory.getCurrentSession().close();
        sessionFactory.close();

        sessionFactory = null;
    }
}