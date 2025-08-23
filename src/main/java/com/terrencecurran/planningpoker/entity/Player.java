package com.terrencecurran.planningpoker.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "players")
public class Player extends PanacheEntityBase {
    
    @Id
    @Column(name = "id")
    public String id;
    
    @Column(name = "username")
    public String username;
    
    @Column(name = "session_id")
    public String sessionId;
    
    @ManyToOne
    @JoinColumn(name = "room_id")
    public Room room;
    
    @Column(name = "is_observer")
    public boolean isObserver;
    
    @Column(name = "joined_at")
    public LocalDateTime joinedAt;
    
    @Column(name = "is_connected")
    public boolean isConnected;
    
    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (joinedAt == null) {
            joinedAt = LocalDateTime.now();
        }
        isConnected = true;
    }
}