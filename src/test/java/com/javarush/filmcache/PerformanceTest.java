package com.javarush.filmcache;

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
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class PerformanceTest {

    private static SessionFactory sessionFactory;
    private static FilmDAO filmDAO;
    private static RedisClient redisClient;
    private static ObjectMapper mapper;
    private static List<Integer> testIds = List.of(1, 20, 45, 100, 250, 300, 400, 500, 600, 700);

    @BeforeAll
    static void setup() {
        // Инициализация Hibernate
        sessionFactory = prepareRelationalDb();
        filmDAO = new FilmDAO(sessionFactory);

        // Инициализация Redis
        redisClient = RedisClient.create(RedisURI.create("localhost", 6379));
        try (StatefulRedisConnection<String, String> conn = redisClient.connect()) {
            System.out.println("Connected to Redis");
        }

        mapper = new ObjectMapper();

        // Загружаем все фильмы из MySQL и заполняем Redis (один раз перед тестами)
        List<Film> films;
        try (Session session = sessionFactory.getCurrentSession()) {
            session.beginTransaction();
            films = filmDAO.getAll();
            session.getTransaction().commit();
        }

        List<FilmDetail> details = transformData(films);
        pushToRedis(details);
    }

    @AfterAll
    static void tearDown() {
        if (sessionFactory != null && !sessionFactory.isClosed()) {
            sessionFactory.close();
        }
        if (redisClient != null) {
            redisClient.shutdown();
        }
    }

    @Test
    void testRedisPerformance() {
        long start = System.currentTimeMillis();
        try (StatefulRedisConnection<String, String> conn = redisClient.connect()) {
            RedisStringCommands<String, String> sync = conn.sync();
            for (Integer id : testIds) {
                String json = sync.get("film:" + id);
                assertNotNull(json, "Данные для фильма " + id + " не найдены в Redis");
                mapper.readValue(json, FilmDetail.class);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        long duration = System.currentTimeMillis() - start;
        System.out.println("Redis чтение 10 фильмов: " + duration + " ms");
    }

    @Test
    void testMysqlPerformance() {
        long start = System.currentTimeMillis();
        try (Session session = sessionFactory.getCurrentSession()) {
            session.beginTransaction();
            for (Integer id : testIds) {
                Film film = filmDAO.getById(id);
                assertNotNull(film, "Фильм с id " + id + " не найден в MySQL");
                // Инициализируем коллекции (хотя они уже загружены через JOIN FETCH)
                film.getActors().size();
                film.getCategories().size();
            }
            session.getTransaction().commit();
        }
        long duration = System.currentTimeMillis() - start;
        System.out.println("MySQL чтение 10 фильмов: " + duration + " ms");
    }

    private static List<FilmDetail> transformData(List<Film> films) {
        return films.stream().map(film -> {
            FilmDetail detail = new FilmDetail();
            detail.setId(film.getId());
            detail.setTitle(film.getTitle());
            detail.setDescription(film.getDescription());
            detail.setReleaseYear(film.getReleaseYear());
            detail.setRentalRate(film.getRentalRate());
            detail.setRating(film.getRating());

            List<String> actorNames = film.getActors().stream()
                    .map(actor -> actor.getFirstName() + " " + actor.getLastName())
                    .collect(Collectors.toList());
            detail.setActors(actorNames);

            List<String> categoryNames = film.getCategories().stream()
                    .map(Category::getName)
                    .collect(Collectors.toList());
            detail.setCategories(categoryNames);

            return detail;
        }).collect(Collectors.toList());
    }

    private static void pushToRedis(List<FilmDetail> data) {
        try (StatefulRedisConnection<String, String> conn = redisClient.connect()) {
            RedisStringCommands<String, String> sync = conn.sync();
            for (FilmDetail detail : data) {
                String key = "film:" + detail.getId();
                String value = mapper.writeValueAsString(detail);
                sync.set(key, value);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static SessionFactory prepareRelationalDb() {
        Properties props = new Properties();
        props.put(Environment.DIALECT, "org.hibernate.dialect.MySQL8Dialect");
        props.put(Environment.DRIVER, "com.p6spy.engine.spy.P6SpyDriver");
        props.put(Environment.URL, "jdbc:p6spy:mysql://localhost:3306/sakila");
        props.put(Environment.USER, "root");
        props.put(Environment.PASS, "sakila"); // замените на свой пароль
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