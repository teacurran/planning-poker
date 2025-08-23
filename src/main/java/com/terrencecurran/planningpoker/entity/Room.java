package com.terrencecurran.planningpoker.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "rooms")
public class Room extends PanacheEntityBase {
    
    @Id
    @Column(name = "id")
    public String id;
    
    @Column(name = "name")
    public String name;
    
    @Column(name = "created_at")
    public LocalDateTime createdAt;
    
    @Column(name = "is_voting_active")
    public boolean isVotingActive;
    
    @Column(name = "are_cards_revealed")
    public boolean areCardsRevealed;
    
    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    public List<Player> players = new ArrayList<>();
    
    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    public List<Vote> votes = new ArrayList<>();
    
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