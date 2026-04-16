# Backend Development - Code Examples

Example implementations for common NewPlanner backend patterns.

## Example 1: Complete Itinerary Feature Flow

### Step 1: DTOs (Request/Response)

```java
// ItineraryRequest.java
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
}

// ItineraryResponse.java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItineraryResponse {
    private String id;
    private String destination;
    private Integer numberOfDays;
    private BudgetLevel budget;
    private List<String> interests;
    private List<ItineraryDayDTO> days;
    private Double totalEstimatedCost;
    private LocalDateTime createdAt;
}

// ItineraryDayDTO.java
@Data
@Builder
public class ItineraryDayDTO {
    private Integer dayNumber;
    private String theme;
    private List<ActivityDTO> activities;
    private WeatherDTO weather;
    private Double dayCost;
}

// ActivityDTO.java
@Data
@Builder
public class ActivityDTO {
    private String id;
    private String placeName;
    private String category;
    private Double latitude;
    private Double longitude;
    private String time;
    private Integer durationMinutes;
    private Double estimatedCost;
    private String notes;
}
```

### Step 2: Entity Classes

```java
// Itinerary.java
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
    
    @OneToMany(mappedBy = "itinerary", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
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

// ItineraryDay.java
@Entity
@Table(name = "itinerary_days")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItineraryDay {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @ManyToOne
    @JoinColumn(name = "itinerary_id", nullable = false)
    private Itinerary itinerary;
    
    @Column(nullable = false)
    private Integer dayNumber;
    
    private String theme;
    
    @OneToMany(mappedBy = "day", cascade = CascadeType.ALL)
    private List<Activity> activities;
    
    @Embedded
    private Weather weather;
    
    @Column(name = "day_cost")
    private Double dayCost;
}

// Activity.java
@Entity
@Table(name = "activities")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Activity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @ManyToOne
    @JoinColumn(name = "day_id", nullable = false)
    private ItineraryDay day;
    
    @Column(nullable = false)
    private String placeName;
    
    private String category;
    
    private Double latitude;
    
    private Double longitude;
    
    @Column(nullable = false)
    private String time;
    
    @Column(name = "duration_minutes")
    private Integer durationMinutes;
    
    @Column(name = "estimated_cost")
    private Double estimatedCost;
    
    private String notes;
}

// Weather.java (Embeddable)
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Weather {
    private Double temperature;
    private String condition; // sunny, rainy, cloudy, etc.
    private Integer humidity;
    private Double windSpeed;
}
```

### Step 3: Repository

```java
@Repository
public interface ItineraryRepository extends JpaRepository<Itinerary, String> {
    List<Itinerary> findByDestination(String destination);
    List<Itinerary> findByBudget(BudgetLevel budget);
    List<Itinerary> findByCreatedAtAfter(LocalDateTime date);
    
    @Query("SELECT i FROM Itinerary i WHERE i.destination = :destination " +
           "ORDER BY i.createdAt DESC LIMIT 10")
    List<Itinerary> findRecentByDestination(@Param("destination") String destination);
}

@Repository
public interface ItineraryDayRepository extends JpaRepository<ItineraryDay, String> {
    List<ItineraryDay> findByItinerary(Itinerary itinerary);
}

@Repository
public interface ActivityRepository extends JpaRepository<Activity, String> {
    List<Activity> findByDay(ItineraryDay day);
}
```

### Step 4: Service

```java
@Service
@Slf4j
public class ItineraryGenerationService {
    
    @Autowired
    private ItineraryRepository itineraryRepository;
    
    @Autowired
    private ItineraryDayRepository dayRepository;
    
    @Autowired
    private ActivityRepository activityRepository;
    
    @Autowired
    private OpenAiClient openAiClient;
    
    @Autowired
    private PlaceEnrichmentService placeEnrichmentService;
    
    @Autowired
    private RouteOptimizationService routeOptimizationService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Transactional
    public ItineraryResponse generateItinerary(ItineraryRequest request) {
        try {
            log.info("Starting itinerary generation for: {}", request.getDestination());
            
            // Step 1: AI generation
            String aiResponse = openAiClient.generateItinerary(request);
            
            // Step 2: Parse and validate
            Itinerary itinerary = parseAndValidateItinerary(aiResponse, request);
            
            // Step 3: Enrich with geospatial data
            itinerary = placeEnrichmentService.enrichItinerary(itinerary);
            
            // Step 4: Optimize routes
            itinerary = routeOptimizationService.optimizeRoutes(itinerary);
            
            // Step 5: Save all data
            itinerary = itineraryRepository.save(itinerary);
            
            log.info("Itinerary generated successfully with ID: {}", itinerary.getId());
            
            return convertToResponse(itinerary);
            
        } catch (Exception e) {
            log.error("Failed to generate itinerary: {}", e.getMessage(), e);
            throw new ItineraryGenerationException("Failed to generate itinerary", e);
        }
    }
    
    private Itinerary parseAndValidateItinerary(String aiResponse, ItineraryRequest request) 
            throws JsonProcessingException {
        
        JsonNode root = objectMapper.readTree(aiResponse);
        
        if (!root.has("itinerary")) {
            throw new InvalidItineraryException("Missing 'itinerary' field in AI response");
        }
        
        Itinerary itinerary = new Itinerary();
        itinerary.setDestination(request.getDestination());
        itinerary.setNumberOfDays(request.getNumberOfDays());
        itinerary.setBudget(request.getBudget());
        itinerary.setInterests(request.getInterests());
        itinerary.setDays(new ArrayList<>());
        
        // Parse days
        JsonNode daysArray = root.get("itinerary");
        int dayNumber = 1;
        double totalCost = 0;
        
        for (JsonNode dayNode : daysArray) {
            ItineraryDay day = parseDayNode(dayNode, itinerary, dayNumber);
            itinerary.getDays().add(day);
            
            if (day.getDayCost() != null) {
                totalCost += day.getDayCost();
            }
            dayNumber++;
        }
        
        itinerary.setTotalEstimatedCost(totalCost);
        
        return itinerary;
    }
    
    private ItineraryDay parseDayNode(JsonNode dayNode, Itinerary itinerary, int dayNumber) {
        ItineraryDay day = new ItineraryDay();
        day.setItinerary(itinerary);
        day.setDayNumber(dayNumber);
        day.setTheme(dayNode.get("theme").asText(""));
        day.setActivities(new ArrayList<>());
        
        JsonNode activitiesArray = dayNode.get("schedule");
        double dayCost = 0;
        
        for (JsonNode actNode : activitiesArray) {
            Activity activity = parseActivityNode(actNode);
            activity.setDay(day);
            day.getActivities().add(activity);
            
            if (activity.getEstimatedCost() != null) {
                dayCost += activity.getEstimatedCost();
            }
        }
        
        day.setDayCost(dayCost);
        return day;
    }
    
    private Activity parseActivityNode(JsonNode actNode) {
        Activity activity = new Activity();
        activity.setPlaceName(actNode.get("place").asText(""));
        activity.setCategory(actNode.get("category").asText(""));
        activity.setTime(actNode.get("time").asText(""));
        activity.setDurationMinutes(actNode.get("duration").asInt(60));
        activity.setEstimatedCost(actNode.get("cost").asDouble(0));
        activity.setNotes(actNode.get("notes").asText(""));
        
        return activity;
    }
    
    private ItineraryResponse convertToResponse(Itinerary itinerary) {
        return ItineraryResponse.builder()
            .id(itinerary.getId())
            .destination(itinerary.getDestination())
            .numberOfDays(itinerary.getNumberOfDays())
            .budget(itinerary.getBudget())
            .interests(itinerary.getInterests())
            .days(itinerary.getDays().stream()
                .map(this::convertDayToDTO)
                .collect(Collectors.toList()))
            .totalEstimatedCost(itinerary.getTotalEstimatedCost())
            .createdAt(itinerary.getCreatedAt())
            .build();
    }
    
    private ItineraryDayDTO convertDayToDTO(ItineraryDay day) {
        return ItineraryDayDTO.builder()
            .dayNumber(day.getDayNumber())
            .theme(day.getTheme())
            .activities(day.getActivities().stream()
                .map(this::convertActivityToDTO)
                .collect(Collectors.toList()))
            .dayCost(day.getDayCost())
            .build();
    }
    
    private ActivityDTO convertActivityToDTO(Activity activity) {
        return ActivityDTO.builder()
            .id(activity.getId())
            .placeName(activity.getPlaceName())
            .category(activity.getCategory())
            .latitude(activity.getLatitude())
            .longitude(activity.getLongitude())
            .time(activity.getTime())
            .durationMinutes(activity.getDurationMinutes())
            .estimatedCost(activity.getEstimatedCost())
            .notes(activity.getNotes())
            .build();
    }
}
```

### Step 5: Controller

```java
@RestController
@RequestMapping("/api/v1/itineraries")
@Slf4j
public class ItineraryController {
    
    @Autowired
    private ItineraryGenerationService generationService;
    
    @Autowired
    private ItineraryRepository repository;
    
    /**
     * Generate a new itinerary
     * POST /api/v1/itineraries/generate
     */
    @PostMapping("/generate")
    public ResponseEntity<ItineraryResponse> generateItinerary(
            @Valid @RequestBody ItineraryRequest request) {
        
        log.info("Received generation request for destination: {}", request.getDestination());
        ItineraryResponse response = generationService.generateItinerary(request);
        
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(response);
    }
    
    /**
     * Get itinerary by ID
     * GET /api/v1/itineraries/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ItineraryResponse> getItinerary(@PathVariable String id) {
        return repository.findById(id)
            .map(itinerary -> {
                log.info("Retrieved itinerary: {}", id);
                return ResponseEntity.ok(convertToResponse(itinerary));
            })
            .orElseThrow(() -> new ItineraryNotFoundException("Itinerary not found: " + id));
    }
    
    /**
     * List itineraries with pagination
     * GET /api/v1/itineraries?page=0&size=10&sort=createdAt,desc
     */
    @GetMapping
    public ResponseEntity<Page<ItineraryResponse>> listItineraries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sort) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(sort).descending());
        Page<Itinerary> itineraries = repository.findAll(pageable);
        
        return ResponseEntity.ok(itineraries.map(this::convertToResponse));
    }
    
    /**
     * Delete itinerary
     * DELETE /api/v1/itineraries/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteItinerary(@PathVariable String id) {
        repository.deleteById(id);
        log.info("Deleted itinerary: {}", id);
        return ResponseEntity.noContent().build();
    }
}
```

### Step 6: Exception Handling

```java
// Custom Exception Classes
public class ItineraryGenerationException extends RuntimeException {
    public ItineraryGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}

public class InvalidItineraryException extends RuntimeException {
    public InvalidItineraryException(String message) {
        super(message);
    }
}

public class ItineraryNotFoundException extends RuntimeException {
    public ItineraryNotFoundException(String message) {
        super(message);
    }
}

// Global Exception Handler
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @ExceptionHandler(ItineraryNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            ItineraryNotFoundException e,
            HttpServletRequest request) {
        
        log.warn("Itinerary not found: {}", e.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
            .status(HttpStatus.NOT_FOUND.value())
            .message(e.getMessage())
            .path(request.getRequestURI())
            .timestamp(LocalDateTime.now())
            .build();
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
    
    @ExceptionHandler(InvalidItineraryException.class)
    public ResponseEntity<ErrorResponse> handleInvalidItinerary(
            InvalidItineraryException e,
            HttpServletRequest request) {
        
        log.error("Invalid itinerary data: {}", e.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
            .status(HttpStatus.BAD_REQUEST.value())
            .message(e.getMessage())
            .path(request.getRequestURI())
            .timestamp(LocalDateTime.now())
            .build();
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception e,
            HttpServletRequest request) {
        
        log.error("Unexpected error: {}", e.getMessage(), e);
        
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

### Step 7: Unit Tests

```java
@SpringBootTest
public class ItineraryGenerationServiceTest {
    
    @Autowired
    private ItineraryGenerationService service;
    
    @MockBean
    private OpenAiClient openAiClient;
    
    @MockBean
    private ItineraryRepository repository;
    
    @MockBean
    private PlaceEnrichmentService enrichmentService;
    
    @MockBean
    private RouteOptimizationService optimizationService;
    
    @Test
    void testGenerateItinerary_Success() {
        // Arrange
        ItineraryRequest request = ItineraryRequest.builder()
            .destination("Madurai")
            .numberOfDays(3)
            .budget(BudgetLevel.LOW)
            .interests(List.of("temples", "food"))
            .build();
        
        String aiResponse = "{\"itinerary\": [{\"day\": 1, \"theme\": \"Temples\", \"schedule\": []}]}";
        
        Itinerary mockItinerary = new Itinerary();
        mockItinerary.setId("itinerary-123");
        mockItinerary.setDestination("Madurai");
        
        given(openAiClient.generateItinerary(request)).willReturn(aiResponse);
        given(enrichmentService.enrichItinerary(any())).willReturn(mockItinerary);
        given(optimizationService.optimizeRoutes(any())).willReturn(mockItinerary);
        given(repository.save(any())).willReturn(mockItinerary);
        
        // Act
        ItineraryResponse response = service.generateItinerary(request);
        
        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo("itinerary-123");
        verify(repository).save(any(Itinerary.class));
    }
}

@WebMvcTest(ItineraryController.class)
public class ItineraryControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private ItineraryGenerationService service;
    
    @Test
    void testGenerateItinerary_ReturnsCreated() throws Exception {
        // Arrange
        ItineraryRequest request = ItineraryRequest.builder()
            .destination("Madurai")
            .numberOfDays(3)
            .budget(BudgetLevel.LOW)
            .interests(List.of("temples"))
            .build();
        
        ItineraryResponse response = ItineraryResponse.builder()
            .id("itinerary-123")
            .destination("Madurai")
            .build();
        
        given(service.generateItinerary(any())).willReturn(response);
        
        // Act & Assert
        mockMvc.perform(post("/api/v1/itineraries/generate")
            .contentType(MediaType.APPLICATION_JSON)
            .content(new ObjectMapper().writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value("itinerary-123"));
    }
}
```

## Example 2: External API Integration

```java
// OpenWeather Client
@Component
@Slf4j
public class OpenWeatherApiClient {
    
    @Value("${api.openweather.key}")
    private String apiKey;
    
    @Value("${api.openweather.baseUrl}")
    private String baseUrl;
    
    @Autowired
    private RestTemplate restTemplate;
    
    @Cacheable("weather")
    public WeatherForecast getForecast(String city, LocalDate date) {
        try {
            String url = String.format("%s/forecast?q=%s&appid=%s&units=metric",
                baseUrl, city, apiKey);
            
            log.info("Fetching weather for: {} on: {}", city, date);
            
            ResponseEntity<WeatherResponse> response = restTemplate.getForEntity(
                url,
                WeatherResponse.class
            );
            
            return parseWeatherResponse(response.getBody(), date);
            
        } catch (RestClientException e) {
            log.error("Weather API error: {}", e.getMessage());
            // Return default weather or throw exception
            return WeatherForecast.getDefault();
        }
    }
    
    private WeatherForecast parseWeatherResponse(WeatherResponse response, LocalDate date) {
        if (response == null || response.getList() == null || response.getList().isEmpty()) {
            log.warn("Empty weather response");
            return WeatherForecast.getDefault();
        }
        
        WeatherData data = response.getList().get(0);
        
        return WeatherForecast.builder()
            .temperature(data.getMain().getTemp())
            .condition(data.getWeather().get(0).getMain())
            .humidity(data.getMain().getHumidity())
            .windSpeed(data.getWind().getSpeed())
            .date(date)
            .build();
    }
}
```

This covers the main patterns for backend development in NewPlanner!
