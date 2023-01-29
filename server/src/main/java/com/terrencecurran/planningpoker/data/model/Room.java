package com.terrencecurran.planningpoker.data.model;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Room { 
  @Id
  @GeneratedValue(strategy=GenerationType.AUTO)
  private Long id;

  private String code;
  private String name;
  private LocalDateTime dateCreated;
  private LocalDateTime dateActive;

  protected Room() {}

  public Room(String code, String name, LocalDateTime dateCreated, LocalDateTime dateActive) {
    this.code = code;
    this.name = name;
    this.dateCreated = dateCreated;
    this.dateActive = dateActive;
  }

  @Override
  public String toString() {
    return String.format(
        "Room[id=%d, code='%s', name='%s', dateCreated='%s', dateActive='%s']",
        id, code, name, dateCreated, dateActive);
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public LocalDateTime getDateCreated() {
    return dateCreated;
  }

  public void setDateCreated(LocalDateTime dateCreated) {
    this.dateCreated = dateCreated;
  }

  public LocalDateTime getDateActive() {
    return dateActive;
  }

  public void setDateActive(LocalDateTime dateActive) {
    this.dateActive = dateActive;
  }
  
}