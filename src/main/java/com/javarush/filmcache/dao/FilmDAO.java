package com.javarush.filmcache.dao;

import com.javarush.filmcache.domain.Film;
import org.hibernate.Query;
import org.hibernate.SessionFactory;

import java.util.List;
import java.util.Queue;

public class FilmDAO {
    private final SessionFactory sessionFactory;

    public FilmDAO(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    // Получение списка фильмов
    public List<Film> getAll() {
        Query<Film> query = sessionFactory.getCurrentSession()
                .createQuery(
                        "select distinct f from Film f " +
                                "left join fetch f.actors " +
                                "left join fetch f.categories", Film.class);
        return query.list();
    }

    // Получение фильма по ID
    public Film getById(Integer id) {
        Query<Film> query = sessionFactory.getCurrentSession().createQuery(
                "select f from Film f " +
                        "left join fetch f.actors " +
                        "left join fetch f.categories " +
                        "where f.id = :id", Film.class);
        query.setParameter("id", id);
        return query.uniqueResult();
    }

    // Общее число фильмов
    public long getTotalCount() {
        Query<Long> query = sessionFactory.getCurrentSession().createQuery(
                "select count(f) from Film f", Long.class);
        return query.uniqueResult();
    }

}
