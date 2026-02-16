package com.javarush.filmcache.redis;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class FilmDetail {
    private Integer id;
    private String title;
    private String description;
    private Integer releaseYear;
    private BigDecimal rentalRate;
    private String rating;
    private List<String> actors;       // имена актёров (например, "John Doe")
    private List<String> categories;   // названия категорий
}