package com.javarush.filmcache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javarush.filmcache.dao.FilmDAO;
import com.javarush.filmcache.domain.Actor;
import com.javarush.filmcache.domain.Category;
import com.javarush.filmcache.domain.Film;
import com.javarush.filmcache.redis.FilmDetail;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisStringCommands;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;

import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

public class App {

    private final SessionFactory sessionFactory;
    private final FilmDAO filmDAO;
    private final ObjectMapper mapper = new ObjectMapper();
    private final RedisClient redisClient;

    public App(SessionFactory sessionFactory, FilmDAO filmDAO) {
        this.sessionFactory = sessionFactory;
        this.filmDAO = filmDAO;
        redisClient = prepareRedisClient();
    }

    public static void main(String[] args) {
        SessionFactory factory = prepareRelationalDb();
        App app = new App(factory, new FilmDAO(factory));
        List<Film> films = app.fetchAllFilms();
        List<FilmDetail> details = app.transformData(films);
        app.pushToRedis(details);
        System.out.println("Загружено фильмов: " + films.size());
        app.shutdown();
    }

    private List<Film> fetchAllFilms() {
        try (Session session = sessionFactory.getCurrentSession()) {
            session.beginTransaction();
            List<Film> films = filmDAO.getAll();
            session.getTransaction().commit();
            return films;
        }
    }

    private List<FilmDetail> transformData(List<Film> films) {
        return films.stream().map(film -> {
            FilmDetail detail = new FilmDetail();
            detail.setId(film.getId());
            detail.setTitle(film.getTitle());
            detail.setDescription(film.getDescription());
            detail.setReleaseYear(film.getReleaseYear());
            detail.setRentalRate(film.getRentalRate());
            detail.setRating(film.getRating());

            // Преобразуем актёров в список строк
            List<String> actorNames = film.getActors().stream()
                    .map(actor -> actor.getFirstName() + " " + actor.getLastName())
                    .collect(Collectors.toList());
            detail.setActors(actorNames);

            // Категории
            List<String> categoryNames = film.getCategories().stream()
                    .map(Category::getName)
                    .collect(Collectors.toList());
            detail.setCategories(categoryNames);

            return detail;
        }).collect(Collectors.toList());
    }

    private RedisClient prepareRedisClient() {
        RedisClient client = RedisClient.create(RedisURI.create("localhost", 6379));
        try (StatefulRedisConnection<String, String> conn = client.connect()) {
            System.out.println("Connected to Redis");
        }
        return client;
    }

    private void pushToRedis(List<FilmDetail> data) {
        try (StatefulRedisConnection<String, String> conn = redisClient.connect()) {
            RedisStringCommands<String, String> sync = conn.sync();
            for (FilmDetail detail : data) {
                String key = "film:" + detail.getId();
                String value = mapper.writeValueAsString(detail);
                sync.set(key, value);
            }
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    private void shutdown() {
        if (sessionFactory != null && !sessionFactory.isClosed()) {
            sessionFactory.close();
        }
    }

    private static SessionFactory prepareRelationalDb() {
        Properties props = new Properties();
        props.put(Environment.DIALECT, "org.hibernate.dialect.MySQL8Dialect");
        props.put(Environment.DRIVER, "com.p6spy.engine.spy.P6SpyDriver");
        props.put(Environment.URL, "jdbc:p6spy:mysql://localhost:3306/sakila");
        props.put(Environment.USER, "root");
        props.put(Environment.PASS, "sakila");
        props.put(Environment.CURRENT_SESSION_CONTEXT_CLASS, "thread");
        props.put(Environment.HBM2DDL_AUTO, "none");
        props.put(Environment.STATEMENT_BATCH_SIZE, "100");

        return new Configuration()
                .addAnnotatedClass(Film.class)
                .addAnnotatedClass(Actor.class)
                .addAnnotatedClass(Category.class)
                .addProperties(props)
                .buildSessionFactory();
    }

}
