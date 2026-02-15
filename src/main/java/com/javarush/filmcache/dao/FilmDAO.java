package com.javarush.filmcache.dao;

import com.javarush.filmcache.domain.Film;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import java.util.List;

public class FilmDAO {
    private final SessionFactory sessionFactory;

    public FilmDAO(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public List<Film> getAll() {
        // Используем JOIN FETCH, чтобы загрузить актёров и категории одним запросом
        Query<Film> query = sessionFactory.getCurrentSession().createQuery(
                "select distinct f from Film f " +
                        "left join fetch f.actors " +
                        "left join fetch f.categories", Film.class);
        return query.list();
    }

    public Film getById(Integer id) {
        Query<Film> query = sessionFactory.getCurrentSession().createQuery(
                "select f from Film f " +
                        "left join fetch f.actors " +
                        "left join fetch f.categories " +
                        "where f.id = :id", Film.class);
        query.setParameter("id", id);
        return query.uniqueResult();
    }

    public long getTotalCount() {
        Query<Long> query = sessionFactory.getCurrentSession().createQuery(
                "select count(f) from Film f", Long.class);
        return query.uniqueResult();
    }
}