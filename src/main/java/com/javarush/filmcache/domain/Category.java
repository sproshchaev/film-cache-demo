package com.javarush.filmcache.domain;

import javax.persistence.*;
import lombok.Data;

@Entity
@Table(schema = "sakila", name = "category")
@Data
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Integer id;

    private String name;
}