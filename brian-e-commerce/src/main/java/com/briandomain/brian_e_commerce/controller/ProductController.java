package com.briandomain.brian_e_commerce.controller;

import com.briandomain.brian_e_commerce.entity.Product;
import com.briandomain.brian_e_commerce.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
public class ProductController {
    @Autowired
    private ProductRepository productRepository;

    @GetMapping("/products")
    public List<Product> getProducts(){
        //return List.of(new Product(1L, "Colgate", "120"));
        return productRepository.findAll();
    }

    @PostMapping("/products")
    public Product addProduct(@RequestBody Product product){
        return productRepository.save(product);
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<?> searchProduct(@PathVariable Long id){
        Optional<Product> product = productRepository.findById(id);
        if(product.isPresent()){
            return ResponseEntity.ok(product.get());
        }
        return ResponseEntity.notFound().build();
        //return productRepository.findById(id);
    }

    //GET - Getting the resource
    //POST - Add a new resource
    //PATCH - Update a resource **partial resource**
    //PUT - Replace a resource **entire resource**
    //DELETE - Delete a resource

    @PatchMapping("/products")
    public Product updateProduct(@RequestBody Product product){
        // You should first validate if the product actually exists before patching it up.
        return productRepository.save(product);
    }

    @DeleteMapping("/products/{id}")
    public void deleteProduct(@PathVariable Long id){
        productRepository.deleteById(id);
    }

    @GetMapping("/search-product")
    // e.g /search-product?name=just-any-name-will-do
    public ResponseEntity<?> searchByName(@RequestParam String name){
        Optional<Product> product = productRepository.findByName(name);
        if(product.isPresent()){
            return ResponseEntity.ok(product.get());
        }
        return ResponseEntity.notFound().build();
    }
}
