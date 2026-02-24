package com.javarush.filmcache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javarush.filmcache.dao.FilmDAO;
import com.javarush.filmcache.domain.Actor;
import com.javarush.filmcache.domain.Category;
import com.javarush.filmcache.domain.Film;
import com.javarush.filmcache.redis.FilmDetail;
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

    public App(SessionFactory sessionFactory, FilmDAO filmDAO) {
        this.sessionFactory = sessionFactory;
        this.filmDAO = filmDAO;
    }

    private List<Film> fetchAllFilms() {
        try (Session session = sessionFactory.getCurrentSession()) {
            session.beginTransaction();
            List<Film> films = filmDAO.getAll();
            session.getTransaction().commit();
            return films;
        }
    }

    // films -> FilmDetail
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

    public static void main(String[] args) {
        SessionFactory factory = prepareRelationalDb();
        App app = new App(factory, new FilmDAO(factory));
        List<Film> films = app.fetchAllFilms();
        log.info("Загружено фильмов: " + films.size());
        app.shutdown();
    }

    private void shutdown() {
        if (sessionFactory != null && !sessionFactory.isClosed()) {
            sessionFactory.close();
        }
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
