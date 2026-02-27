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
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@Slf4j
class PerformanceTest {

    private static SessionFactory sessionFactory;
    private static FilmDAO filmDAO;
    private static RedisClient redisClient;
    private static ObjectMapper mapper;
    private static List<Integer> testIds = List.of(1, 20, 45, 100, 250, 300, 400, 500, 600, 700);

    @BeforeAll
    static void setup() {
        sessionFactory = prepareRelationalDb();
        filmDAO = new FilmDAO(sessionFactory);

        redisClient = RedisClient.create(RedisURI.create("localhost", 6379));
        try (StatefulRedisConnection<String, String> connection = redisClient.connect()) {
            log.info("Установлено соединение с Redis");
        }

        mapper = new ObjectMapper();

        List<Film> films;
        try (Session session = sessionFactory.getCurrentSession()) {
            session.beginTransaction();
            films = filmDAO.getAll();
            session.getTransaction().commit();
        }

        List<FilmDetail> filmDetails = transformData(films);
        pushToRedis(filmDetails);

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
        try (StatefulRedisConnection<String, String> connection = redisClient.connect()) {
            RedisStringCommands<String, String> sync = connection.sync();
            for (Integer id : testIds) {
                String json = sync.get("film:" + id);
                assertNotNull(json, "Данные для фильма " + id + " не найдены в Redis");
                mapper.readValue(json, FilmDetail.class);
            }
        } catch (JsonProcessingException e) {
            log.error("Ошибка " + e);
        }
        long duration = System.currentTimeMillis() - start;
        log.info("Redis чтение 10 фильмов: " + duration + " ms");
    }

    @Test
    void testMySqlPerformance() {
        long start = System.currentTimeMillis();
        try (Session session = sessionFactory.getCurrentSession()) {
            session.beginTransaction();

            for (Integer id : testIds) {
                Film film = filmDAO.getById(id);
                assertNotNull(film, "Фильм с id " + id + " не найден в MySQL");
                film.getActors().size();
                film.getCategories().size();
            }
            session.getTransaction().commit();
        }
        long duration = System.currentTimeMillis() - start;
        log.info("MySQL чтение 10 фильмов: " + duration + " ms");
    }

    private static void pushToRedis(List<FilmDetail> data) {
        // StatefulRedisConnection - соединение с Redis, метод .connect() открывает новое соединение
        try (StatefulRedisConnection<String, String> connection = redisClient.connect()) {
            // У соединения вызываем метод .sync(), возвращающий объект реализующий синхронные команды
            // для работы со строками. Методы set и get
            RedisStringCommands<String, String> redisStringCommands = connection.sync();
            for (FilmDetail filmDetail : data) {
                String key = "film:" + filmDetail.getId();            // film:123
                String value = mapper.writeValueAsString(filmDetail); // JSON-строка
                redisStringCommands.set(key, value); // через RedisStringCommands мы записываем в Redis (аналогично HashMap)
                // redisStringCommands.get(key); // и можем считать RedisStringCommands
            }
        } catch (JsonProcessingException e) {
            log.error("Ощибка записи в Redis" + e);
        }
    }

    private static List<FilmDetail> transformData(List<Film> films) {
        return films.stream().map(
                film -> {
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

    private static SessionFactory prepareRelationalDb() {
        Properties properties = new Properties();
        properties.put(Environment.DIALECT, "org.hibernate.dialect.MySQL8Dialect");
        properties.put(Environment.DRIVER, "com.p6spy.engine.spy.P6SpyDriver");
        properties.put(Environment.URL, "jdbc:p6spy:mysql://localhost:3306/sakila");
        properties.put(Environment.USER, "root"); //todo  Настройка секретов
        properties.put(Environment.PASS, "sakila");
        properties.put(Environment.CURRENT_SESSION_CONTEXT_CLASS, "thread");
        properties.put(Environment.HBM2DDL_AUTO, "none");
        properties.put(Environment.STATEMENT_BATCH_SIZE, "100");

        return new Configuration()
                .addAnnotatedClass(Film.class)
                .addAnnotatedClass(Actor.class)
                .addAnnotatedClass(Category.class)
                .addProperties(properties)
                .buildSessionFactory();
    }


}