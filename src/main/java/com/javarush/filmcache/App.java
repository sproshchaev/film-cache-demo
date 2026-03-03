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

import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

@Slf4j
public class App {

    private final SessionFactory sessionFactory;
    private final FilmDAO filmDAO;
    private final ObjectMapper mapper = new ObjectMapper();
    private final RedisClient redisClient;

    public App(SessionFactory sessionFactory, FilmDAO filmDAO) {
        this.sessionFactory = sessionFactory;
        this.filmDAO = filmDAO;
        this.redisClient = preparedRedisClient();
    }

    private List<Film> fetchAllFilms() {
        try (Session session = sessionFactory.getCurrentSession()) {
            session.beginTransaction();
            List<Film> films = filmDAO.getAll();
            session.getTransaction().commit();
            return films;
        }
    }

    /**
     * Метод который осуществляет преобразование в денормализованное состояние films -> FilmDetail
     * @param films
     * @return List<FilmDetail>
     */
    private List<FilmDetail> transformData(List<Film> films) {
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

    /**
     * docker run -d --name redis -p 6379:6379 redis:6.2-alpine
     * @return RedisClient
     */
    private RedisClient preparedRedisClient() {
        RedisClient client = RedisClient.create(RedisURI.create("localhost", 6379));
        try (StatefulRedisConnection<String, String> connection = client.connect()) {
            log.info("Connected to Redis");
        }
        return client;
    }

    /**
     * Метод записи данных в Redis
     * Каждый объект сериализуется в JSON и сохраняется по ключу.
     * Ключ формируется на основе идентификатора фильма
     * @param data
     */
    private void pushToRedis(List<FilmDetail> data) {
        // StatefulRedisConnection - соединение с Redis, метод .connect() открывает новое соединение
        try (StatefulRedisConnection<String, String> connection = redisClient.connect()) {
            // У соединения вызываем метод .sync(), возвращающий объект реализующий синхронные команды
            // для работы со строками. Методы set и get
            RedisStringCommands<String, String> redisStringCommands = connection.sync();
            for(FilmDetail filmDetail : data) {
                String key = "film:" + filmDetail.getId();            // film:123
                String value = mapper.writeValueAsString(filmDetail); // JSON-строка
                redisStringCommands.set(key, value); // через RedisStringCommands мы записываем в Redis (аналогично HashMap)
                                                     // redisStringCommands.get(key); // и можем считать RedisStringCommands
            }
        } catch (JsonProcessingException e) {
            log.error("Ощибка записи в Redis" + e);
        }
    }



    public static void main(String[] args) {
        SessionFactory factory = prepareRelationalDb();
        App app = new App(factory, new FilmDAO(factory));
        // Список фильмов из БД
        List<Film> films = app.fetchAllFilms();
        // Преобразовали для последующей записи (K,V) в Redis
        List<FilmDetail> filmDetails = app.transformData(films);
        app.pushToRedis(filmDetails);
        log.info("Загружено фильмов: " + films.size());
        app.shutdown();
    }

    private void shutdown() {
        if (sessionFactory != null && !sessionFactory.isClosed()) {
            sessionFactory.close();
        }
    }

    private static SessionFactory prepareRelationalDb() {
        Properties props = new Properties();
        // Загружаем файл application.properties из текущей директории
        try (java.io.InputStream input = new java.io.FileInputStream("application.properties")) {
            props.load(input);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Не удалось загрузить application.properties. " +
                    "Убедитесь, что файл существует в корне проекта.", e);
        }

        String dbUser = props.getProperty("db.user");
        String dbPassword = props.getProperty("db.password");

        if (dbUser == null || dbPassword == null) {
            throw new RuntimeException("В application.properties не заданы db.user и/или db.password");
        }

        Properties properties = new Properties();
        properties.put(Environment.DIALECT, "org.hibernate.dialect.MySQL8Dialect");
        properties.put(Environment.DRIVER, "com.p6spy.engine.spy.P6SpyDriver");
        properties.put(Environment.URL, "jdbc:p6spy:mysql://localhost:3306/sakila");

        //properties.put(Environment.USER, "root"); //todo  Настройка секретов
        //properties.put(Environment.PASS, "sakila");

        properties.put(Environment.USER, dbUser);
        properties.put(Environment.PASS, dbPassword);


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
