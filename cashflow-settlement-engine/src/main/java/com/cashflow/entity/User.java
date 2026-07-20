package com.cashflow.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/*
 * A participant in the settlement system.
 *
 * Deliberately lean -- the settlement algorithm only needs a stable UUID.
 * Auth, email, and profile data belong in a separate service.
 */
@Entity
@Table(
    name = "users",
    indexes = { @Index(name = "idx_user_username", columnList = "username", unique = true) }
)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "username", nullable = false, unique = true, length = 64)
    private String username;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    protected User() {}

    public User(String username, String displayName) {
        this.username    = username;
        this.displayName = displayName;
    }

    public UUID    getId()                  { return id; }
    public String  getUsername()            { return username; }
    public void    setUsername(String u)    { this.username = u; }
    public String  getDisplayName()         { return displayName; }
    public void    setDisplayName(String d) { this.displayName = d; }
    public Instant getCreatedAt()           { return createdAt; }

    @Override
    public String toString() {
        return "User{id=" + id + ", username='" + username + "'}";
    }
}
