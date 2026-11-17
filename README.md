# Lab 5 & 6 - Spring Security (Authentication & Role-Based Authorization)

## Course Information
- **Course:** CPAN-228 Web Application Development
- **Topic:** Spring Security — Authentication, Role-Based Authorization (`USER`, `COACH`, and `ADMIN`), `SecurityFilterChain`, `UserDetailsService`, BCrypt Password Hashing & Thymeleaf Security Tags
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
5. Build a custom Login page (`/login`) and a Registration flow (`/register`) with a **"Register as a Coach"** checkbox.
6. Seed a default `ADMIN` account on startup using an `ApplicationRunner` (`DataLoader`).
7. Protect administrative and coach routes and conditionally render navigation links in Thymeleaf using `sec:authorize`.

By the end of this lab:
- **Unauthenticated visitors** will be redirected to `/login` (with access only to `/login`, `/register`, and static assets).
- **Regular Athletes (`USER`)** (registered with the Coach checkbox unchecked) can browse challenges (`/`, `/challenges`), view and log workouts (`/workout-logs`, `/log-workout`), and view coaches (`/coaches`), but **cannot** create challenges, register coaches, or access the H2 console.
- **Coaches (`COACH`)** (registered by checking the **"Register as a Coach"** checkbox on `/register`) have access to all Athlete pages **plus** the ability to create new challenges (`/create-challenge`), but **cannot** access Admin-only pages (`/create-coach` or `/h2-console/**`).
- **Administrators (`ADMIN`)** (seeded on startup via `DataLoader`) have full access to **everything** — including `/create-challenge`, `/create-coach`, and the **H2 Database Console** (`/h2-console/**`).

---

## Role-Based Access Control (RBAC) Matrix

| Role | Who They Are / How Created | What They Can Access |
| :--- | :--- | :--- |
| **`USER`** | Self-registered Athletes on `/register` *(Coach checkbox unchecked)* | Catalogs & Athlete Workout Logging (`/`, `/challenges`, `/workout-logs`, `/log-workout`, `/coaches`) |
| **`COACH`** | Self-registered Coaches on `/register` *(Coach checkbox checked)* | All `USER` pages **+** Create Challenge (`/create-challenge`) *(No access to `/create-coach` or `/h2-console/**`)* |
| **`ADMIN`** | Seeded on startup via `DataLoader` (`admin` / `admin123`) | **Everything** — `/create-challenge`, `/create-coach`, and `/h2-console/**` |

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
└── register.html                         <-- Part 6: Self-registration form with Coach checkbox
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
     - `role` (`String`) — stores the user's role: `"USER"`, `"COACH"`, or `"ADMIN"` (`@Column(nullable = false)`)

2. **Create `UserRepository.java`** inside `src/main/java/com/example/FitnessChallengeApp_Test/repository/`:
   - Extend `JpaRepository<User, Long>`.
   - Add derived query methods to look up a user by username:
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
             .roles(user.getRole()) // Automatically prefixes with "ROLE_" (e.g., "ROLE_COACH", "ROLE_ADMIN")
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
   - `/login`, `/register`, `/css/**`, `/js/**` $\rightarrow$ **Everyone** (`.permitAll()`, no login required)
   - `/create-challenge` $\rightarrow$ **`COACH` or `ADMIN`** (`.hasAnyRole("COACH", "ADMIN")`)
   - `/create-coach`, `/h2-console/**` $\rightarrow$ **`ADMIN` only** (`.hasRole("ADMIN")`)
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
   - Include a link to `th:href="@{/register}"` for new athletes and coaches to create an account.
2. Add a controller method (`GET /login`) in `AuthController` (or `UserController`) that returns the `"login"` view name.

---

### Part 6 — Create the Registration Flow with Coach Checkbox (`/register`)

1. Create `src/main/resources/templates/register.html` with a form posting to `th:action="@{/register}"`:
   - Include inputs for `username` and `password`.
   - Include a **"Register as a Coach"** checkbox so users can choose whether to sign up as a standard Athlete (`USER`) or a Coach (`COACH`):
     ```html
     <div class="form-check form-switch mb-4">
         <input class="form-check-input" type="checkbox" id="registerAsCoach" name="registerAsCoach" value="true">
         <label class="form-check-label text-light" for="registerAsCoach">
             Register as a Certified <strong>Coach</strong> (allows creating fitness challenges)
         </label>
     </div>
     ```
2. In `AuthController` (or `UserController` / `RegistrationController`), implement:
   - **`GET /register`**: Adds an empty `User` object to the model and returns `"register"`.
   - **`POST /register`**:
     - Accepts the `User` model attribute and `@RequestParam(defaultValue = "false") boolean registerAsCoach`.
     - Encodes the raw password using `passwordEncoder.encode(user.getPassword())` before saving.
     - Assigns the role based on the checkbox:
       ```java
       user.setRole(registerAsCoach ? "COACH" : "USER");
       ```
       *(Note: `"ADMIN"` can never be assigned through the public registration form!)*
     - Saves the user to `userRepository` and redirects to `"redirect:/login?registered"`.

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

---

### Part 8 — Protect the Navigation Links in Thymeleaf

1. Open [`src/main/resources/templates/fragments/navbar.html`](file:///src/main/resources/templates/fragments/navbar.html) (and [`challenges.html`](file:///src/main/resources/templates/challenges.html) / [`coaches.html`](file:///src/main/resources/templates/coaches.html)) and declare the Spring Security namespace on the `<html>` tag:
   ```html
   <html lang="en"
         xmlns:th="http://www.thymeleaf.org"
         xmlns:sec="http://www.thymeleaf.org/extras/spring-security">
   ```
2. Use `sec:authorize="hasAnyRole('COACH', 'ADMIN')"` to wrap the **Create Challenge** link (`/create-challenge`) so both Coaches and Admins can see it, while regular Athletes (`USER`) cannot:
   ```html
   <li class="nav-item" sec:authorize="hasAnyRole('COACH', 'ADMIN')">
       <a class="nav-link text-light px-3 rounded-pill" th:href="@{/create-challenge}">
           <i class="bi bi-plus-circle-fill me-1 text-info"></i> Create Challenge
       </a>
   </li>
   ```
3. Use `sec:authorize="hasRole('ADMIN')"` to wrap Admin-only links so neither `USER` nor `COACH` accounts can see them:
   - **Register Coach** button/link (`/create-coach`)
   - **H2 Console** link (`/h2-console`)
4. Display the authenticated user's username (`<span sec:authentication="name"></span>`) and add a **Logout** `<form th:action="@{/logout}" method="post">` button visible to all authenticated users using `sec:authorize="isAuthenticated()"`.

---

## Testing Your Work

Verify each of the following scenarios manually in your browser:

1. **Unauthenticated Redirect:**
   - Visit `http://localhost:8080/challenges` or `http://localhost:8080/workout-logs` in an incognito window $\rightarrow$ Confirm you are redirected to `http://localhost:8080/login`.
2. **Athlete Self-Registration (`USER` Role — Checkbox Unchecked):**
   - Navigate to `http://localhost:8080/register`, register an athlete account (e.g., `alex` / `pass123`) **without** checking the Coach box, and log in at `/login`.
   - Confirm you can view `/challenges`, `/workout-logs`, `/coaches`, and submit a workout at `/log-workout`.
   - Confirm **Create Challenge**, **Register Coach**, and **H2 Console** are hidden, and visiting `/create-challenge` or `/create-coach` directly returns **HTTP 403 Forbidden**.
3. **Coach Self-Registration (`COACH` Role — Checkbox Checked):**
   - Log out, navigate to `http://localhost:8080/register`, register a coach account (e.g., `coach_maya` / `pass123`) **with the "Register as a Coach" checkbox checked**, and log in.
   - Confirm that **Create Challenge** (`/create-challenge`) is visible and accessible, allowing you to launch a new challenge!
   - Confirm that `/create-coach` and `/h2-console` remain hidden and blocked with **HTTP 403 Forbidden**.
4. **Admin (`ADMIN` Role — Seeded Account):**
   - Log out and sign in with the seeded admin credentials (`admin` / `admin123`).
   - Confirm that **Create Challenge** (`/create-challenge`), **Register Coach** (`/create-coach`), and **H2 Console** (`/h2-console`) are all visible and accessible.

---

## Submission Workflow

```bash
# Check status and stage changes
git status
git add .

# Commit your work
git commit -m "Lab 5 & 6: Implement Spring Security authentication, USER/COACH/ADMIN RBAC, and Thymeleaf security tags"

# Push to your fork
git push origin feature/lab5-yourname
```
