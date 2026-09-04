package com.globaltrade.scm.entity;
import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;
@Entity
@Table(name = "carrier", uniqueConstraints = @UniqueConstraint(name = "uq_carrier_code", columnNames = "code"))
public class Carrier implements Serializable {
    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "carrier_id")
    private Long id;
    @Column(name = "name", nullable = false, length = 150)
    private String name;
    @Column(name = "code", nullable = false, length = 20)
    private String code;
    @Column(name = "api_endpoint", length = 255)
    private String apiEndpoint;
    @Column(name = "region", length = 50)
    private String region;
    @Column(name = "active", nullable = false)
    private boolean active = true;
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getCode() {
        return code;
    }
    public void setCode(String code) {
        this.code = code;
    }
    public String getApiEndpoint() {
        return apiEndpoint;
    }
    public void setApiEndpoint(String apiEndpoint) {
        this.apiEndpoint = apiEndpoint;
    }
    public String getRegion() {
        return region;
    }
    public void setRegion(String region) {
        this.region = region;
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
        if (!(o instanceof Carrier)) return false;
        Carrier carrier = (Carrier) o;
        return id != null && id.equals(carrier.id);
    }
    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
