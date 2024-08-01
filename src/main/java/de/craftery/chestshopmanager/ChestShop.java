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
public class ChestShop extends BaseDatabaseEntity<ChestShop, Long> {
    @Id
    @GeneratedValue(strategy=GenerationType.SEQUENCE)
    @Column
    private long id;

    @Column(nullable = false)
    private long shopId;

    @Column(nullable = false)
    private int x;

    @Column(nullable = false)
    private int y;

    @Column(nullable = false)
    private int z;

    @Column(columnDefinition = "VARCHAR(24)", nullable = false)
    private String owner;

    @Column(nullable = false)
    private int stock;

    @Column(nullable = false)
    private boolean full = false;

    @Column(columnDefinition = "VARCHAR(64)", nullable = false)
    private String item;

    @Column(nullable = false)
    private int quantity;

    @Column
    private Integer buyPrice = null;

    @Column
    private Integer sellPrice = null;

    @Override
    public Long getIdentifyingColumn() {
        return this.id;
    }

    public static List<ChestShop> getAll() {
        return BaseDatabaseEntity.getAll(ChestShop.class);
    }

    public static List<ChestShop> getByItem(String itemName) {
        try {
            @Cleanup Session session = HibernateConfigurator.getSessionFactory().openSession();

            return session.createQuery("FROM ChestShop where lower(item) = :item", ChestShop.class)
                    .setParameter("item", itemName.toLowerCase())
                    .getResultList();
        } catch (NoResultException e) {
            return new ArrayList<>();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public static @Nullable ChestShop getByCoordinate(int x, int y, int z) {
        try {
            @Cleanup Session session = HibernateConfigurator.getSessionFactory().openSession();

            return session.createQuery("FROM ChestShop where x = :x AND y = :y AND z = :z", ChestShop.class)
                    .setParameter("x", x)
                    .setParameter("y", y)
                    .setParameter("z", z)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}