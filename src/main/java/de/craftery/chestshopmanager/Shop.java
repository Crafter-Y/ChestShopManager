package de.craftery.chestshopmanager;

import de.craftery.chestshopmanager.db.BaseDatabaseEntity;
import de.craftery.chestshopmanager.db.HibernateConfigurator;
import jakarta.persistence.*;
import lombok.Cleanup;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.Session;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table
public class Shop extends BaseDatabaseEntity<Shop, Long> {
    @Id
    @GeneratedValue(strategy= GenerationType.SEQUENCE)
    @Column
    private long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String command;


    public static @Nullable Shop getByName(String name) {
        return BaseDatabaseEntity.getAll(Shop.class).stream().filter(home -> home.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    public static @Nullable Shop getById(long id) {
        return BaseDatabaseEntity.getById(Shop.class, id);
    }

    public static List<Shop> getAll() {
        return BaseDatabaseEntity.getAll(Shop.class);
    }

    public static List<Shop> getByCommand(String command) {
        try {
            @Cleanup Session session = HibernateConfigurator.getSessionFactory().openSession();

            return session.createQuery("FROM Shop where lower(command) = :command", Shop.class)
                    .setParameter("command", command.toLowerCase())
                    .getResultList();
        } catch (NoResultException e) {
            return new ArrayList<>();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    @Override
    public Long getIdentifyingColumn() {
        return this.id;
    }
}
