package com.javarush.filmcache;

import com.javarush.filmcache.dao.FilmDAO;
import com.javarush.filmcache.domain.*;
import com.javarush.filmcache.redis.FilmDetail;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisStringCommands;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 3, time = 5)
@Fork(1)
public class BenchmarkTest {

    private SessionFactory sessionFactory;
    private FilmDAO filmDAO;
    private RedisClient redisClient;
    private ObjectMapper mapper;
    private List<Integer> testIds = List.of(1, 20, 45, 100, 250, 300, 400, 500, 600, 700);

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(BenchmarkTest.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }

    @Setup(Level.Trial)
    public void setup() {
        sessionFactory = prepareRelationalDb();
        filmDAO = new FilmDAO(sessionFactory);
        redisClient = RedisClient.create(RedisURI.create("localhost", 6379));
        mapper = new ObjectMapper();

        // Загружаем данные в Redis (как в PerformanceTest)
        try (Session session = sessionFactory.getCurrentSession()) {
            session.beginTransaction();
            List<Film> films = filmDAO.getAll();
            session.getTransaction().commit();

            List<FilmDetail> filmDetails = films.stream().map(film -> {
                FilmDetail d = new FilmDetail();
                d.setId(film.getId());
                d.setTitle(film.getTitle());
                d.setDescription(film.getDescription());
                d.setReleaseYear(film.getReleaseYear());
                d.setRentalRate(film.getRentalRate());
                d.setRating(film.getRating());
                d.setActors(film.getActors().stream().map(a -> a.getFirstName() + " " + a.getLastName()).collect(Collectors.toList()));
                d.setCategories(film.getCategories().stream().map(Category::getName).collect(Collectors.toList()));
                return d;
            }).collect(Collectors.toList());

            try (StatefulRedisConnection<String, String> conn = redisClient.connect()) {
                RedisStringCommands<String, String> sync = conn.sync();
                for (FilmDetail fd : filmDetails) {
                    sync.set("film:" + fd.getId(), mapper.writeValueAsString(fd));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (sessionFactory != null) sessionFactory.close();
        if (redisClient != null) redisClient.shutdown();
    }

    @Benchmark
    public void readFromRedis() throws Exception {
        try (StatefulRedisConnection<String, String> conn = redisClient.connect()) {
            RedisStringCommands<String, String> sync = conn.sync();
            for (Integer id : testIds) {
                String json = sync.get("film:" + id);
                mapper.readValue(json, FilmDetail.class);
            }
        }
    }

    @Benchmark
    public void readFromMySQL() {
        try (Session session = sessionFactory.getCurrentSession()) {
            session.beginTransaction();
            for (Integer id : testIds) {
                Film film = filmDAO.getById(id);
                film.getActors().size();  // принудительная загрузка коллекций
                film.getCategories().size();
            }
            session.getTransaction().commit();
        }
    }

    private static SessionFactory prepareRelationalDb() {

        Properties properties = new Properties();
        properties.put(Environment.DIALECT, "org.hibernate.dialect.MySQL8Dialect");
        properties.put(Environment.DRIVER, "com.p6spy.engine.spy.P6SpyDriver");
        properties.put(Environment.URL, "jdbc:p6spy:mysql://localhost:3306/sakila");

        properties.put(Environment.USER, "root");
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