# 🔮 Gadalka Backend

![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/SpringBoot-3.x-green)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue)
![Docker](https://img.shields.io/badge/Docker-ready-blue)

Backend сервис для проекта **Gadalka** — приложения для гадания на картах.

Сервис предоставляет **REST API** для фронтенда и отвечает за:

* управление пользователями
* получение профиля пользователя
* работу с картами
* генерацию раскладов
* хранение истории гаданий

---

# 🚀 Технологии

Проект построен на современном backend-стеке:

* **Java 17**
* **Spring Boot**
* **Spring Web**
* **Spring Data JPA**
* **PostgreSQL**
* **Flyway** — миграции БД
* **JWT** — аутентификация
* **Docker / Docker Compose**
* **Lombok**
* **OpenAPI / Swagger**

---

# 📦 Быстрый запуск

## 1️⃣ Клонирование репозитория

```bash
git clone https://github.com/aapogoretskiy/gadalka-backend
cd gadalka-backend
```

---

# 🐳 Запуск базы данных

Для локальной разработки используется **PostgreSQL в Docker**.

Запустите контейнер:

```bash
docker-compose up -d
```

Проверить контейнер:

```bash
docker ps
```

После запуска PostgreSQL будет доступен на:

```
localhost:5432
```

---

# ⚙️ Конфигурация приложения

Файл конфигурации:

```
src/main/resources/application.yaml
```

Пример настроек БД:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/gadalka
    username: gadalka
    password: gadalka
```

---

# 🗄️ Миграции базы данных

Для управления схемой базы используется **Flyway**.

Путь к миграциям:

```
src/main/resources/db.migration
```

Пример файлов миграций:

```
V1__init.sql
V2__seed_user.sql
V3__create_user_profile.sql
```

Миграции выполняются **автоматически при запуске приложения**.

---

# ▶️ Запуск приложения

Запуск через Maven:

```bash
mvn spring-boot:run
```

или

```bash
./mvnw spring-boot:run
```

После запуска сервис будет доступен:

```
http://localhost:8080
```

---

# 📚 API документация

Swagger UI доступен по адресу:

```
http://localhost:8080/swagger-ui/index.html
```

Через Swagger можно:

* просматривать API
* тестировать эндпоинты
* передавать JWT токен

---

# 🔐 Аутентификация

Сервис использует **JWT токены**.

Для доступа к защищенным эндпоинтам необходимо передать header:

```
Authorization: Bearer <token>
```

---

# 📡 Пример API запроса

Получение профиля пользователя:

```http
GET /profile
Authorization: Bearer <token>
```

Пример ответа:

```json
{
  "id": 2,
  "birthDate": "1999-01-01",
  "birthTime": "12:30:00",
  "birthCity": "Нижний Новгород",
  "goals": [
    "MONEY",
    "LOVE"
  ]
}
```

---

# 📂 Структура проекта

```
src/main/java/ru/sapa/gadalka_backend

├── configuration
│   └── конфигурация Spring
│
├── api
│   ├── dto
│   │   └── объекты передачи данных
│   │
│   └── REST контроллеры
│
├── service
│   └── бизнес логика
│
├── repository
│   └── работа с базой данных
│
├── domain
│   └── JPA сущности
│
├── mapper
│   └── преобразование Entity ↔ DTO
│
└── security
    └── JWT аутентификация
```

---

# 🐳 docker-compose

Пример docker-compose для локальной разработки:

```yaml
version: "3.9"

services:
  postgres:
    image: postgres:16
    container_name: gadalka-postgres
    environment:
      POSTGRES_DB: gadalka
      POSTGRES_USER: gadalka
      POSTGRES_PASSWORD: gadalka
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data

volumes:
  pgdata:
```

---

# 🛠️ Требования

Для разработки необходимо:

* **Java 17+**
* **Maven 3.9+**
* **Docker**

---
