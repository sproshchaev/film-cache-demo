package com.javarush.filmcache;

import com.javarush.filmcache.dao.FilmDAO;
import com.javarush.filmcache.domain.Actor;
import com.javarush.filmcache.domain.Category;
import com.javarush.filmcache.domain.Film;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;

import java.util.List;
import java.util.Properties;

public class App {

    private final SessionFactory sessionFactory;
    private final FilmDAO filmDAO;

    public App(SessionFactory sessionFactory, FilmDAO filmDAO) {
        this.sessionFactory = sessionFactory;
        this.filmDAO = filmDAO;
    }

    public static void main(String[] args) {
        SessionFactory factory = prepareRelationalDb();
        App app = new App(factory, new FilmDAO(factory));
        List<Film> films = app.fetchAllFilms();
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
