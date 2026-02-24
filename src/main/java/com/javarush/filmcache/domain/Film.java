package com.javarush.filmcache.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Set;

@Entity
@Table(schema = "sakila", name = "film")
@Data // todo проверить на LazyInitializationException
public class Film {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "film_id")
    private Integer id;

    // title
    @Column(name = "title")
    private String title;

    // description
    @Column(name = "description")
    private String description;

    // release_year
    @Column(name = "release_year")
    private Integer releaseYear;

    // rental_rate
    @Column(name = "rental_rate")
    private BigDecimal rentalRate;

    // rating
    @Column(name = "rating")
    private String rating;

    @ManyToMany
    @JoinTable(
            schema = "sakila",
            name = "film_actor",
            joinColumns = @JoinColumn(name = "film_id"),
            inverseJoinColumns = @JoinColumn(name = "actor_id")
    )
    private Set<Actor> actors;

    @ManyToMany
    @JoinTable(
            schema = "sakila",
            name = "film_category",
            joinColumns = @JoinColumn(name = "film_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private Set<Category> categories;

}
