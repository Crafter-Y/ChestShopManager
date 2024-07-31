package de.craftery.chestshopmanager;

import de.craftery.chestshopmanager.db.BaseDatabaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jetbrains.annotations.Nullable;

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

    @Override
    public Long getIdentifyingColumn() {
        return this.id;
    }
}
