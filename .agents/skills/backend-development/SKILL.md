---
name: backend-development
description: Develops Spring Boot REST APIs for NewPlanner AI Trip Planner, including itinerary generation, API integrations, database operations, and data validation. Use when building backend features, creating endpoints, or implementing business logic.
---

# Backend Development Skill

Guides development of NewPlanner's Spring Boot backend, covering API design, database operations, AI integration, and third-party API handling.

## When to use this skill

- Creating new REST API endpoints for features
- Implementing itinerary generation and optimization logic
- Integrating third-party APIs (OpenWeather, OpenTripMap, OpenRouteService)
- Implementing database operations and entity relationships
- Building validation and enrichment pipelines
- Implementing error handling and logging
- Setting up authentication and authorization
- Keywords: Spring Boot, REST API, backend, database, business logic, validation

## Prerequisites

- Spring Boot 3.x knowledge (we use Spring Boot 3.x+)
- Maven for build management
- PostgreSQL/MongoDB for persistence
- Familiarity with REST principles (GET, POST, PUT, DELETE)
- Understanding of JSON serialization
- Basic SQL or query builder knowledge
- Git for version control

## Project Structure

NewPlanner backend follows standard Spring Boot conventions:

```
backend/
├─── src/main/java/com/newplanner/
│    ├─── controller/           # REST API endpoints
│    ├─── service/              # Business logic
│    ├─── repository/           # Database access
│    ├─── entity/               # JPA entities
│    ├─── dto/                  # Data transfer objects
│    ├─── exception/            # Custom exceptions
│    ├─── config/               # Configuration classes
│    ├─── util/                 # Utility classes
│    └─── client/               # External API clients
├─── src/main/resources/
│    ├─── application.properties # Configuration
│    └─── db/migration/         # Flyway migrations (optional)
├─── src/test/java/             # Unit tests
├─── pom.xml                     # Maven configuration
└─── README.md
```

## How to develop backend features

### Step 1: Define the API contract

Start with the REST endpoint specification:

```java
// Define what the endpoint should do
@RestController
@RequestMapping("/api/itineraries")
public class ItineraryController {
    
    /**
     * POST /api/itineraries/generate
     * Request: ItineraryRequest (destination, days, budget, interests)
     * Response: ItineraryResponse (complete itinerary with all details)
     * Status: 200 OK or 400 Bad Request
     */
    @PostMapping("/generate")
    public ResponseEntity<ItineraryResponse> generateItinerary(
        @RequestBody ItineraryRequest request) {
        // Implementation
    }
}
```

Document:
- Request body format (DTOs)
- Response format
- Status codes (200, 201, 400, 401, 404, 500)
- Authentication requirements
- Rate limiting (if applicable)

### Step 2: Create request/response DTOs

Define data transfer objects for clean API contracts:

```java
// Request DTO
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItineraryRequest {
    @NotBlank(message = "Destination is required")
    private String destination;
    
    @Min(value = 1, message = "Days must be at least 1")
    @Max(value = 30, message = "Days cannot exceed 30")
    private Integer numberOfDays;
    
    @NotNull(message = "Budget level is required")
    private BudgetLevel budget;
    
    @NotEmpty(message = "At least one interest is required")
    private List<String> interests;
    
    private String startDate;
    private Integer groupSize;
}

// Response DTO
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItineraryResponse {
    private String id;
    private String destination;
    private Integer numberOfDays;
    private List<ItineraryDayDTO> days;
    private Double totalEstimatedCost;
    private LocalDateTime createdAt;
}
```

Best practices:
- Use `@NotNull`, `@NotBlank`, `@Min`, `@Max` for validation
- Include clear error messages
- Use consistent naming across request/response
- Document field requirements in Javadoc

### Step 3: Create JPA entities

Define database entities that correspond to DTOs:

```java
@Entity
@Table(name = "itineraries")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Itinerary {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false)
    private String destination;
    
    @Column(nullable = false)
    private Integer numberOfDays;
    
    @Enumerated(EnumType.STRING)
    private BudgetLevel budget;
    
    @ElementCollection
    @CollectionTable(name = "itinerary_interests")
    private List<String> interests;
    
    @OneToMany(mappedBy = "itinerary", cascade = CascadeType.ALL)
    private List<ItineraryDay> days;
    
    @Column(name = "total_estimated_cost")
    private Double totalEstimatedCost;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
```

Best practices:
- Use `@GeneratedValue(strategy = GenerationType.UUID)` for IDs
- Add `createdAt` and `updatedAt` timestamps
- Use `@Enumerated(EnumType.STRING)` for enums
- Use `@OneToMany`, `@ManyToOne` for relationships
- Add `cascade = CascadeType.ALL` for dependent entities

### Step 4: Create repository interface

Define database access using Spring Data JPA:

```java
@Repository
public interface ItineraryRepository extends JpaRepository<Itinerary, String> {
    
    // Find by user (add userId field to Itinerary entity)
    List<Itinerary> findByUserId(String userId);
    
    // Find recent itineraries
    List<Itinerary> findByCreatedAtAfter(LocalDateTime date);
    
    // Find by destination and budget
    List<Itinerary> findByDestinationAndBudget(String destination, BudgetLevel budget);
    
    // Custom query
    @Query("SELECT i FROM Itinerary i WHERE i.destination = :destination " +
           "AND i.createdAt > :date ORDER BY i.createdAt DESC")
    List<Itinerary> findRecentByDestination(
        @Param("destination") String destination,
        @Param("date") LocalDateTime date);
}
```

Best practices:
- Extend `JpaRepository<Entity, IdType>`
- Use descriptive method names (Spring auto-implements them)
- Use `@Query` for complex searches
- Add pagination: `JpaRepository<E, ID> extends PagingAndSortingRepository<E, ID>`

### Step 5: Implement service layer

Business logic lives in services:

```java
@Service
@Slf4j
public class ItineraryGenerationService {
    
    @Autowired
    private ItineraryRepository itineraryRepository;
    
    @Autowired
    private OpenAiClient openAiClient;
    
    @Autowired
    private PlaceEnrichmentService placeEnrichmentService;
    
    @Autowired
    private RouteOptimizationService routeOptimizationService;
    
    /**
     * Generate complete itinerary: AI → Validation → Enrichment → Optimization → Store
     */
    public ItineraryResponse generateItinerary(ItineraryRequest request) {
        try {
            // Step 1: Generate AI itinerary
            String aiResponse = openAiClient.generateItinerary(request);
            log.info("AI generated itinerary for destination: {}", request.getDestination());
            
            // Step 2: Validate JSON structure
            Itinerary itinerary = parseAndValidateItinerary(aiResponse, request);
            log.info("Itinerary validated successfully");
            
            // Step 3: Enrich with geospatial data
            itinerary = placeEnrichmentService.enrichItinerary(itinerary);
            log.info("Itinerary enriched with place data");
            
            // Step 4: Optimize routes
            itinerary = routeOptimizationService.optimizeRoutes(itinerary);
            log.info("Routes optimized");
            
            // Step 5: Save to database
            Itinerary saved = itineraryRepository.save(itinerary);
            log.info("Itinerary saved with ID: {}", saved.getId());
            
            // Step 6: Convert to DTO and return
            return convertToResponse(saved);
            
        } catch (JsonException e) {
            log.error("JSON validation failed: {}", e.getMessage());
            throw new InvalidItineraryException("Failed to parse AI response");
        } catch (Exception e) {
            log.error("Unexpected error during itinerary generation", e);
            throw new ItineraryGenerationException("Failed to generate itinerary");
        }
    }
    
    private Itinerary parseAndValidateItinerary(String aiResponse, ItineraryRequest request) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(aiResponse);
            
            // Validate structure
            if (!root.has("itinerary")) {
                throw new JsonException("Missing 'itinerary' field");
            }
            
            Itinerary itinerary = new Itinerary();
            itinerary.setDestination(request.getDestination());
            itinerary.setNumberOfDays(request.getNumberOfDays());
            itinerary.setBudget(request.getBudget());
            itinerary.setInterests(request.getInterests());
            
            return itinerary;
        } catch (JsonProcessingException e) {
            throw new JsonException("Invalid JSON format", e);
        }
    }
    
    private ItineraryResponse convertToResponse(Itinerary itinerary) {
        return ItineraryResponse.builder()
            .id(itinerary.getId())
            .destination(itinerary.getDestination())
            .numberOfDays(itinerary.getNumberOfDays())
            .days(convertDaysToDTO(itinerary.getDays()))
            .totalEstimatedCost(itinerary.getTotalEstimatedCost())
            .createdAt(itinerary.getCreatedAt())
            .build();
    }
}
```

Best practices:
- Use `@Service` annotation
- Inject dependencies via `@Autowired`
- Use try-catch for proper error handling
- Log important operations (use SLF4J with `@Slf4j`)
- Keep methods focused (single responsibility)
- Use custom exceptions for specific errors

### Step 6: Create REST controller

Expose endpoints to the frontend:

```java
@RestController
@RequestMapping("/api/itineraries")
@Slf4j
public class ItineraryController {
    
    @Autowired
    private ItineraryGenerationService itineraryGenerationService;
    
    @Autowired
    private ItineraryRepository itineraryRepository;
    
    /**
     * Generate a new itinerary
     * POST /api/itineraries/generate
     */
    @PostMapping("/generate")
    public ResponseEntity<ItineraryResponse> generateItinerary(
            @Valid @RequestBody ItineraryRequest request) {
        
        log.info("Received itinerary generation request for destination: {}", 
                 request.getDestination());
        
        ItineraryResponse response = itineraryGenerationService.generateItinerary(request);
        
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(response);
    }
    
    /**
     * Get itinerary by ID
     * GET /api/itineraries/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ItineraryResponse> getItinerary(@PathVariable String id) {
        return itineraryRepository.findById(id)
            .map(itinerary -> ResponseEntity.ok(convertToResponse(itinerary)))
            .orElseThrow(() -> new ItineraryNotFoundException("Itinerary not found"));
    }
    
    /**
     * List all itineraries (with pagination)
     * GET /api/itineraries?page=0&size=10
     */
    @GetMapping
    public ResponseEntity<Page<ItineraryResponse>> listItineraries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        Page<Itinerary> itineraries = itineraryRepository.findAll(
            PageRequest.of(page, size, Sort.by("createdAt").descending())
        );
        
        Page<ItineraryResponse> responses = itineraries.map(this::convertToResponse);
        
        return ResponseEntity.ok(responses);
    }
    
    /**
     * Delete itinerary
     * DELETE /api/itineraries/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteItinerary(@PathVariable String id) {
        itineraryRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
    
    private ItineraryResponse convertToResponse(Itinerary itinerary) {
        // Convert entity to DTO
        return null; // Implementation
    }
}
```

Best practices:
- Use `@RestController` + `@RequestMapping`
- Use `@Valid` for request validation
- Use appropriate HTTP status codes (201 for create, 200 for get, 204 for delete)
- Include pagination for list endpoints
- Use meaningful exception handling
- Add `@Slf4j` for logging

### Step 7: Integrate external APIs

For third-party integrations (OpenWeather, OpenTripMap, etc.):

```java
@Component
@Slf4j
public class OpenWeatherApiClient {
    
    @Value("${api.openweather.key}")
    private String apiKey;
    
    @Value("${api.openweather.baseUrl}")
    private String baseUrl;
    
    @Autowired
    private RestTemplate restTemplate;
    
    public WeatherForecast getForecast(String city, LocalDate date) {
        try {
            String url = String.format(
                "%s/forecast?q=%s&appid=%s&units=metric",
                baseUrl, city, apiKey
            );
            
            log.info("Fetching weather forecast from: {}", url);
            
            ResponseEntity<WeatherResponse> response = restTemplate.getForEntity(
                url,
                WeatherResponse.class
            );
            
            return parseWeatherResponse(response.getBody(), date);
            
        } catch (RestClientException e) {
            log.error("Failed to fetch weather forecast", e);
            throw new ExternalApiException("Weather API call failed", e);
        }
    }
    
    private WeatherForecast parseWeatherResponse(WeatherResponse response, LocalDate date) {
        // Parse and validate API response
        if (response == null || response.getList() == null) {
            throw new ApiValidationException("Invalid weather response format");
        }
        
        return WeatherForecast.builder()
            .temperature(response.getList().get(0).getTemp())
            .condition(response.getList().get(0).getWeather().get(0).getMain())
            .date(date)
            .build();
    }
}
```

Best practices:
- Use `@Component` or `@Service` for API clients
- Inject API keys via `@Value` from `application.properties`
- Use `RestTemplate` or `WebClient` for HTTP calls
- Always wrap API responses in try-catch
- Validate API responses before parsing
- Log API calls for debugging
- Throw custom exceptions for API errors

### Step 8: Add exception handling

Create global exception handler:

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @ExceptionHandler(InvalidItineraryException.class)
    public ResponseEntity<ErrorResponse> handleInvalidItinerary(
            InvalidItineraryException e,
            HttpServletRequest request) {
        
        log.error("Invalid itinerary: {}", e.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
            .status(HttpStatus.BAD_REQUEST.value())
            .message(e.getMessage())
            .path(request.getRequestURI())
            .timestamp(LocalDateTime.now())
            .build();
        
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(error);
    }
    
    @ExceptionHandler(ItineraryNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            ItineraryNotFoundException e,
            HttpServletRequest request) {
        
        ErrorResponse error = ErrorResponse.builder()
            .status(HttpStatus.NOT_FOUND.value())
            .message(e.getMessage())
            .path(request.getRequestURI())
            .timestamp(LocalDateTime.now())
            .build();
        
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(error);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception e,
            HttpServletRequest request) {
        
        log.error("Unexpected error", e);
        
        ErrorResponse error = ErrorResponse.builder()
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .message("An unexpected error occurred")
            .path(request.getRequestURI())
            .timestamp(LocalDateTime.now())
            .build();
        
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(error);
    }
}
```

### Step 9: Write unit tests

Test services and controllers:

```java
@SpringBootTest
public class ItineraryGenerationServiceTest {
    
    @Autowired
    private ItineraryGenerationService service;
    
    @MockBean
    private OpenAiClient openAiClient;
    
    @MockBean
    private ItineraryRepository repository;
    
    @Test
    void testGenerateItinerary_Success() {
        // Arrange
        ItineraryRequest request = new ItineraryRequest(
            "Madurai", 3, BudgetLevel.LOW, 
            List.of("temples", "food")
        );
        
        String aiResponse = "{\"itinerary\": [...]}";
        given(openAiClient.generateItinerary(request))
            .willReturn(aiResponse);
        
        // Act
        ItineraryResponse response = service.generateItinerary(request);
        
        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getDestination()).isEqualTo("Madurai");
        verify(repository).save(any(Itinerary.class));
    }
    
    @Test
    void testGenerateItinerary_InvalidJson() {
        // Arrange
        ItineraryRequest request = new ItineraryRequest(
            "Madurai", 3, BudgetLevel.LOW, 
            List.of("temples")
        );
        
        given(openAiClient.generateItinerary(request))
            .willReturn("invalid json");
        
        // Act & Assert
        assertThatThrownBy(() -> service.generateItinerary(request))
            .isInstanceOf(InvalidItineraryException.class);
    }
}
```

## Decision tree: Choosing database approach

```
If [data is highly relational] (users, itineraries, places, reviews):
  → Use PostgreSQL with Spring Data JPA
  
If [data is schema-flexible] (rich place data, varied attributes):
  → Use MongoDB with Spring Data Mongo
  
If [simple key-value storage]:
  → Use Redis for caching/sessions
```

## Decision tree: API integration strategy

```
If [read-only, non-time-critical]:
  → Cache results (24-hour TTL)
  → Use @Cacheable annotation
  
If [real-time data required]:
  → Fetch on-demand
  → Add rate limiting
  
If [high-frequency calls]:
  → Batch requests
  → Implement circuit breaker pattern
```

## Best practices

- ✅ **Validate early:** Always validate requests at controller level and database constraints
- ✅ **Separate concerns:** Controller → Service → Repository layers
- ✅ **Handle errors gracefully:** Use custom exceptions and global exception handler
- ✅ **Log operations:** Use SLF4J to log important steps
- ✅ **Use DTOs:** Keep entities internal, expose via DTOs
- ✅ **Async operations:** Use `@Async` for long-running tasks (AI generation)
- ✅ **Cache wisely:** Cache geospatial data, weather, static content
- ✅ **Test thoroughly:** Unit tests for services, integration tests for controllers
- ✅ **Document APIs:** Add Swagger/OpenAPI annotations
- ✅ **Use transactions:** `@Transactional` for multi-step operations

## Common pitfalls

- ❌ **Exposing entities directly:** Always use DTOs for API responses
- ❌ **No error handling:** Catch and handle exceptions properly
- ❌ **Synchronous API calls:** Long-running tasks should be async
- ❌ **N+1 query problems:** Use `@EntityGraph` or projections
- ❌ **No validation:** Always validate input, don't trust frontend
- ❌ **Hardcoded values:** Use `application.properties` for configuration
- ✅ **Do this instead:** Use DTOs, handle errors, async where needed, eager load data

## Configuration (application.properties)

```properties
# Server
server.port=8080
server.servlet.context-path=/

# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/newplanner
spring.datasource.username=postgres
spring.datasource.password=password
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.hibernate.ddl-auto=validate

# API Keys (use environment variables in production)
api.openai.key=${OPENAI_API_KEY}
api.openai.model=gpt-4o
api.openweather.key=${OPENWEATHER_API_KEY}
api.openweather.baseUrl=https://api.openweathermap.org/data/2.5
api.openroute.key=${OPENROUTE_API_KEY}

# Logging
logging.level.root=INFO
logging.level.com.newplanner=DEBUG
spring.jpa.show-sql=false

# Actuator (for monitoring)
management.endpoints.web.exposure.include=health,metrics
```

## Maven dependencies (pom.xml) - Key ones

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>

<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>

<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
</dependency>
```

## Integration points

**Related API clients:**
- OpenAI client (itinerary generation)
- OpenWeather client (weather data)
- OpenTripMap client (place data)
- OpenRouteService client (route optimization)

**Related services:**
- ItineraryGenerationService
- PlaceEnrichmentService
- RouteOptimizationService
- ExpenseService

**Related repositories:**
- ItineraryRepository
- PlaceRepository
- UserRepository
- ReviewRepository

**Related controllers:**
- ItineraryController
- PlaceController
- ExpenseController
- UserController

## Troubleshooting

**Problem 1: JSON parsing fails**
- Check: Is API response valid JSON?
- Solution: Always wrap parsing in try-catch
- Solution: Log both request and response for debugging

**Problem 2: N+1 query problem (slow database queries)**
- Check: Are you fetching related entities one-by-one?
- Solution: Use `@EntityGraph` to eager load relationships
- Solution: Use native SQL or JPQL projections

**Problem 3: External API timeout**
- Check: Is the API endpoint reachable?
- Solution: Add timeout configuration to RestTemplate
- Solution: Implement circuit breaker pattern (Resilience4j)

**Problem 4: Validation not working**
- Check: Is `@Valid` annotation present on controller method?
- Solution: Add `spring-boot-starter-validation` dependency
- Solution: Ensure entity has `@NotNull`, `@NotBlank` annotations

## See also

- Spring Boot Documentation: https://spring.io/projects/spring-boot
- Spring Data JPA: https://spring.io/projects/spring-data-jpa
- Baeldung Spring Boot Tutorials: https://www.baeldung.com/spring-boot
- NewPlanner Frontend APIs: `../../frontend/`
- External API Documentation: Check `.agents/skills/api-integration/` skill
