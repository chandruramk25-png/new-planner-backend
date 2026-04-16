# Backend Development Skills

This folder contains skills for developing the NewPlanner Spring Boot backend.

## Overview

The backend-development skill provides comprehensive guidance for building robust REST APIs, integrating external services, managing databases, and implementing business logic for the NewPlanner AI Trip Planner.

## Skill: backend-development

**Main file:** [SKILL.md](SKILL.md)

**Purpose:** Guides development of Spring Boot REST APIs, including:
- API design and controller creation
- JPA entity and repository setup
- Service layer implementation
- JavaScript integration and validation
- Database operations
- Error handling
- Testing strategies

**Use when:** Building new endpoints, implementing features, integrating APIs, fixing bugs

## Resources

### [backend-development-checklist.md](resources/backend-development-checklist.md)
Comprehensive pre-, during-, and post-development checklist covering:
- API design validation
- Entity and repository setup
- Service and controller implementation
- Testing and performance
- Deployment readiness

**Use:** Before starting a feature to ensure nothing is missed

### [spring-boot-patterns.md](resources/spring-boot-patterns.md)
Quick reference for common Spring Boot patterns:
- REST controller template
- Service layer template
- Entity template
- Validation examples
- Error handling
- Caching strategies
- Async operations
- Database queries
- Configuration examples

**Use:** When you need a quick code snippet or pattern

## Examples

### [backend-code-examples.md](examples/backend-code-examples.md)

Complete, production-ready examples including:

**Example 1: Complete Feature Flow**
- DTOs (request/response)
- JPA entities with relationships
- Repository queries
- Service layer with validation and enrichment
- REST controller with all CRUD operations
- Exception handling with global handler
- Unit and integration tests

**Example 2: External API Integration**
- OpenWeather client with caching
- Error handling
- Response parsing and validation

## Quick Start

### For your first backend feature:

1. **Read:** [SKILL.md](SKILL.md) — Full guidance
2. **Reference:** [spring-boot-patterns.md](resources/spring-boot-patterns.md) — Code templates
3. **Use:** [backend-code-examples.md](examples/backend-code-examples.md) — Copy and adapt
4. **Verify:** [backend-development-checklist.md](resources/backend-development-checklist.md) — Nothing missed

### Project Structure

NewPlanner backend follows this structure:

```
backend/
├─── src/main/java/com/newplanner/
│    ├─── controller/           # REST API endpoints
│    ├─── service/              # Business logic
│    ├─── repository/           # Database access (Spring Data JPA)
│    ├─── entity/               # JPA entities
│    ├─── dto/                  # Data transfer objects (DTO)
│    ├─── exception/            # Custom exceptions
│    ├─── config/               # Spring configuration
│    ├─── client/               # Third-party API clients
│    └─── util/                 # Utility classes
├─── src/main/resources/
│    ├─── application.properties    # Configuration
│    └─── db/migration/            # Database migrations
├─── src/test/java/             # Unit and integration tests
├─── pom.xml                     # Maven configuration
└─── README.md
```

## Core Tech Stack

- **Framework:** Spring Boot 3.x
- **Build:** Maven
- **Database:** PostgreSQL (with Spring Data JPA)
- **External APIs:** OpenAI, OpenWeather, OpenTripMap, OpenRouteService
- **Testing:** JUnit 5, Mockito, Spring Test
- **Logging:** SLF4J

## Common Development Tasks

### Creating a new API endpoint:

1. Define `@RestController` and `@RequestMapping`
2. Create request/response `DTOs` with validation
3. Create `JPA Entity` and `Repository`
4. Implement `Service` with business logic
5. Add `@PostMapping`, `@GetMapping`, etc. to controller
6. Create custom exception classes
7. Add global exception handler
8. Write unit and integration tests
9. Use checklist to verify completeness

### Integrating a new third-party API:

1. Create API `@Component` class (e.g., `OpenWeatherApiClient`)
2. Inject API key from `application.properties`
3. Use `RestTemplate` for HTTP calls
4. Wrap in try-catch for error handling
5. Validate response format before parsing
6. Log all calls for debugging
7. Add tests with mock responses
8. Cache results if read-only and non-time-critical

### Handling errors:

1. Create custom exceptions extending `RuntimeException`
2. Create `@RestControllerAdvice` global handler
3. Map exceptions to appropriate HTTP status codes
4. Return consistent `ErrorResponse` format
5. Log errors with context
6. Hide internal details from error messages

## Best Practices for NewPlanner

### Data Flow Pattern: Validate → Enrich → Optimize → Store → Display

For endpoint that generates itineraries:

```
User Input
   ↓
Validate (JSON structure, required fields)
   ↓
Enrich (add lat/lon, costs, travel times from APIs)
   ↓
Optimize (reorder places, calculate routes)
   ↓
Store (save to database)
   ↓
Display (return to frontend)
```

### Code Organization

- **Controllers:** Handle HTTP, validation (`@Valid`), status codes
- **Services:** Business logic, validation, error handling
- **Repositories:** Database queries, data access
- **Entities:** JPA entities, relationships, constraints
- **DTOs:** API contracts (never expose entities directly)
- **Exceptions:** Custom exceptions for specific errors
- **Clients:** Third-party API integration

### Testing Strategy

- **Unit tests:** Service layer with mocked dependencies
- **Integration tests:** Full flow with embedded database
- **Controller tests:** HTTP layer with MockMvc
- **Validation tests:** Input validation with invalid data

## Troubleshooting

**API not responding?**
- Check controller endpoint path matches request URL
- Ensure `@RequestMapping` and `@PostMapping` are correct
- Verify `@Valid` is on request parameter

**Database queries slow?**
- Use `@EntityGraph` to avoid N+1 queries
- Add database indexes on frequently queried columns
- Use projections or native SQL for complex queries
- Check query execution plans with `EXPLAIN`

**External API integration failing?**
- Verify API key is set in `application.properties`
- Check API response format matches your parsing code
- Add detailed logging to debug API calls
- Test with mock data first
- Implement retry mechanism for transient failures

**Tests failing?**
- Ensure mocks are properly set up with `@MockBean`
- Use `@SpringBootTest` for integration tests
- Use `@WebMvcTest` for controller tests
- Look at test error messages and stack traces
- Run tests with debug logging enabled

## References

- Main Guidance: [SKILL.md](SKILL.md)
- Spring Boot Docs: https://spring.io/projects/spring-boot
- Spring Data JPA: https://spring.io/projects/spring-data-jpa
- Testing Guide: https://spring.io/guides/gs/testing-web/
- NewPlanner Frontend: `../../frontend/`

---

**Tip:** Start with the checklist and code examples, then dive into SKILL.md for detailed guidance!
