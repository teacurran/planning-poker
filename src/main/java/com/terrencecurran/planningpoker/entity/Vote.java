package com.terrencecurran.planningpoker.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "votes")
public class Vote extends PanacheEntityBase {
    
    @Id
    @Column(name = "id")
    public String id;
    
    @ManyToOne
    @JoinColumn(name = "room_id")
    public Room room;
    
    @ManyToOne
    @JoinColumn(name = "player_id")
    public Player player;
    
    @Column(name = "value")
    public String value;
    
    @Column(name = "voting_round")
    public Integer votingRound;
    
    @Column(name = "created_at")
    public LocalDateTime createdAt;
    
    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}