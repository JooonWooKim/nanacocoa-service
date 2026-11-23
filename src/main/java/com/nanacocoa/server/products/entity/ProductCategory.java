package com.nanacocoa.server.products.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product_categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductCategory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 80)
    private String name;
    @Column(nullable = false, length = 64, unique = true)
    private String nameKey;
    @Column(nullable = false)
    private long sortOrder;
    @Column(nullable = false)
    private boolean visible = true;

    public ProductCategory(String name, String nameKey, long sortOrder) {
        rename(name, nameKey);
        this.sortOrder = sortOrder;
    }

    public void rename(String name, String nameKey) {
        this.name = name;
        this.nameKey = nameKey;
    }

    public void reorder(long sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void show(boolean visible) {
        this.visible = visible;
    }
}
