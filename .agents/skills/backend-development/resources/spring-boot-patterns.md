# Common Spring Boot Patterns for NewPlanner

Quick reference for frequently used patterns in NewPlanner backend.

## REST Controller Template

```java
@RestController
@RequestMapping("/api/v1/resource-name")
@Slf4j
@Validated
public class ResourceController {
    
    @Autowired
    private ResourceService service;
    
    @PostMapping
    public ResponseEntity<ResourceResponse> create(@Valid @RequestBody ResourceRequest request) {
        log.info("Creating resource");
        ResourceResponse response = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<ResourceResponse> getById(@PathVariable String id) {
        ResourceResponse response = service.getById(id);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping
    public ResponseEntity<Page<ResourceResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<ResourceResponse> responses = service.list(page, size);
        return ResponseEntity.ok(responses);
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<ResourceResponse> update(
            @PathVariable String id,
            @Valid @RequestBody ResourceRequest request) {
        ResourceResponse response = service.update(id, request);
        return ResponseEntity.ok(response);
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

## Service Template

```java
@Service
@Slf4j
@Transactional
public class ResourceService {
    
    @Autowired
    private ResourceRepository repository;
    
    public ResourceResponse create(ResourceRequest request) {
        log.info("Creating new resource");
        
        Resource resource = new Resource();
        resource.setName(request.getName());
        // ... set other fields
        
        Resource saved = repository.save(resource);
        log.info("Resource created with ID: {}", saved.getId());
        
        return convertToResponse(saved);
    }
    
    public ResourceResponse getById(String id) {
        Resource resource = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Not found: " + id));
        return convertToResponse(resource);
    }
    
    public Page<ResourceResponse> list(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return repository.findAll(pageable).map(this::convertToResponse);
    }
    
    public ResourceResponse update(String id, ResourceRequest request) {
        Resource resource = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Not found: " + id));
        
        resource.setName(request.getName());
        // ... update other fields
        
        Resource updated = repository.save(resource);
        log.info("Resource updated: {}", id);
        
        return convertToResponse(updated);
    }
    
    public void delete(String id) {
        repository.deleteById(id);
        log.info("Resource deleted: {}", id);
    }
    
    private ResourceResponse convertToResponse(Resource resource) {
        return ResourceResponse.builder()
            .id(resource.getId())
            .name(resource.getName())
            // ... map other fields
            .build();
    }
}
```

## Entity Template

```java
@Entity
@Table(name = "resources")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Resource {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(length = 1000)
    private String description;
    
    @Enumerated(EnumType.STRING)
    private ResourceStatus status;
    
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

## Validation Examples

```java
// Request DTO with validation
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRequest {
    
    @NotBlank(message = "Name is required")
    @Size(min = 3, max = 100, message = "Name must be 3-100 characters")
    private String name;
    
    @Email(message = "Email must be valid")
    private String email;
    
    @Min(value = 18, message = "Age must be at least 18")
    @Max(value = 120, message = "Age must be realistic")
    private Integer age;
    
    @NotEmpty(message = "At least one interest is required")
    private List<String> interests;
    
    @Pattern(regexp = "^[0-9]{10}$", message = "Phone must be 10 digits")
    private String phone;
}

// Custom validation
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DestinationValidator.class)
public @interface ValidDestination {
    String message() default "Invalid destination";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

public class DestinationValidator implements ConstraintValidator<ValidDestination, String> {
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true;
        // Check if destination exists in database or list
        return value.length() > 2;
    }
}

// Use custom validation
public class ItineraryRequest {
    @ValidDestination
    private String destination;
}
```

## Error Handling

```java
// Custom Exception
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}

// Global Exception Handler
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            ResourceNotFoundException e,
            HttpServletRequest request) {
        
        ErrorResponse error = ErrorResponse.builder()
            .status(HttpStatus.NOT_FOUND.value())
            .message(e.getMessage())
            .path(request.getRequestURI())
            .timestamp(LocalDateTime.now())
            .build();
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationError(
            MethodArgumentNotValidException e,
            HttpServletRequest request) {
        
        String message = e.getBindingResult().getFieldErrors().stream()
            .map(err -> err.getField() + ": " + err.getDefaultMessage())
            .collect(Collectors.joining("; "));
        
        ErrorResponse error = ErrorResponse.builder()
            .status(HttpStatus.BAD_REQUEST.value())
            .message(message)
            .path(request.getRequestURI())
            .timestamp(LocalDateTime.now())
            .build();
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
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
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}

// Error Response DTO
@Data
@Builder
public class ErrorResponse {
    private int status;
    private String message;
    private String path;
    private LocalDateTime timestamp;
}
```

## Caching

```java
// Enable caching
@Configuration
@EnableCaching
public class CacheConfig {
    
    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("weather", "places", "routes");
    }
}

// Use caching
@Service
public class WeatherService {
    
    @Cacheable(value = "weather", key = "#city + '-' + #date")
    public WeatherForecast getForecast(String city, LocalDate date) {
        // This will be called only once per city-date combination
        return fetchFromApi(city, date);
    }
    
    @CacheEvict(value = "weather", allEntries = true)
    public void clearCache() {
        log.info("Weather cache cleared");
    }
}
```

## Database Query Examples

```java
@Repository
public interface ItineraryRepository extends JpaRepository<Itinerary, String> {
    
    // By single field
    List<Itinerary> findByDestination(String destination);
    
    // By multiple fields with AND
    List<Itinerary> findByDestinationAndBudget(String destination, BudgetLevel budget);
    
    // By date range
    List<Itinerary> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    
    // Complex query
    @Query("SELECT i FROM Itinerary i WHERE i.destination = :destination " +
           "AND i.budget = :budget " +
           "AND i.createdAt > :date " +
           "ORDER BY i.createdAt DESC")
    List<Itinerary> findRecentByDestinationAndBudget(
        @Param("destination") String destination,
        @Param("budget") BudgetLevel budget,
        @Param("date") LocalDateTime date);
    
    // With pagination
    Page<Itinerary> findByDestination(String destination, Pageable pageable);
    
    // Native SQL
    @Query(value = "SELECT * FROM itineraries WHERE total_estimated_cost > :minCost", 
           nativeQuery = true)
    List<Itinerary> findExpensiveItineraries(@Param("minCost") Double minCost);
    
    // Count
    int countByBudget(BudgetLevel budget);
    
    // Delete
    @Modifying
    @Transactional
    void deleteByCreatedAtBefore(LocalDateTime date);
}
```

## Transaction Management

```java
// Class-level transactional
@Service
@Transactional
public class OrderService {
    
    // Inherited transactional behavior
    public void processOrder(String orderId) {
        // All DB operations auto-committed or rolled back
    }
    
    // Read-only operation (more efficient)
    @Transactional(readOnly = true)
    public OrderDTO getOrder(String orderId) {
        return repository.findById(orderId)
            .map(this::convertToDTO)
            .orElseThrow();
    }
    
    // No transaction (for read operations with no modifications)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<OrderDTO> listOrders() {
        return repository.findAll().stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }
    
    // Requires new transaction
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void auditOrder(String orderId) {
        // Runs in new transaction, commits independently
    }
}
```

## Async Operations

```java
// Enable async
@Configuration
@EnableAsync
public class AsyncConfig {
    
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("NewPlanner-");
        executor.initialize();
        return executor;
    }
}

// Use async for long-running operations
@Service
public class ItineraryGenerationService {
    
    @Async("taskExecutor")
    public CompletableFuture<ItineraryResponse> generateItineraryAsync(
            ItineraryRequest request) {
        
        try {
            log.info("Starting async itinerary generation");
            ItineraryResponse response = generateItinerary(request);
            return CompletableFuture.completedFuture(response);
        } catch (Exception e) {
            log.error("Async generation failed", e);
            return CompletableFuture.failedFuture(e);
        }
    }
}

// Use in controller
@PostMapping("/generate-async")
public ResponseEntity<CompletableFuture<ItineraryResponse>> generateAsync(
        @Valid @RequestBody ItineraryRequest request) {
    
    CompletableFuture<ItineraryResponse> future = 
        service.generateItineraryAsync(request);
    
    return ResponseEntity.accepted().body(future);
}
```

## Testing Utilities

```java
// Unit test base class
@SpringBootTest
public abstract class BaseServiceTest {
    
    @Autowired
    protected ObjectMapper objectMapper;
    
    protected String toJson(Object object) throws JsonProcessingException {
        return objectMapper.writeValueAsString(object);
    }
    
    protected <T> T fromJson(String json, Class<T> clazz) throws JsonProcessingException {
        return objectMapper.readValue(json, clazz);
    }
}

// Controller test
@WebMvcTest(ItineraryController.class)
public class ItineraryControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void testSuccessfulRequest() throws Exception {
        mockMvc.perform(get("/api/v1/itineraries/123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("123"));
    }
    
    @Test
    void testNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/itineraries/invalid"))
            .andExpect(status().isNotFound());
    }
}
```

## Configuration Properties

```properties
# application.properties
server.port=8080
server.servlet.context-path=/

# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/newplanner
spring.datasource.username=${DB_USERNAME:postgres}
spring.datasource.password=${DB_PASSWORD:password}
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false

# Logging
logging.level.root=INFO
logging.level.com.newplanner=DEBUG
logging.pattern.console=%d{yyyy-MM-dd HH:mm:ss} - %msg%n

# API Configuration
api.openai.key=${OPENAI_API_KEY}
api.openai.model=gpt-4o
api.openweather.key=${OPENWEATHER_API_KEY}
api.openroute.key=${OPENROUTE_API_KEY}

# Caching
spring.cache.type=simple

# Async
spring.task.execution.pool.core-size=2
spring.task.execution.pool.max-size=5
spring.task.execution.pool.queue-capacity=100

# Actuator
management.endpoints.web.exposure.include=health,metrics
management.endpoint.health.show-details=always
```

These patterns form the backbone of NewPlanner's backend architecture!
