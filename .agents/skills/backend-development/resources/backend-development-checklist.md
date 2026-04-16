# NewPlanner Backend Development Checklist

Use this comprehensive checklist when developing backend features for NewPlanner.

## Pre-development

- [ ] **Understand the feature:** What problem does this backend feature solve?
- [ ] **Define data model:** What entities/tables do we need?
- [ ] **Document API contract:** What are request/response formats?
- [ ] **Check existing code:** Is there similar functionality we can build on?
- [ ] **Plan external integrations:** Do we need to call third-party APIs?
- [ ] **Consider scale:** How many requests/records will this handle?

## API Design

- [ ] **Define REST endpoints** (GET, POST, PUT, DELETE)
- [ ] **Document HTTP status codes:** 200, 201, 400, 401, 404, 500
- [ ] **Create request DTOs** with validation annotations
- [ ] **Create response DTOs** (never expose entities directly)
- [ ] **Add pagination** for list endpoints (page, size, sort)
- [ ] **Plan error responses** (consistent error format)
- [ ] **Document authentication/authorization** requirements

## Entity Design

- [ ] **Create JPA entities** with proper annotations
- [ ] **Add ID generation:** `@GeneratedValue(strategy = GenerationType.UUID)`
- [ ] **Add timestamps:** `createdAt`, `updatedAt` with `@PrePersist`, `@PreUpdate`
- [ ] **Define relationships:** `@OneToMany`, `@ManyToOne`, cascade rules
- [ ] **Add constraints:** `@NotNull`, `@Column(nullable = false)`, unique constraints
- [ ] **Use enums:** `@Enumerated(EnumType.STRING)` for fixed values
- [ ] **Add Javadoc:** Document each entity's purpose

## Repository Layer

- [ ] **Extend JpaRepository** for CRUD operations
- [ ] **Add custom query methods:** Descriptive method names
- [ ] **Use @Query** for complex searches if needed
- [ ] **Add pagination support:** `PagingAndSortingRepository`
- [ ] **Test repository** with unit tests

## Service Layer

- [ ] **Create @Service class** with business logic
- [ ] **Inject dependencies** via @Autowired
- [ ] **Implement methods** following the feature requirements
- [ ] **Add validation** at service level
- [ ] **Handle errors** with custom exceptions
- [ ] **Use logging** (@Slf4j with log.info, log.error)
- [ ] **Add @Transactional** for multi-step operations
- [ ] **Consider async operations** (@Async) for long tasks
- [ ] **Implement caching** where appropriate (@Cacheable)

## Controller Layer

- [ ] **Create @RestController** with @RequestMapping
- [ ] **Define endpoints** with proper HTTP methods
- [ ] **Add @Valid** for request validation
- [ ] **Use appropriate status codes:** 201 for create, 200 for get, 204 for delete
- [ ] **Add Javadoc** explaining each endpoint
- [ ] **Handle exceptions** gracefully
- [ ] **Add logging** for request tracking
- [ ] **Include pagination** parameters for list endpoints

## Validation

- [ ] **Request DTO validation** (@NotNull, @NotBlank, @Min, @Max, @Email, etc.)
- [ ] **Custom field validators** if needed
- [ ] **Database constraint validation** in entities
- [ ] **API response validation** for external API calls
- [ ] **Unit tests** for validation rules

## External API Integration

- [ ] **Create API client class** (@Component or @Service)
- [ ] **Inject API keys** from application.properties
- [ ] **Use RestTemplate or WebClient** for HTTP calls
- [ ] **Add error handling** (try-catch, custom exceptions)
- [ ] **Log API calls** for debugging
- [ ] **Validate API responses** before processing
- [ ] **Test with mock data** or stubs
- [ ] **Document API requirements:** Rate limits, authentication, response format

## Error Handling

- [ ] **Create custom exception classes** for different errors
- [ ] **Create @RestControllerAdvice** global exception handler
- [ ] **Map exceptions to HTTP status codes**
- [ ] **Create consistent ErrorResponse DTO**
- [ ] **Log errors** with proper context
- [ ] **Hide internal details** from error messages
- [ ] **Test error scenarios** with unit tests

## Database

- [ ] **Choose database:** PostgreSQL (default for NewPlanner)
- [ ] **Set up connection** in application.properties
- [ ] **Configure JPA/Hibernate** (dialect, ddl-auto)
- [ ] **Create migrations** if needed (Flyway or Liquibase)
- [ ] **Define indexes** for frequently queried columns
- [ ] **Plan for cascading** deletes if needed
- [ ] **Test with data** before deployment

## Unit Testing

- [ ] **Test service layer** with mocked dependencies
- [ ] **Test repository layer** with @DataJpaTest
- [ ] **Test controller layer** with @WebMvcTest
- [ ] **Test validation** with invalid inputs
- [ ] **Test error handling** with exceptions
- [ ] **Use mock data** via @Mock or @MockBean
- [ ] **Verify interactions** with verify()
- [ ] **Achieve >80% code coverage**

## Integration Testing

- [ ] **Test full flow:** Controller → Service → Repository → Database
- [ ] **Start embedded database** or test container
- [ ] **Test with real HTTP requests** using TestRestTemplate
- [ ] **Test API contracts** (request/response format)
- [ ] **Test error scenarios** (validation, not found, etc.)
- [ ] **Clean up data** after each test

## Performance

- [ ] **Add caching** for frequently accessed data
- [ ] **Optimize database queries** (avoid N+1)
- [ ] **Use @EntityGraph** or projections
- [ ] **Add indexes** on frequently queried columns
- [ ] **Test with large datasets**
- [ ] **Monitor response times**
- [ ] **Add circuit breaker** for external APIs

## Documentation

- [ ] **Add Javadoc** to classes and methods
- [ ] **Document API** with Swagger/OpenAPI annotations
- [ ] **Create README** with setup instructions
- [ ] **Document configuration** (application.properties)
- [ ] **Document database schema** (entity relationships)
- [ ] **Add code comments** for complex logic
- [ ] **Document examples** of API calls

## Configuration

- [ ] **Set up application.properties** with all required keys
- [ ] **Use environment variables** for sensitive data (API keys, passwords)
- [ ] **Configure logging** (log levels, format)
- [ ] **Set up database** connection
- [ ] **Configure Spring profiles** (dev, test, prod)

## Security

- [ ] **Validate all inputs** (OWASP Top 10)
- [ ] **Use HTTPS** in production
- [ ] **Implement authentication** if needed (JWT, OAuth)
- [ ] **Add authorization** checks (@PreAuthorize)
- [ ] **Sanitize outputs** to prevent injection
- [ ] **Never log sensitive data** (passwords, API keys)
- [ ] **Use CORS** properly if calling from frontend

## Before Deployment

- [ ] **All tests pass** (unit + integration)
- [ ] **Code reviewed** by team member
- [ ] **Performance tested** under load
- [ ] **Error handling verified** (expected and edge cases)
- [ ] **Documentation complete** (Javadoc, API docs)
- [ ] **Database migrations ready** (if schema changed)
- [ ] **API contract matches frontend** requirements
- [ ] **Configuration set up** for production
- [ ] **Security review** passed
- [ ] **Logging and monitoring** configured

## Post-deployment

- [ ] **Monitor logs** for errors
- [ ] **Check metrics** (response times, error rates)
- [ ] **Monitor database** (slow queries, locks)
- [ ] **Test API** with real data
- [ ] **Be ready to rollback** if issues arise
- [ ] **Gather user feedback**
- [ ] **Document lessons learned**
