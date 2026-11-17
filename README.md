# Lab 5 & 6 - Spring Security (Authentication & Role-Based Authorization)

## Course Information
- **Course:** CPAN-228 Web Application Development
- **Topic:** Spring Security — Authentication, Role-Based Authorization (`USER` vs. `ADMIN`), `SecurityFilterChain`, `UserDetailsService`, BCrypt Password Hashing & Thymeleaf Security Tags
- **Application Theme:** FitSquad Hub (Fitness Challenge & Athlete Workout Platform)

---

## Overview

In Lab 4, you built custom Spring Data JPA queries, dynamic sorting, and pagination on top of the **FitSquad Hub** (`Challenge`, `WorkoutLog`, and `Coach`) application. In this lab, you will secure that same application using **Spring Security**.

The starter project already has the **Challenges Catalog** (`/challenges`), **Athlete Workout Logs** (`/workout-logs`, `/log-workout`), and **Certified Coaches** (`/coaches`) pages built and backed by H2 + Spring Data JPA. However, the platform is currently wide open—anyone can create official challenges, register coaches, or inspect the H2 database console without logging in.

Your job is to wire up the **Spring Security** layer end-to-end:
1. Add the Spring Security & Thymeleaf Security dependencies.
2. Create the `User` entity and `UserRepository`.
3. Implement `UserDetailsService` to load users from the H2 database.
4. Configure the `SecurityFilterChain` and `BCryptPasswordEncoder` in `SecurityConfig`.
5. Build a custom Login page (`/login`) and Athlete Registration flow (`/register`).
6. Seed a default `ADMIN` account on startup using an `ApplicationRunner` (`DataLoader`).
7. Protect administrative routes and hide admin-only navigation links in Thymeleaf using `sec:authorize`.

By the end of this lab:
- **Unauthenticated visitors** will be redirected to `/login` (with access only to `/login`, `/register`, and static assets).
- **Regular Athletes (`USER`)** will be able to browse challenges (`/`, `/challenges`), view and log workouts (`/workout-logs`, `/log-workout`), and view coaches (`/coaches`), but **cannot** access admin creation pages or the H2 console.
- **Administrators (`ADMIN`)** will have full access to **Challenge & Coach management** (`/create-challenge`, `/create-coach`) as well as the **H2 Database Console** (`/h2-console/**`).

---

## Role-Based Access Control (RBAC) Matrix

| Role | Who They Are | What They Can Access |
| :--- | :--- | :--- |
| **`USER`** | Self-registered Athletes (via `/register`) | Catalogs & Athlete Workout Logging (`/`, `/challenges`, `/workout-logs`, `/log-workout`, `/coaches`) |
| **`ADMIN`** | Seeded on startup (via `DataLoader`) | **Everything** — including `/create-challenge`, `/create-coach`, and `/h2-console/**` |

---

## Getting Started

### GitHub Setup

1. **Fork the Repository** on GitHub to your personal account.
2. **Clone Your Fork** locally:
   ```bash
   git clone https://github.com/YOUR-USERNAME/fitness-challenge-hub-week8-9-lab5.git
   cd fitness-challenge-hub-week8-9-lab5
   ```
3. **Add Upstream Remote:**
   ```bash
   git remote add upstream https://github.com/Web-App-Development-Fall-2026/fitness-challenge-hub-week8-9-lab5.git
   ```
4. **Create a Feature Branch** for Lab 5 & 6:
   ```bash
   git checkout -b feature/lab5-yourname
   ```
   *(Replace `yourname` with your actual name, e.g., `feature/lab5-john-doe`)*
5. **Run the Starter Project:**
   ```bash
   ./mvnw spring-boot:run
   ```
   *(On Windows: `.\mvnw.cmd spring-boot:run`)*

---

## Target Architecture for Lab 5 & 6

You will add and update the following components:

```
src/main/java/com/example/FitnessChallengeApp_Test/
├── config/
│   ├── SecurityConfig.java               <-- Part 4: SecurityFilterChain & BCryptPasswordEncoder
│   └── DataLoader.java                   <-- Part 7: ApplicationRunner seeding ADMIN user
├── controller/
│   └── AuthController.java               <-- Parts 5 & 6: GET /login, GET /register, POST /register
├── model/
│   └── User.java                         <-- Part 2: JPA User entity (@Table(name = "USERS"))
├── repository/
│   └── UserRepository.java               <-- Part 2: JpaRepository with findByUsername()
└── service/
    └── CustomUserDetailsService.java     <-- Part 3: Implements Spring Security UserDetailsService
src/main/resources/templates/
├── fragments/
│   └── navbar.html                       <-- Part 8: Role-gated links (sec:authorize) & Logout button
├── login.html                            <-- Part 5: Custom FitSquad Hub login form
└── register.html                         <-- Part 6: Athlete self-registration form
```

---

## Lab 5 & 6 Assignment

### Part 1 — Add the Spring Security Dependencies

Open [`pom.xml`](file:///pom.xml) and add the following two dependencies inside the `<dependencies>` block:

1. **`spring-boot-starter-security`** from the `org.springframework.boot` group:
   ```xml
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-security</artifactId>
   </dependency>
   ```
2. **`thymeleaf-extras-springsecurity6`** from the `org.thymeleaf.extras` group (enables `sec:authorize` and `sec:authentication` attributes in Thymeleaf templates):
   ```xml
   <dependency>
       <groupId>org.thymeleaf.extras</groupId>
       <artifactId>thymeleaf-extras-springsecurity6</artifactId>
   </dependency>
   ```

---

### Part 2 — Create the `User` Entity & `UserRepository`

1. **Create `User.java`** inside `src/main/java/com/example/FitnessChallengeApp_Test/model/`:
   - Annotate with `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Entity`, and `@Table(name = "USERS")`.
   - > **Important Note:** In H2 and standard SQL, `USER` is a reserved keyword! Always map your table name to `"USERS"` using `@Table(name = "USERS")`.
   - Define the following fields to support authentication and authorization:
     - `id` (`Long`) — annotated with `@Id` and `@GeneratedValue(strategy = GenerationType.IDENTITY)`
     - `username` (`String`) — annotated with `@NotBlank` and `@Column(nullable = false, unique = true)`
     - `password` (`String`) — stores the BCrypt-encoded password hash (`@NotBlank`, `@Column(nullable = false)`)
     - `role` (`String`) — stores the user's role, e.g., `"USER"` or `"ADMIN"` (`@Column(nullable = false)`)

2. **Create `UserRepository.java`** inside `src/main/java/com/example/FitnessChallengeApp_Test/repository/`:
   - Extend `JpaRepository<User, Long>`.
   - Add a derived query method to look up a user by username:
     ```java
     Optional<User> findByUsername(String username);
     boolean existsByUsername(String username);
     ```

---

### Part 3 — Implement `UserDetailsService`

Create a new service class (e.g., `CustomUserDetailsService.java`) inside `src/main/java/com/example/FitnessChallengeApp_Test/service/`:

1. Implement the `org.springframework.security.core.userdetails.UserDetailsService` interface.
2. Annotate the class with `@Service`.
3. Inject `UserRepository` (via constructor injection or `@Autowired`).
4. Override `loadUserByUsername(String username)`:
   - Look up the `User` from `userRepository.findByUsername(username)`.
   - If no user is found, throw a `UsernameNotFoundException("User not found: " + username)`.
   - Return a Spring Security `UserDetails` object built from the database `User`'s `username`, `password`, and `role`:
     ```java
     return org.springframework.security.core.userdetails.User
             .withUsername(user.getUsername())
             .password(user.getPassword())
             .roles(user.getRole()) // Automatically prefixes with "ROLE_" (e.g., "ROLE_ADMIN")
             .build();
     ```

---

### Part 4 — Create `SecurityConfig`

Create a new package `com.example.FitnessChallengeApp_Test.config` and add `SecurityConfig.java`:

1. Annotate the class with `@Configuration` and `@EnableWebSecurity`.
2. Define a `PasswordEncoder` bean that uses `BCryptPasswordEncoder`:
   ```java
   @Bean
   public PasswordEncoder passwordEncoder() {
       return new BCryptPasswordEncoder();
   }
   ```
3. Define a `SecurityFilterChain` bean that configures the following authorization rules **in order**:
   - `/login`, `/register`, `/css/**`, `/js/**` $\rightarrow$ **Everyone** (`permitAll()`, no login required)
   - `/create-challenge`, `/create-coach` $\rightarrow$ **`ADMIN` only** (`.hasRole("ADMIN")`)
   - `/h2-console/**` $\rightarrow$ **`ADMIN` only** (`.hasRole("ADMIN")`)
   - **Everything else** (`/`, `/challenges`, `/workout-logs`, `/log-workout`, `/coaches`, etc.) $\rightarrow$ **Any authenticated user** (`.authenticated()`)
4. Configure **Form Login**:
   - Use `/login` as the custom login page (`.loginPage("/login")`).
   - Redirect to `/challenges` (or `/`) on successful login (`.defaultSuccessUrl("/challenges", true)`).
   - Permit all users to access the login endpoint.
5. Configure **Logout**:
   - Redirect to `/login?logout` on successful logout (`.logoutSuccessUrl("/login?logout")`).
6. **H2 Console Compatibility:**
   - Disable the `X-Frame-Options` header so the H2 console frames can render (`headers -> headers.frameOptions(frame -> frame.disable())`).
   - Ignore CSRF protection for the H2 console path (`csrf -> csrf.ignoringRequestMatchers("/h2-console/**")`).

---

### Part 5 — Create the Login Page (`/login`)

1. Create `src/main/resources/templates/login.html` styled to match FitSquad Hub:
   - Build an HTML `<form th:action="@{/login}" method="post">` with `name="username"` and `name="password"` inputs.
   - Display a friendly alert banner when `${param.error}` is present (*"Invalid username or password."*) and when `${param.logout}` is present (*"You have been signed out safely."*).
   - Include a link to `th:href="@{/register}"` for new athletes to create an account.
2. Add a controller method (`GET /login`) in `AuthController` (or `UserController`) that returns the `"login"` view name.

---

### Part 6 — Create the Registration Flow (`/register`)

1. In `AuthController` (or `UserController` / `RegistrationController`), implement:
   - **`GET /register`**: Adds an empty `User` object to the model and returns `"register"`.
   - **`POST /register`**:
     - Validates the submitted `User` (and checks if `userRepository.existsByUsername(user.getUsername())` is already true).
     - Encodes the raw password using `passwordEncoder.encode(user.getPassword())` before saving.
     - Assigns the default role `"USER"` to all self-registered athletes (`user.setRole("USER")`).
     - Saves the user to `userRepository` and redirects to `"redirect:/login?registered"`.
2. Create `src/main/resources/templates/register.html` with a form posting to `th:action="@{/register}"`.

---

### Part 7 — Seed an Admin User with `DataLoader`

Create `DataLoader.java` inside `src/main/java/com/example/FitnessChallengeApp_Test/config/` implementing `ApplicationRunner`:

1. Annotate the class with `@Component` and inject `UserRepository` and `PasswordEncoder`.
2. Inside `run(ApplicationArguments args)`:
   - Check if a user with username `"admin"` exists (`userRepository.findByUsername("admin").isEmpty()`).
   - If not, seed the admin user with:
     - **Username:** `"admin"`
     - **Password:** `passwordEncoder.encode("admin123")`
     - **Role:** `"ADMIN"`
   - *(Optional)* You may also seed a default athlete account (`username = "athlete"`, `password = "athlete123"`, `role = "USER"`) for quick testing.

---

### Part 8 — Protect the Navigation Links in Thymeleaf

1. Open [`src/main/resources/templates/fragments/navbar.html`](file:///src/main/resources/templates/fragments/navbar.html) (and [`challenges.html`](file:///src/main/resources/templates/challenges.html) / [`coaches.html`](file:///src/main/resources/templates/coaches.html)) and declare the Spring Security namespace on the `<html>` tag:
   ```html
   <html lang="en"
         xmlns:th="http://www.thymeleaf.org"
         xmlns:sec="http://www.thymeleaf.org/extras/spring-security">
   ```
2. Use `sec:authorize="hasRole('ADMIN')"` to wrap administrative links so regular `USER` accounts do not see them:
   - **Create Challenge** link (`/create-challenge`)
   - **Register Coach** link (`/create-coach`)
   - **H2 Console** link (`/h2-console`)
3. Display the authenticated user's username (`<span sec:authentication="name"></span>`) and add a **Logout** `<form th:action="@{/logout}" method="post">` button visible to all authenticated users using `sec:authorize="isAuthenticated()"`.

---

## Testing Your Work

Verify each of the following scenarios manually in your browser:

1. **Unauthenticated Redirect:**
   - Visit `http://localhost:8080/challenges` or `http://localhost:8080/workout-logs` in an incognito window $\rightarrow$ Confirm you are redirected to `http://localhost:8080/login`.
2. **Self-Registration (`USER` Role):**
   - Navigate to `http://localhost:8080/register`, register a new athlete account (e.g., `alex` / `pass123`), and log in at `/login`.
3. **Athlete (`USER`) Permissions:**
   - Confirm that logged in as `alex` (`USER`), you can view `/challenges`, `/workout-logs`, and `/coaches`, and submit a new workout at `/log-workout`.
   - Confirm that the **Create Challenge**, **Register Coach**, and **H2 Console** links are hidden in the navbar.
   - Manually type `http://localhost:8080/create-challenge`, `http://localhost:8080/create-coach`, or `http://localhost:8080/h2-console` in the address bar $\rightarrow$ Confirm Spring Security blocks access with **HTTP 403 Forbidden**.
4. **Admin (`ADMIN`) Permissions:**
   - Click **Logout** (confirm redirect to `/login?logout`), then sign in with the seeded admin credentials (`admin` / `admin123`).
   - Confirm that **Create Challenge**, **Register Coach**, and **H2 Console** links appear in the navbar and that `/create-challenge`, `/create-coach`, and `/h2-console` all load normally.

---

## Submission Workflow

```bash
# Check status and stage changes
git status
git add .

# Commit your work
git commit -m "Lab 5 & 6: Implement Spring Security authentication, RBAC, and Thymeleaf security tags"

# Push to your fork
git push origin feature/lab5-yourname
```
