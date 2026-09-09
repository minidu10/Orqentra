package com.orqentra.auth.identity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "restaurants")
public class Restaurant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String reference;

    @Column(nullable = false)
    private String name;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected Restaurant() {}

    public Restaurant(String reference, String name) {
        this.reference = reference;
        this.name = name;
    }

    public Long getId() { return id; }
    public String getReference() { return reference; }
    public String getName() { return name; }
    public Instant getCreatedAt() { return createdAt; }
}
