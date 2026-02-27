# film-cache-demo

1. Задача (технические требования)
```angular2html
- Оптимизация производительности с Hibernate и Redis
- демо проект на базе Sakila
- При просмотре детальной информации о фильме нужно отобразить:
 - название
 - описание
 - год выпуска
 - рейтинг
 - список актеров
 - категории

- В реляционной БД эти данные хранятся в разных таблицах:
  - film
  - actor
  - film_actor
  - category
  - film_category

- Типичный запрос без оптимизации может приводить к проблеме N+1
```

2. Решение
```angular2html
- Выгружаем агрегатированные данные (фильм + актеры + категории) в Redis

- Приложение обращается сначала к Redis и только при отсутствии данных - к БД

- Структура данных:
  - film: film_id, title, description, release_year, rental_rate, rating
  - actor: actor_id, first_name, last_name
  - film_actor: film_id, actor_id <- many-to-many
  - category: category_id, name
  - film_category: film_id, category_id <- many-to-many

- Технологический стек:
  - Java 17 
  - Maven
  - Hibernate
  - MySQL
  - Redis
  - P6Spy
  - Docker 

- Domain:
  - Film
  - Actor
  - Category
- DAO - методы получения данных из MySQL
- Redis DTO - класс FilmDetail (плоская структура, готова для кэша)
- Загружаем фильмы из MySQL, трансформируем в DTO, сохраняем в Redis, тестируем чтение
```

3. Окружение
```bash
docker run --name mysql-sakila -e MYSQL_ROOT_PASSWORD=sakila -d -p 3306:3306 restsql/mysql-sakila
docker run -d --name redis -p 6379:6379 redis:6.2-alpine
```

4.Апгрейд проекта
```angular2html
1) Предметная область: Фильмы, ТЗ: страны, города, языки
2) Связи: @ManyToMany, ТЗ: @OneToMany страна -> языки, @ManyToOne город -> страна, @OneToOne страна -> столица.
3) Проблема N+1: JOIN FETCH, ТЗ: с городами и странами
4) Кэшируемые данные: плоский DTO FilmDetail, ТЗ: CityCountry(id города, название, население, данные страны, список языков)
5) Трансформация: Film -> FilmDetail, ТЗ: City -> CityCountry
6) Ключ в Redis: film:<id>, ТЗ: <id города>
7) Тестирование: два теста, сравниваем время
```