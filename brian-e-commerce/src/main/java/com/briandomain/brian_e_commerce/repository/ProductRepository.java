package com.briandomain.brian_e_commerce.repository;

import com.briandomain.brian_e_commerce.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    // our custom database lookup methods implemented below:


    // Query method -> spring will handle the sql logic for us. Search "springjpa query method" to learn more
    Optional<Product> findByName(String name);

    /*
    //Custom method 1
    @Query("SELECT * FROM Product WHERE name = ?1 AND price = ?2")
    Product productCustomQuery(String name, String price);
     */


    //Custom method 1 implementation 2
    @Query("SELECT * FROM Product WHERE name = :name AND price = :price")
    Product productCustomQuery(@Param("name") String name, @Param("price") String price);
}
