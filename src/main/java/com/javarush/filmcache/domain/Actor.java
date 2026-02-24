package com.javarush.filmcache.domain;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(schema = "sakila", name ="actor")
@Data
public class Actor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "actor_id")
    private Integer id;

    // first_name
    @Column(name = "first_name")
    private String firstName;

    // last_name
    @Column(name = "last_name")
    private String lastName;

}
