package com.briandomain.brian_e_commerce.entity;

import jakarta.persistence.*;

@Entity
// @Table(name = "item") //overrides table name in database. It should be product but it's now item.
public class Product {

    @Id //marks the 'private Long id' class attribute as the id key in the database
    @GeneratedValue// we're telling springboot to automatically generate a value for us when creating the item and storing in database
    private Long id;
    //@Column(name = "title") //instead of storing the attribute name as name in database, store it as title instead
    private String name;
    private String price;

    public Product() {
    }

    public Product(Long id, String name, String price) {
        this.id = id;
        this.name = name;
        this.price = price;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPrice() {
        return price;
    }
}
