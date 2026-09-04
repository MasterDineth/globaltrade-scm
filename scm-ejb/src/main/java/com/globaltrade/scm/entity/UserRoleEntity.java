package com.globaltrade.scm.entity;
import jakarta.persistence.*;
import java.io.Serializable;
@Entity
@Table(name = "user_role")
public class UserRoleEntity implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_id")
    private Long id;
    @Column(name = "name", nullable = false, length = 32, unique = true)
    private String name;
    public UserRoleEntity() {}
    public UserRoleEntity(String name) {
        this.name = name;
    }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
