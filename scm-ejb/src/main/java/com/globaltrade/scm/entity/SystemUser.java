package com.globaltrade.scm.entity;

import jakarta.persistence.*;

import java.io.Serializable;
import java.util.Objects;

/**
 * Backing store for {@code SupplyChainLoginModule} (custom JAAS login
 * module). {@code passwordHash} is a salted hash (see
 * {@code SecurityUtil#hashPassword}) -- the login module never compares
 * plaintext passwords, and this entity never leaves the security package
 * boundary as anything but a hashed row.
 */
@Entity
@Table(name = "system_user", uniqueConstraints = @UniqueConstraint(name = "uq_user_username", columnNames = "username"))
public class SystemUser implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "system_user_id")
    private Long id;

    @Column(name = "username", nullable = false, length = 100)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", length = 150)
    private String fullName;

    @ManyToOne(optional = false)
    @JoinColumn(name = "role_id")
    private UserRoleEntity role;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public SystemUser() {
    }

    public SystemUser(String username, String passwordHash, String fullName, UserRoleEntity role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public UserRoleEntity getRole() {
        return role;
    }

    public void setRole(UserRoleEntity role) {
        this.role = role;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SystemUser)) return false;
        SystemUser that = (SystemUser) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
