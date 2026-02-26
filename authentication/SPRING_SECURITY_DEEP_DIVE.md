# Spring Security Deep Dive: JWT Authentication from Ground Up

> **A Comprehensive Guide to Understanding Spring Security 6 with JWT**
>
> This tutorial covers every step, bean, quirk, and edge case involved in JWT-based authentication using your implementation as a practical example.

---

## Table of Contents

1. [Spring Security Architecture Overview](#1-spring-security-architecture-overview)
2. [Application Startup: Bean Initialization](#2-application-startup-bean-initialization)
3. [The Filter Chain: Heart of Spring Security](#3-the-filter-chain-heart-of-spring-security)
4. [Registration Flow: Step-by-Step](#4-registration-flow-step-by-step)
5. [Login Flow: Authentication in Action](#5-login-flow-authentication-in-action)
6. [JWT Generation: Creating Tokens](#6-jwt-generation-creating-tokens)
7. [JWT Consumption: Validating Requests](#7-jwt-consumption-validating-requests)
8. [Token Refresh: Extending Sessions](#8-token-refresh-extending-sessions)
9. [Token Revocation: Advanced Security](#9-token-revocation-advanced-security)
10. [Security Context: Thread-Local Magic](#10-security-context-thread-local-magic)
11. [Quirks, Gotchas, and Edge Cases](#11-quirks-gotchas-and-edge-cases)
12. [Performance Considerations](#12-performance-considerations)

---

## 1. Spring Security Architecture Overview

### 1.1 The Big Picture

Spring Security is built on a **filter-based architecture**. Every HTTP request passes through a chain of filters before reaching your controller. Think of it as airport security checkpoints - each filter checks something specific.

```
Client Request
    ↓
[Filter Chain]
    ├── CorsFilter
    ├── CsrfFilter (disabled in your case)
    ├── JwtAuthenticationFilter ← Your custom filter
    ├── UsernamePasswordAuthenticationFilter
    ├── ExceptionTranslationFilter
    └── FilterSecurityInterceptor
    ↓
Your Controller (if authorized)
```

### 1.2 Core Concepts

**Filter**: Intercepts requests and performs security checks
**Authentication**: Represents user credentials (email + password)
**Principal**: The currently authenticated user
**Authorities/Roles**: Permissions the user has
**SecurityContext**: Thread-local storage for authentication information

---

## 2. Application Startup: Bean Initialization

When your Spring Boot application starts, Spring Security beans are initialized in a specific order. Let's trace this process:

### 2.1 Configuration Class Loading

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    // ...
}
```

**What happens here:**

1. `@Configuration` tells Spring this class contains bean definitions
2. `@EnableWebSecurity` activates Spring Security (imports `WebSecurityConfiguration`)
3. `@EnableMethodSecurity` enables method-level security annotations like `@PreAuthorize`

### 2.2 Bean Creation Order (Simplified)

```
Application Startup
    ↓
1. UserRepository (JPA repository)
    ↓
2. CustomUserDetailsService (uses UserRepository)
    ↓
3. PasswordEncoder (BCryptPasswordEncoder)
    ↓
4. JwtService (@Service, has @Value injections)
    ↓
5. JwtAuthenticationFilter (uses JwtService + UserDetailsService)
    ↓
6. AuthenticationProvider (uses UserDetailsService + PasswordEncoder)
    ↓
7. AuthenticationManager (uses AuthenticationProvider)
    ↓
8. SecurityFilterChain (registers all filters)
    ↓
9. AuthenticationService (uses all above beans)
    ↓
Application Ready!
```

### 2.3 Essential Beans Explained

#### Bean 1: `PasswordEncoder`

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

**Purpose**: Hashes passwords using BCrypt (a slow, adaptive hashing algorithm)

**Why Required**: 
- Never store plain-text passwords
- BCrypt has built-in salt (random data added to passwords)
- Adaptive cost factor (can increase difficulty over time)

**Quirk**: BCrypt has a 72-byte limit. Passwords longer than 72 bytes are truncated!

#### Bean 2: `UserDetailsService`

```java
@Service
public class CustomUserDetailsService implements UserDetailsService {
    @Override
    public UserDetails loadUserByUsername(String email) {
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}
```

**Purpose**: Bridges Spring Security with your User entity

**Why Required**: Spring Security doesn't know about your `User` entity. This service teaches it how to load user data.

**Key Points**:
- Your `User` entity implements `UserDetails` interface
- Spring Security calls this method during authentication
- Called again during JWT validation to get fresh user data

**Quirk**: The method is named `loadUserByUsername` but you're passing email. The "username" is just Spring Security's terminology for the principal identifier.

#### Bean 3: `AuthenticationProvider`

```java
@Bean
public AuthenticationProvider authenticationProvider() {
    DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
    authProvider.setPasswordEncoder(passwordEncoder());
    return authProvider;
}
```

**Purpose**: Handles the actual authentication logic

**Why Required**: This is the brain that:
1. Loads user via `UserDetailsService`
2. Compares passwords using `PasswordEncoder`
3. Throws exceptions if authentication fails

**Type**: `DaoAuthenticationProvider` = Database Authentication Provider

**Flow**:
```
authenticate(username, password)
    ↓
loadUserByUsername(username) → Get UserDetails
    ↓
passwordEncoder.matches(rawPassword, encodedPassword)
    ↓
If matches → Return Authentication object
If not → Throw BadCredentialsException
```

#### Bean 4: `AuthenticationManager`

```java
@Bean
public AuthenticationManager authenticationManager(AuthenticationConfiguration config) {
    return config.getAuthenticationManager();
}
```

**Purpose**: Facade that delegates to `AuthenticationProvider`

**Why Required**: Your `AuthenticationService` needs this to authenticate users during login

**Architecture**:
```
AuthenticationManager (interface)
    ↓
ProviderManager (implementation)
    ↓
List<AuthenticationProvider>
    ├── DaoAuthenticationProvider (your bean)
    ├── LdapAuthenticationProvider (if configured)
    └── Other providers...
```

**Quirk**: Even though you have only one provider, Spring Security uses a list internally. It tries each provider until one succeeds or all fail.

#### Bean 5: `SecurityFilterChain`

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .csrf(AbstractHttpConfigurer::disable)
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .authorizeHttpRequests(authorize -> authorize
            .requestMatchers("/api/auth/**").permitAll()
            .requestMatchers("/api/admin/**").hasRole("ADMIN")
            .anyRequest().authenticated()
        )
        .sessionManagement(session -> session
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        )
        .authenticationProvider(authenticationProvider())
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
}
```

**Purpose**: Configures the entire security filter chain

**Why Required**: This is where you tell Spring Security:
- Which endpoints are public
- Which require authentication
- Which filters to use
- Session management strategy

**Breaking it down**:

1. **CSRF Disabled**: 
   ```java
   .csrf(AbstractHttpConfigurer::disable)
   ```
   - CSRF protects against Cross-Site Request Forgery
   - Only needed for browser-based, session cookie authentication
   - JWT is stateless, CSRF doesn't apply
   - **Gotcha**: If you ever use cookies for JWT, re-enable CSRF!

2. **CORS Configuration**:
   ```java
   .cors(cors -> cors.configurationSource(corsConfigurationSource()))
   ```
   - Allows frontend (React, Angular) from different origin
   - Configures which domains can access your API
   - Handles preflight OPTIONS requests

3. **Authorization Rules**:
   ```java
   .authorizeHttpRequests(authorize -> authorize
       .requestMatchers("/api/auth/**").permitAll()  // Public
       .requestMatchers("/api/admin/**").hasRole("ADMIN")  // Admin only
       .anyRequest().authenticated()  // Everything else
   )
   ```
   - **Order matters!** Rules are evaluated top-to-bottom
   - More specific patterns should come first
   - `permitAll()` = no authentication needed
   - `hasRole("ADMIN")` = requires ROLE_ADMIN authority
   - `authenticated()` = any authenticated user

   **Quirk**: `hasRole("ADMIN")` automatically adds "ROLE_" prefix. Your User entity should return "ROLE_ADMIN" from `getAuthorities()`.

4. **Stateless Sessions**:
   ```java
   .sessionManagement(session -> session
       .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
   )
   ```
   - Tells Spring Security: "Never create HTTP sessions"
   - Critical for JWT authentication
   - Each request is independent
   - **Gotcha**: SecurityContext is still created per-request, just not stored in session

5. **Custom Filter Registration**:
   ```java
   .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
   ```
   - Adds your JWT filter before the username/password filter
   - **Order matters!** JWT validation must happen early
   - `UsernamePasswordAuthenticationFilter` handles form-based login (not used in your API)

---

## 3. The Filter Chain: Heart of Spring Security

### 3.1 Filter Lifecycle

Every request goes through the filter chain. Let's trace a request to `/api/user/me`:

```
HTTP GET /api/user/me
Header: Authorization: Bearer eyJhbGc...

    ↓
[1] CorsFilter
    ↓ Checks if request origin is allowed
    ↓ Adds CORS headers to response
    ↓
[2] JwtAuthenticationFilter ← YOUR CUSTOM FILTER
    ↓ Extracts JWT from Authorization header
    ↓ Validates JWT signature and expiration
    ↓ Loads user from database
    ↓ Sets SecurityContext
    ↓
[3] FilterSecurityInterceptor
    ↓ Checks if user has required role
    ↓ Compares SecurityContext authorities with @PreAuthorize rules
    ↓
[4] DispatcherServlet
    ↓ Routes to your controller
    ↓
@GetMapping("/api/user/me")
public ResponseEntity<?> getCurrentUser() {
    // Your code here
}
```

### 3.2 Your Custom JWT Filter Deep Dive

```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    // ...
}
```

**Why extend `OncePerRequestFilter`?**
- Guarantees filter executes exactly once per request
- Even with forward/include scenarios (servlet internals)
- Alternative: `GenericFilterBean` (might execute multiple times)

#### Step-by-Step Execution

```java
@Override
protected void doFilterInternal(
    HttpServletRequest request,
    HttpServletResponse response,
    FilterChain filterChain
) throws ServletException, IOException {
```

**Parameters**:
- `request`: The incoming HTTP request
- `response`: The HTTP response (can be modified)
- `filterChain`: Represents the remaining filters

#### Step 1: Skip Public Endpoints

```java
final String requestPath = request.getServletPath();
if (requestPath.startsWith("/api/auth/")) {
    filterChain.doFilter(request, response);
    return;
}
```

**Why**: 
- `/api/auth/register`, `/api/auth/login` don't need JWT
- Calling `filterChain.doFilter()` passes control to next filter
- `return` exits your filter (important!)

**Quirk**: If you forget `return`, code continues executing after `filterChain.doFilter()`, causing weird bugs.

#### Step 2: Extract Authorization Header

```java
final String authHeader = request.getHeader("Authorization");
if (authHeader == null || !authHeader.startsWith("Bearer ")) {
    filterChain.doFilter(request, response);
    return;
}
```

**Format**: `Authorization: Bearer <token>`

**Why "Bearer"?**
- RFC 6750 standard for OAuth 2.0
- Indicates "bearer token" (whoever has it, can use it)
- Must be followed by exactly one space

**Edge Cases**:
- `Bearer` (no token) → Caught by length check
- `bearer` (lowercase) → Won't match! Case-sensitive
- `Bearer  token` (two spaces) → Token extraction fails

#### Step 3: Extract Token

```java
final String jwt = authHeader.substring(7);
```

**Why 7?** `"Bearer ".length() == 7`

**Gotcha**: If header is `"Bearer "` (just 7 chars), you get empty string, not exception!

#### Step 4: Extract Username from JWT

```java
userEmail = jwtService.extractUsername(jwt);
```

This calls:
```java
public String extractUsername(String token) {
    return extractClaim(token, Claims::getSubject);
}
```

Which calls:
```java
public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
    final Claims claims = extractAllClaims(token);
    return claimsResolver.apply(claims);
}
```

Which calls:
```java
private Claims extractAllClaims(String token) {
    return Jwts.parser()
        .verifyWith(getSignInKey())
        .build()
        .parseSignedClaims(token)
        .getPayload();
}
```

**What happens here**:
1. JWT is base64-decoded
2. Signature is verified using secret key
3. All claims are extracted
4. Subject claim (username/email) is returned

**Exceptions thrown**:
- `JwtException`: Invalid signature
- `ExpiredJwtException`: Token expired
- `MalformedJwtException`: Invalid JWT format
- `UnsupportedJwtException`: Unsupported algorithm

#### Step 5: Check SecurityContext

```java
if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
```

**Why check if authentication is null?**
- Prevents redundant database queries
- If another filter already authenticated, skip this
- **Quirk**: In stateless mode, this is always null for new requests

**SecurityContext is ThreadLocal**:
```java
ThreadLocal<SecurityContext> contextHolder = new ThreadLocal<>();
```
- Each thread has its own isolated SecurityContext
- Automatically cleared after request completes
- **Gotcha**: If you spawn new threads, SecurityContext isn't inherited!

#### Step 6: Load User from Database

```java
UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);
```

**Why load from DB when we have JWT?**
- JWT is immutable (can't update roles in existing token)
- User might be disabled/deleted after JWT was issued
- Get fresh authorities/roles
- Check account status (locked, expired, etc.)

**Performance Impact**: Database query on every request!
- **Optimization**: Cache user details in Redis
- **Alternative**: Include roles in JWT (but they become stale)

#### Step 7: Validate Token

```java
if (jwtService.isTokenValid(jwt, userDetails)) {
```

```java
public boolean isTokenValid(String token, UserDetails userDetails) {
    final String username = extractUsername(token);
    return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
}
```

**Checks**:
1. Username in token matches loaded user (prevents token reuse)
2. Token hasn't expired (checks `exp` claim)

**Why check username again?** Already extracted earlier!
- Defense in depth
- Extra validation layer
- Catches edge cases

#### Step 8: Validate Issuer and Audience

```java
if (jwtService.validateIssuer(jwt) && jwtService.validateAudience(jwt)) {
```

**Issuer (`iss` claim)**: Who created the token
```java
public boolean validateIssuer(String token) {
    String tokenIssuer = extractClaim(token, Claims::getIssuer);
    return issuer.equals(tokenIssuer);  // "authentication-service"
}
```

**Audience (`aud` claim)**: Who the token is for
```java
public boolean validateAudience(String token) {
    Claims claims = extractAllClaims(token);
    return claims.getAudience().contains(audience);  // "authentication-client"
}
```

**Why important?**
- Prevents tokens from other services being used
- Multi-tenant security
- Meets JWT best practices (RFC 7519)

**Quirk**: Audience is a Set, not a single value. One token can target multiple audiences.

#### Step 9: Create Authentication Object

```java
UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
    userDetails,  // Principal
    null,         // Credentials (password not needed after auth)
    userDetails.getAuthorities()  // Roles/permissions
);
```

**Three parameters**:
1. `principal`: The authenticated user (usually `UserDetails`)
2. `credentials`: Password/secret (null after authentication)
3. `authorities`: List of `GrantedAuthority` (roles/permissions)

**Important**: This constructor creates an **authenticated** token!
- There's a 2-parameter constructor that creates **unauthenticated** token
- The 3-parameter version sets `authenticated = true` internally

#### Step 10: Add Request Details

```java
authToken.setDetails(
    new WebAuthenticationDetailsSource().buildDetails(request)
);
```

**What gets stored**:
- Remote IP address
- Session ID (if any)
- Custom details you add

**Why**: 
- Audit logging
- IP-based security checks
- Debugging

**Access later**:
```java
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
WebAuthenticationDetails details = (WebAuthenticationDetails) auth.getDetails();
String ipAddress = details.getRemoteAddress();
```

#### Step 11: Set SecurityContext

```java
SecurityContextHolder.getContext().setAuthentication(authToken);
```

**This is the magic moment!** From now until the request ends:
- `SecurityContextHolder.getContext().getAuthentication()` returns your token
- `@PreAuthorize` annotations work
- Controller can access authenticated user

**Thread-local storage**:
```
Thread-1 [Request A]
    └── SecurityContext
        └── Authentication (user: john@example.com)

Thread-2 [Request B]
    └── SecurityContext
        └── Authentication (user: jane@example.com)
```

Each request in its own thread has isolated security context.

#### Step 12: Continue Filter Chain

```java
filterChain.doFilter(request, response);
```

**What happens next**:
- Remaining filters execute
- Request reaches your controller
- Response is generated
- Travels back through filters in reverse
- **Finally**: SecurityContext is cleared (by Spring Security)

---

## 4. Registration Flow: Step-by-Step

Let's trace a complete registration request:

```http
POST /api/auth/register
Content-Type: application/json

{
  "email": "john@example.com",
  "password": "SecurePass123!",
  "firstName": "John",
  "lastName": "Doe",
  "role": "USER"
}
```

### Step 1: Request enters filter chain

```
JwtAuthenticationFilter.doFilterInternal()
    ↓
if (requestPath.startsWith("/api/auth/")) {
    filterChain.doFilter(request, response);  ← Takes this path
    return;
}
```

**Why skip?** Registration doesn't need authentication!

### Step 2: DispatcherServlet routes to controller

```java
@PostMapping("/api/auth/register")
public ResponseEntity<?> register(@RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
    AuthenticationResponse response = authenticationService.register(request, httpRequest);
    return ResponseEntity.ok(response);
}
```

**@RequestBody**:
- Jackson deserializes JSON → `RegisterRequest` object
- Automatic validation (if you add `@Valid` and validation annotations)

### Step 3: AuthenticationService.register()

```java
@Transactional
public AuthenticationResponse register(RegisterRequest request, HttpServletRequest httpRequest) {
```

**@Transactional**:
- Starts database transaction
- If exception thrown, rolls back
- Ensures atomicity (user saved + tokens generated = atomic operation)

#### Step 3.1: Check if email exists

```java
if (userRepository.existsByEmail(request.getEmail())) {
    throw new IllegalStateException("Email already registered");
}
```

**SQL Generated** (JPA):
```sql
SELECT COUNT(*) > 0 FROM users WHERE email = ?
```

**Why not `findByEmail()`?**
- `existsByEmail()` only counts, more efficient
- Doesn't load entire user object

**Race condition risk**:
```
Thread 1: Check email → Not exists
Thread 2: Check email → Not exists
Thread 1: Insert user → Success
Thread 2: Insert user → UNIQUE CONSTRAINT VIOLATION!
```

**Solution**: Database unique constraint on email column
```java
@Column(nullable = false, unique = true)
private String email;
```

**Exception handling**:
```java
} catch (IllegalStateException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(...);
}
```

#### Step 3.2: Create User entity

```java
User user = new User(
    request.getEmail(),
    passwordEncoder.encode(request.getPassword()),
    request.getFirstName(),
    request.getLastName(),
    request.getRole() != null ? request.getRole() : Role.USER
);
```

**Password encoding**:
```java
passwordEncoder.encode(request.getPassword())
```

BCrypt internals:
```
Input: "SecurePass123!"
    ↓
Generate random salt (16 bytes)
    ↓
Hash password + salt with cost factor (10 rounds by default)
    ↓
Output: "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"
         │   │  │                                                      │
         │   │  └─ 22-char salt                                       └─ 31-char hash
         │   └─ Cost factor (2^10 = 1024 iterations)
         └─ Algorithm identifier ($2a = BCrypt)
```

**Why BCrypt?**
- Slow by design (prevents brute force)
- Built-in salt (prevents rainbow tables)
- Adaptive cost (can increase difficulty over time)

**Quirk**: Same password produces different hashes each time!
```java
passwordEncoder.encode("password123")  // $2a$10$ABC...
passwordEncoder.encode("password123")  // $2a$10$XYZ...  ← Different!
```

That's why verification uses `matches()`:
```java
passwordEncoder.matches("password123", storedHash)  // true for both
```

#### Step 3.3: Set login metadata

```java
user.setLastLogin(LocalDateTime.now());
user.setLastLoginIp(getClientIp(httpRequest));
```

**IP extraction**:
```java
private String getClientIp(HttpServletRequest request) {
    String xForwardedFor = request.getHeader("X-Forwarded-For");
    if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
        return xForwardedFor.split(",")[0].trim();
    }
    
    String xRealIp = request.getHeader("X-Real-IP");
    if (xRealIp != null && !xRealIp.isEmpty()) {
        return xRealIp;
    }
    
    return request.getRemoteAddr();
}
```

**Why three checks?**

1. **X-Forwarded-For**: Standard proxy header
   - Format: `client, proxy1, proxy2`
   - Take first IP (original client)
   - **Gotcha**: Can be spoofed! Trust only if from your proxy

2. **X-Real-IP**: Nginx-specific header
   - Single IP value
   - Simpler than X-Forwarded-For

3. **RemoteAddr**: Direct connection IP
   - Falls back if no proxy headers
   - **Gotcha**: Returns proxy IP if behind proxy!

**Architecture**:
```
Client (203.0.113.10)
    ↓
Nginx Proxy (10.0.1.5)
    ↓ X-Forwarded-For: 203.0.113.10
    ↓ X-Real-IP: 203.0.113.10
    ↓
Your App
    ↓ request.getRemoteAddr() = 10.0.1.5 (proxy IP)
    ↓ But we want 203.0.113.10 (client IP)
```

#### Step 3.4: Save user to database

```java
userRepository.save(user);
```

**What happens**:

1. **@PrePersist callback** (in User entity):
   ```java
   @PrePersist
   protected void onCreate() {
       createdAt = LocalDateTime.now();
       updatedAt = LocalDateTime.now();
   }
   ```

2. **JPA generates SQL**:
   ```sql
   INSERT INTO users (
       email, password, first_name, last_name, role,
       enabled, account_non_expired, account_non_locked,
       credentials_non_expired, created_at, updated_at,
       last_login, last_login_ip
   ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
   ```

3. **Database assigns ID** (auto-increment)

4. **User object updated** with generated ID:
   ```java
   user.getId();  // Now returns database-assigned ID
   ```

**Transaction**:
- Not committed yet! Still in @Transactional scope
- If JWT generation fails, entire transaction rolls back
- User won't be in database

---

## 5. Login Flow: Authentication in Action

```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "john@example.com",
  "password": "SecurePass123!"
}
```

### Step 1: Controller receives request

```java
@PostMapping("/api/login")
public ResponseEntity<?> authenticate(@RequestBody AuthenticationRequest request, HttpServletRequest httpRequest) {
    AuthenticationResponse response = authenticationService.authenticate(request, httpRequest);
    return ResponseEntity.ok(response);
}
```

### Step 2: AuthenticationService.authenticate()

```java
@Transactional
public AuthenticationResponse authenticate(AuthenticationRequest request, HttpServletRequest httpRequest) {
    authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(
            request.getEmail(),
            request.getPassword()
        )
    );
    // ...
}
```

### Step 3: AuthenticationManager delegates to provider

```
authenticationManager.authenticate(token)
    ↓
ProviderManager.authenticate(token)
    ↓
for (AuthenticationProvider provider : providers) {
    if (provider.supports(token.getClass())) {
        return provider.authenticate(token);
    }
}
    ↓
DaoAuthenticationProvider.authenticate(token)
```

### Step 4: DaoAuthenticationProvider loads user

```java
// Inside DaoAuthenticationProvider
UserDetails user = this.userDetailsService.loadUserByUsername(username);
```

Calls your:
```java
@Override
public UserDetails loadUserByUsername(String email) {
    return userRepository.findByEmail(email)
        .orElseThrow(() -> new UsernameNotFoundException("User not found"));
}
```

**SQL**:
```sql
SELECT * FROM users WHERE email = ?
```

**What if user not found?**
```java
throw new UsernameNotFoundException("User not found");
```
- Caught by Spring Security
- Converted to `BadCredentialsException`
- **Why?** Security! Don't reveal whether email exists

### Step 5: Verify password

```java
// Inside DaoAuthenticationProvider
if (!passwordEncoder.matches(presentedPassword, user.getPassword())) {
    throw new BadCredentialsException("Bad credentials");
}
```

**BCrypt matching**:
```
Input: "SecurePass123!"
Stored: "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZ..."
    ↓
Extract salt and cost from stored hash
    ↓
Hash input password with same salt and cost
    ↓
Compare result with stored hash
    ↓
Return true/false
```

**Timing attack protection**:
- BCrypt takes ~100ms (intentionally slow)
- Same time whether password correct or wrong
- Prevents timing-based password guessing

### Step 6: Check account status

```java
// Inside DaoAuthenticationProvider
preAuthenticationChecks.check(user);
```

Checks (from your `User` entity):
```java
@Override
public boolean isAccountNonExpired() { return accountNonExpired; }

@Override
public boolean isAccountNonLocked() { return accountNonLocked; }

@Override
public boolean isCredentialsNonExpired() { return credentialsNonExpired; }

@Override
public boolean isEnabled() { return enabled; }
```

**Exceptions thrown**:
- `AccountExpiredException` if account expired
- `LockedException` if account locked
- `CredentialsExpiredException` if credentials expired
- `DisabledException` if account disabled

**Use cases**:
```java
// Temporary ban
user.setAccountNonLocked(false);

// Force password reset
user.setCredentialsNonExpired(false);

// Deactivate account
user.setEnabled(false);
```

### Step 7: Create authenticated token

```java
// Inside DaoAuthenticationProvider
return createSuccessAuthentication(user, authentication, user);
```

Returns:
```java
UsernamePasswordAuthenticationToken(
    user,           // Principal
    null,           // Credentials cleared!
    user.getAuthorities()  // Roles
)
```

**Back in your service**:
```java
// If we get here, authentication succeeded!
User user = userRepository.findByEmail(request.getEmail())
    .orElseThrow(() -> new UsernameNotFoundException("User not found"));
```

**Why load user again?**
- `AuthenticationManager` just validates credentials
- Doesn't return the User entity
- Need full entity to update login metadata

### Step 8: Update login metadata

```java
user.setLastLogin(LocalDateTime.now());
user.setLastLoginIp(getClientIp(httpRequest));
userRepository.save(user);
```

**Audit trail**: Track when and where users log in

---

## 6. JWT Generation: Creating Tokens

Both registration and login end with:

```java
String accessToken = jwtService.generateAccessToken(user);
String refreshToken = jwtService.generateRefreshToken(user);
```

### 6.1 Access Token Generation

```java
public String generateAccessToken(UserDetails userDetails) {
    return generateAccessToken(new HashMap<>(), userDetails);
}

public String generateAccessToken(Map<String, Object> extraClaims, UserDetails userDetails) {
    return buildToken(extraClaims, userDetails, accessTokenExpiration);
}
```

**Extra claims**: Custom data you want in the token
```java
Map<String, Object> claims = new HashMap<>();
claims.put("userId", user.getId());
claims.put("roles", user.getRoles());
jwtService.generateAccessToken(claims, user);
```

**Caution**: JWT is NOT encrypted, only signed!
- Anyone can decode and read claims
- Don't put sensitive data (passwords, SSNs, etc.)

### 6.2 Token Building

```java
private String buildToken(
    Map<String, Object> extraClaims,
    UserDetails userDetails,
    long expiration
) {
    long currentTimeMillis = System.currentTimeMillis();
    
    return Jwts.builder()
        .claims(extraClaims)
        .subject(userDetails.getUsername())
        .issuer(issuer)
        .audience().add(audience).and()
        .issuedAt(new Date(currentTimeMillis))
        .expiration(new Date(currentTimeMillis + expiration))
        .signWith(getSignInKey())
        .compact();
}
```

#### Claim by Claim

1. **Custom Claims**:
   ```java
   .claims(extraClaims)
   ```
   - Added first (can be overridden by standard claims)
   - Avoid reserved claim names: `iss`, `sub`, `aud`, `exp`, `iat`, `nbf`, `jti`

2. **Subject** (`sub`):
   ```java
   .subject(userDetails.getUsername())
   ```
   - Primary identifier for the token
   - In your case: user's email
   - **Standard**: Who the token is about

3. **Issuer** (`iss`):
   ```java
   .issuer(issuer)  // "authentication-service"
   ```
   - Who created the token
   - Validates token came from your service
   - **Multi-service**: Each service has unique issuer

4. **Audience** (`aud`):
   ```java
   .audience().add(audience).and()  // "authentication-client"
   ```
   - Who should accept the token
   - Can have multiple audiences
   - **Example**: Token for both "web-app" and "mobile-app"

5. **Issued At** (`iat`):
   ```java
   .issuedAt(new Date(currentTimeMillis))
   ```
   - When token was created
   - Unix timestamp (seconds since 1970)
   - **Use**: Audit, token freshness checks

6. **Expiration** (`exp`):
   ```java
   .expiration(new Date(currentTimeMillis + expiration))
   ```
   - When token becomes invalid
   - Access token: 15 minutes (900000ms)
   - Refresh token: 7 days (604800000ms)
   - **Auto-validated**: JJWT library checks this automatically

7. **Signature**:
   ```java
   .signWith(getSignInKey())
   ```

#### Digital Signature Deep Dive

```java
private SecretKey getSignInKey() {
    byte[] keyBytes = Decoders.BASE64.decode(secretKey);
    return Keys.hmacShaKeyFor(keyBytes);
}
```

**Process**:

1. **Secret Key** (from application.properties):
   ```properties
   jwt.secret.key=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970
   ```
   - Base64-encoded
   - Minimum 256 bits (32 bytes) for HS256
   - **Never** commit to Git! Use environment variables

2. **Decode to bytes**:
   ```java
   byte[] keyBytes = Decoders.BASE64.decode(secretKey);
   ```

3. **Create HMAC key**:
   ```java
   Keys.hmacShaKeyFor(keyBytes)
   ```
   - HMAC-SHA256 algorithm
   - Same key for signing and verification (symmetric)
   - **Alternative**: RS256 uses RSA (asymmetric, public/private keys)

4. **Sign token**:
   ```
   Header + Payload
       ↓
   HMAC-SHA256(header.payload, secretKey)
       ↓
   Signature
   ```

#### Token Structure

```java
.compact()
```

Creates:
```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJqb2huQGV4YW1wbGUuY29tIiwiaXNzIjoiYXV0aGVudGljYXRpb24tc2VydmljZSIsImF1ZCI6ImF1dGhlbnRpY2F0aW9uLWNsaWVudCIsImlhdCI6MTcwOTA0NjQwMCwiZXhwIjoxNzA5MDQ3MzAwfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c
│                                           │                                                                                                                                                                                    │
└─────────────── Header ───────────────────┴────────────────────────────────────────────────────────── Payload ──────────────────────────────────────────────────────────────────────┴──────────── Signature ───────────
```

**Decoded**:

**Header**:
```json
{
  "alg": "HS256",
  "typ": "JWT"
}
```

**Payload**:
```json
{
  "sub": "john@example.com",
  "iss": "authentication-service",
  "aud": "authentication-client",
  "iat": 1709046400,
  "exp": 1709047300
}
```

**Signature**: Binary hash (Base64URL encoded)

### 6.3 Access Token vs Refresh Token

**Access Token**:
```java
jwt.access.token.expiration=900000  // 15 minutes
```
- Short-lived
- Sent with every API request
- Contains user identity and roles
- If stolen, attacker has limited time window
- Not stored in database (stateless)

**Refresh Token**:
```java
jwt.refresh.token.expiration=604800000  // 7 days
```
- Long-lived
- Used only to get new access token
- Should be stored securely (HTTP-only cookie)
- Can be revoked (store in database)
- **Best practice**: Rotate on each use

**Why two tokens?**

```
Access Token Stolen:
└── Attacker has 15 minutes to attack
└── Limited damage

Refresh Token Stolen:
└── Attacker can get new access tokens
└── But only from /api/auth/refresh endpoint
└── You can implement stricter validation (IP check, device fingerprint)
└── You can revoke refresh token in database
```

---

## 7. JWT Consumption: Validating Requests

When a client makes an authenticated request:

```http
GET /api/user/me
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

We already covered this in [Section 3.2](#step-by-step-execution), but let's dive deeper into the validation logic.

### 7.1 Token Extraction and Parsing

```java
private Claims extractAllClaims(String token) {
    return Jwts.parser()
        .verifyWith(getSignInKey())
        .build()
        .parseSignedClaims(token)
        .getPayload();
}
```

**What JJWT does internally**:

1. **Split token**:
   ```java
   String[] parts = token.split("\\.");
   String encodedHeader = parts[0];
   String encodedPayload = parts[1];
   String encodedSignature = parts[2];
   ```

2. **Decode header and payload**:
   ```java
   byte[] header = Base64URL.decode(encodedHeader);
   byte[] payload = Base64URL.decode(encodedPayload);
   ```

3. **Verify signature**:
   ```java
   byte[] expectedSignature = HMAC_SHA256(header + "." + payload, secretKey);
   byte[] actualSignature = Base64URL.decode(encodedSignature);
   
   if (!Arrays.equals(expectedSignature, actualSignature)) {
       throw new SignatureException("JWT signature does not match");
   }
   ```

4. **Parse claims**:
   ```java
   Claims claims = parseJson(payload);
   ```

5. **Check expiration**:
   ```java
   if (claims.getExpiration().before(new Date())) {
       throw new ExpiredJwtException(claims);
   }
   ```

**Exceptions you might see**:

```java
try {
    Claims claims = extractAllClaims(token);
} catch (ExpiredJwtException e) {
    // Token expired
    // e.getClaims() still available (can read expired token)
} catch (UnsupportedJwtException e) {
    // Unsupported algorithm or format
} catch (MalformedJwtException e) {
    // Invalid JWT structure
} catch (SignatureException e) {
    // Signature verification failed
} catch (IllegalArgumentException e) {
    // Empty or null token
}
```

### 7.2 Claim Extraction

```java
public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
    final Claims claims = extractAllClaims(token);
    return claimsResolver.apply(claims);
}
```

**Functional programming pattern**:

```java
// Extract subject
String email = jwtService.extractClaim(token, Claims::getSubject);

// Extract expiration
Date expiration = jwtService.extractClaim(token, Claims::getExpiration);

// Extract custom claim
String userId = jwtService.extractClaim(token, claims -> claims.get("userId", String.class));
```

**Method reference** `Claims::getSubject` is equivalent to:
```java
(claims) -> claims.getSubject()
```

### 7.3 Token Validation Logic

```java
public boolean isTokenValid(String token, UserDetails userDetails) {
    final String username = extractUsername(token);
    return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
}
```

**Two checks**:

1. **Username match**:
   ```java
   username.equals(userDetails.getUsername())
   ```
   - Token's `sub` claim matches loaded user
   - **Why needed?** Prevents token swapping
   - **Scenario**: Attacker intercepts token for user A, tries to use it for user B

2. **Not expired**:
   ```java
   !isTokenExpired(token)
   ```
   ```java
   private boolean isTokenExpired(String token) {
       return extractExpiration(token).before(new Date());
   }
   ```
   - Checks `exp` claim
   - **Note**: Already checked by JJWT, but double-validated
   - **Edge case**: Clock skew between servers

### 7.4 Issuer and Audience Validation

```java
if (jwtService.validateIssuer(jwt) && jwtService.validateAudience(jwt)) {
    // Proceed with authentication
}
```

**Why critical?**

Imagine you have multiple services:
- `authentication-service` (your app)
- `payment-service`
- `admin-service`

A token from `payment-service` shouldn't work on your authentication API!

```java
public boolean validateIssuer(String token) {
    String tokenIssuer = extractClaim(token, Claims::getIssuer);
    return issuer.equals(tokenIssuer);
}
```

**Scenario**:
```
Token from payment-service:
{
  "iss": "payment-service",
  "sub": "admin@example.com",
  "aud": "payment-client"
}

Your service expects:
{
  "iss": "authentication-service",  ← Mismatch!
  "aud": "authentication-client"    ← Mismatch!
}

Result: Authentication fails ✓
```

### 7.5 Loading Fresh User Data

```java
UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);
```

**Why load from database on every request?**

Pro:
- Get latest user data (role changes, account status)
- Detect disabled/deleted users
- Up-to-date authorities

Con:
- Database query on every request
- Performance impact at scale

**Optimization strategies**:

1. **Redis caching**:
   ```java
   @Cacheable("users")
   public UserDetails loadUserByUsername(String email) {
       return userRepository.findByEmail(email)...
   }
   ```

2. **Include roles in JWT**:
   ```java
   Map<String, Object> claims = new HashMap<>();
   claims.put("roles", user.getRoles());
   jwtService.generateAccessToken(claims, user);
   ```
   - Extract roles from token, skip DB query
   - **Downside**: Stale roles until token expires

3. **Hybrid approach**:
   - Include roles in JWT (skip DB for role checks)
   - Still query DB for sensitive operations
   - Best of both worlds

---

## 8. Token Refresh: Extending Sessions

```http
POST /api/auth/refresh
Content-Type: application/json

{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

### 8.1 Refresh Flow

```java
public AuthenticationResponse refreshToken(RefreshTokenRequest request, HttpServletRequest httpRequest) {
    final String refreshToken = request.getRefreshToken();
    final String userEmail;

    try {
        userEmail = jwtService.extractUsername(refreshToken);

        if (userEmail != null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);

            if (jwtService.isTokenValid(refreshToken, userDetails)) {
                if (jwtService.validateIssuer(refreshToken) && jwtService.validateAudience(refreshToken)) {
                    // ... IP check ...
                    
                    String accessToken = jwtService.generateAccessToken(userDetails);

                    return new AuthenticationResponse(
                        accessToken,
                        refreshToken,  ← Same refresh token returned!
                        jwtService.getAccessTokenExpiration()
                    );
                }
            }
        }
    } catch (Exception e) {
        throw new IllegalStateException("Invalid refresh token");
    }

    throw new IllegalStateException("Invalid refresh token");
}
```

### 8.2 Refresh Token Rotation (Not Implemented, But Should Be!)

**Current approach**:
- Same refresh token reused indefinitely
- If stolen, attacker has access until token expires (7 days)

**Best practice - Refresh token rotation**:
```java
// Generate NEW refresh token on each use
String newAccessToken = jwtService.generateAccessToken(userDetails);
String newRefreshToken = jwtService.generateRefreshToken(userDetails);  // NEW!

// Invalidate old refresh token (store in database)
revokedTokenRepository.save(new RevokedToken(oldRefreshToken));

return new AuthenticationResponse(
    newAccessToken,
    newRefreshToken,  // Send new refresh token
    jwtService.getAccessTokenExpiration()
);
```

**Why rotate?**

```
User uses refresh token:
└── Gets new access + refresh tokens
└── Old refresh token blacklisted

Attacker steals old refresh token:
└── Tries to use it
└── Server: "This token was already used!"
└── Server: Invalidate ALL user's tokens (security breach detected)
└── User: Forced to re-login
└── Attacker: Locked out
```

### 8.3 IP-Based Anomaly Detection

```java
User user = userRepository.findByEmail(userEmail)
    .orElseThrow(() -> new UsernameNotFoundException("User not found"));

String currentIp = getClientIp(httpRequest);
String lastLoginIp = user.getLastLoginIp();

if (lastLoginIp != null && !lastLoginIp.equals(currentIp)) {
    System.out.println("Warning: IP address changed during token refresh for user: " + userEmail);
    // In production: Send email alert, require re-authentication, etc.
}
```

**Enhancement ideas**:

```java
// 1. Reject if IP changed
if (!lastLoginIp.equals(currentIp)) {
    throw new SecurityException("IP address mismatch");
}

// 2. Allow only same country (use GeoIP database)
String lastCountry = geoIpService.getCountry(lastLoginIp);
String currentCountry = geoIpService.getCountry(currentIp);
if (!lastCountry.equals(currentCountry)) {
    sendEmailAlert(user, "Login from new country: " + currentCountry);
}

// 3. Device fingerprinting
String deviceId = request.getHeader("X-Device-ID");
if (!user.getTrustedDevices().contains(deviceId)) {
    require2FA();
}
```

### 8.4 When Should Client Refresh?

**Strategy 1: Proactive refresh**
```javascript
// Frontend: Refresh before expiration
setInterval(() => {
    if (tokenExpiresIn < 5 * 60 * 1000) {  // Less than 5 minutes left
        refreshAccessToken();
    }
}, 60 * 1000);  // Check every minute
```

**Strategy 2: Reactive refresh**
```javascript
// Frontend: Refresh on 401 error
axios.interceptors.response.use(
    response => response,
    async error => {
        if (error.response.status === 401) {
            await refreshAccessToken();
            return axios.request(error.config);  // Retry original request
        }
        return Promise.reject(error);
    }
);
```

**Recommended**: Proactive (smoother UX)

---

## 9. Token Revocation: Advanced Security

**Current limitation**: You can't invalidate JWTs before they expire!

### 9.1 Why Revocation is Hard

JWT is **stateless**:
- Server doesn't track issued tokens
- Can't revoke until expiration
- No central session store

**Scenarios requiring revocation**:
- User logs out
- Password changed
- Account compromised
- Admin bans user

### 9.2 Token Blacklist Approach

Database table (see your `database-schema-optional.sql`):
```sql
CREATE TABLE revoked_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    token VARCHAR(500) NOT NULL UNIQUE,
    token_type ENUM('ACCESS', 'REFRESH') NOT NULL,
    user_id BIGINT NOT NULL,
    revoked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    reason VARCHAR(255),
    INDEX idx_token (token)
);
```

**Implementation**:

```java
@Service
public class TokenRevocationService {
    @Autowired
    private RevokedTokenRepository revokedTokenRepository;

    public void revokeToken(String token, String reason) {
        Date expiration = jwtService.extractExpiration(token);
        RevokedToken revoked = new RevokedToken(token, expiration, reason);
        revokedTokenRepository.save(revoked);
    }

    public boolean isTokenRevoked(String token) {
        return revokedTokenRepository.existsByToken(token);
    }
}
```

**Update JWT filter**:
```java
// In JwtAuthenticationFilter
if (jwtService.isTokenValid(jwt, userDetails)) {
    // Check blacklist
    if (tokenRevocationService.isTokenRevoked(jwt)) {
        logger.warn("Attempt to use revoked token");
        filterChain.doFilter(request, response);
        return;  // Don't authenticate
    }
    
    // ... rest of authentication ...
}
```

**Logout implementation**:
```java
@PostMapping("/api/auth/logout")
public ResponseEntity<?> logout(@RequestHeader("Authorization") String authHeader) {
    String token = authHeader.substring(7);
    tokenRevocationService.revokeToken(token, "User logout");
    return ResponseEntity.ok("Logged out successfully");
}
```

**Cleanup job** (remove expired tokens):
```java
@Scheduled(cron = "0 0 2 * * ?")  // Run at 2 AM daily
public void cleanupExpiredTokens() {
    revokedTokenRepository.deleteByExpiresAtBefore(new Date());
}
```

### 9.3 Token Versioning Approach

Add `tokenVersion` to User entity:
```java
@Entity
public class User implements UserDetails {
    // ...
    
    @Column(nullable = false)
    private Long tokenVersion = 1L;
    
    public void incrementTokenVersion() {
        this.tokenVersion++;
    }
}
```

**Include in JWT**:
```java
Map<String, Object> claims = new HashMap<>();
claims.put("tokenVersion", user.getTokenVersion());
jwtService.generateAccessToken(claims, user);
```

**Validate in filter**:
```java
Long tokenVersion = jwtService.extractClaim(jwt, claims -> claims.get("tokenVersion", Long.class));
User user = userRepository.findByEmail(userEmail).orElseThrow();

if (!tokenVersion.equals(user.getTokenVersion())) {
    logger.warn("Token version mismatch - token invalidated");
    return;  // Don't authenticate
}
```

**Revoke all tokens**:
```java
@PostMapping("/api/auth/logout-all-devices")
public ResponseEntity<?> logoutAllDevices() {
    User user = getCurrentUser();
    user.incrementTokenVersion();
    userRepository.save(user);
    return ResponseEntity.ok("Logged out from all devices");
}
```

**Pros**:
- No database lookup on every request
- Simple to implement
- Instant revocation

**Cons**:
- Must include version in JWT (slightly larger tokens)
- Still need database query to get user (but you're already doing that)

---

## 10. Security Context: Thread-Local Magic

### 10.1 What is SecurityContextHolder?

```java
SecurityContextHolder.getContext().setAuthentication(authToken);
```

**Internal structure**:
```java
public class SecurityContextHolder {
    private static ThreadLocal<SecurityContext> contextHolder = new ThreadLocal<>();
    
    public static SecurityContext getContext() {
        SecurityContext ctx = contextHolder.get();
        if (ctx == null) {
            ctx = createEmptyContext();
            contextHolder.set(ctx);
        }
        return ctx;
    }
    
    public static void clearContext() {
        contextHolder.remove();
    }
}
```

### 10.2 ThreadLocal Explained

**ThreadLocal** = variable that exists separately for each thread

```java
Thread-1 (User A's request)
    └── ThreadLocal variable = "User A data"

Thread-2 (User B's request)
    └── ThreadLocal variable = "User B data"

Thread-1 reads ThreadLocal → "User A data"
Thread-2 reads ThreadLocal → "User B data"
```

**Why needed?**

```java
// WITHOUT ThreadLocal (WRONG!):
static SecurityContext globalContext;  // Shared by all threads!

// Thread-1 sets: globalContext = UserA
// Thread-2 sets: globalContext = UserB
// Thread-1 reads: globalContext → UserB (WRONG! Data leaked!)
```

**With ThreadLocal**:
```java
// Thread-1 sets: threadLocal.set(UserA)
// Thread-2 sets: threadLocal.set(UserB)
// Thread-1 reads: threadLocal.get() → UserA ✓
// Thread-2 reads: threadLocal.get() → UserB ✓
```

### 10.3 SecurityContext Lifecycle

```
Request arrives
    ↓
[1] SecurityContextHolder.clearContext() (start clean)
    ↓
[2] Filters execute
    ↓
[3] JwtAuthenticationFilter sets authentication
    SecurityContextHolder.getContext().setAuthentication(user)
    ↓
[4] Controller executes
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    UserDetails user = (UserDetails) auth.getPrincipal();
    ↓
[5] Response sent
    ↓
[6] SecurityContextHolder.clearContext() (Spring Security cleanup filter)
    ↓
Request complete (thread available for next request)
```

**Critical**: If you forget to clear, next request on same thread inherits previous user!

### 10.4 Accessing Current User

**In controllers**:
```java
@GetMapping("/api/user/me")
public ResponseEntity<?> getCurrentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    
    if (authentication != null && authentication.getPrincipal() instanceof User) {
        User user = (User) authentication.getPrincipal();
        // Use user...
    }
}
```

**Shorter version** (Spring Security provides this):
```java
@GetMapping("/api/user/me")
public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal User user) {
    // 'user' injected by Spring Security!
}
```

**Even shorter**:
```java
@GetMapping("/api/user/me")
public ResponseEntity<?> getCurrentUser(Principal principal) {
    String email = principal.getName();
    // But you lose User entity fields
}
```

### 10.5 Common Pitfalls

**Pitfall 1: Async methods**

```java
@Async
public void sendEmail() {
    // SecurityContext is NOT inherited by new thread!
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    // auth is NULL!
}
```

**Solution**:
```java
// Pass user explicitly
@Async
public void sendEmail(User user) {
    // Use user parameter
}

// Or configure Spring Security for async
@EnableAsync
@Configuration
public class AsyncConfig implements AsyncConfigurer {
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setTaskDecorator(new SecurityContextCopyingTaskDecorator());
        return executor;
    }
}
```

**Pitfall 2: New thread creation**

```java
new Thread(() -> {
    // SecurityContext NOT available!
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    // auth is NULL!
}).start();
```

**Solution**: Don't create threads manually in Spring applications. Use `@Async` or thread pools.

**Pitfall 3: Reactive programming**

```java
return Mono.fromCallable(() -> {
    // SecurityContext NOT inherited!
    return SecurityContextHolder.getContext().getAuthentication();
});
```

**Solution**: Use `ReactiveSecurityContextHolder` for WebFlux/Reactor

---

## 11. Quirks, Gotchas, and Edge Cases

### 11.1 BCrypt Quirks

**1. 72-byte password limit**:
```java
String longPassword = "A".repeat(100);
String hash1 = passwordEncoder.encode(longPassword);

String truncatedPassword = longPassword.substring(0, 72);
String hash2 = passwordEncoder.encode(truncatedPassword);

// Both validate as same password!
passwordEncoder.matches(longPassword, hash1);  // true
passwordEncoder.matches(truncatedPassword, hash1);  // true
```

**Solution**: Pre-hash long passwords with SHA-256 before BCrypt (rare need)

**2. Null password**:
```java
passwordEncoder.encode(null);  // Throws IllegalArgumentException
```

**3. Empty password**:
```java
passwordEncoder.encode("");  // Works! Returns valid BCrypt hash
```

**Solution**: Add validation:
```java
if (password == null || password.trim().isEmpty()) {
    throw new IllegalArgumentException("Password cannot be empty");
}
```

### 11.2 JWT Quirks

**1. Token size**:
```java
// Each claim increases token size
Map<String, Object> claims = new HashMap<>();
claims.put("roles", user.getRoles());  // +50 bytes
claims.put("permissions", user.getPermissions());  // +100 bytes
claims.put("metadata", largeObject);  // +1000 bytes

String token = jwtService.generateAccessToken(claims, user);
// Token is now 1500+ bytes!
```

**HTTP header limit**: 8KB (varies by server)
**Cookie limit**: 4KB

**Solution**: Keep JWTs minimal. Store only essential claims.

**2. Clock skew**:
```java
// Server A creates token at 10:00:00
// Server B receives token at 09:59:58 (clock 2 seconds behind)
// Token "issued in the future"!
```

**Solution**: Add leeway:
```java
Jwts.parser()
    .clockSkewSeconds(60)  // Allow 60 seconds difference
    .verifyWith(key)
    .build();
```

**3. Token in URL**:
```
https://api.example.com/api/user/me?token=eyJhbGc...
```

**NEVER DO THIS!**
- URLs logged in server logs
- URLs stored in browser history
- URLs visible in network tab
- URLs shared accidentally

**Always use Authorization header**:
```
Authorization: Bearer eyJhbGc...
```

### 11.3 Spring Security Quirks

**1. Role prefix**:
```java
// User entity returns:
new SimpleGrantedAuthority("ROLE_ADMIN")

// Security config checks:
.hasRole("ADMIN")  // Automatically adds ROLE_ prefix

// But:
.hasAuthority("ROLE_ADMIN")  // Exact match, no prefix added
```

**Confusing!** Use one approach consistently.

**2. Method security not enabled**:
```java
@PreAuthorize("hasRole('ADMIN')")
public void adminMethod() {
    // This does NOTHING if @EnableMethodSecurity is missing!
}
```

**Solution**: Add to config:
```java
@EnableMethodSecurity
public class SecurityConfig { }
```

**3. Filter order matters**:
```java
// WRONG:
.addFilterAfter(jwtFilter, UsernamePasswordAuthenticationFilter.class)
// JWT validation happens AFTER UsernamePassword filter (too late!)

// CORRECT:
.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
```

**4. AntPathRequestMatcher vs MvcRequestMatcher**:
```java
// Ant matcher:
.requestMatchers("/api/users/*").permitAll()
// Matches: /api/users/123
// NOT matches: /api/users/123/profile

// Wildcard:
.requestMatchers("/api/users/**").permitAll()
// Matches: /api/users/123/profile/settings
```

### 11.4 Database Quirks

**1. Transaction rollback doesn't clear SecurityContext**:
```java
@Transactional
public void register(RegisterRequest request) {
    User user = new User(...);
    userRepository.save(user);
    
    // Set authentication
    SecurityContextHolder.getContext().setAuthentication(auth);
    
    throw new RuntimeException("Oops!");  // Transaction rolls back
    
    // BUT SecurityContext still has authentication!
    // User thinks they're logged in, but user doesn't exist in DB!
}
```

**Solution**: Set authentication AFTER successful transaction

**2. LazyInitializationException**:
```java
@GetMapping("/api/user/me")
public ResponseEntity<?> getUser() {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    
    // User entity loaded in filter (different transaction)
    user.getOrders().size();  // LazyInitializationException!
}
```

**Solution**: Use DTOs, fetch joins, or explicit loading

### 11.5 Production Gotchas

**1. Logging sensitive data**:
```java
logger.info("User logged in: " + authRequest);  // Contains password!
logger.debug("JWT token: " + token);  // Token exposed in logs!
```

**Solution**: Never log passwords/tokens. Redact sensitive fields.

**2. Exception messages leak information**:
```java
// BAD:
throw new UsernameNotFoundException("User " + email + " not found");
// Tells attacker: "This email doesn't exist, try another"

// GOOD:
throw new BadCredentialsException("Invalid username or password");
// Doesn't reveal whether email or password was wrong
```

**3. Timing attacks**:
```java
// BAD:
if (user == null) {
    return false;  // Returns instantly
}
if (!passwordEncoder.matches(password, user.getPassword())) {
    return false;  // Returns after 100ms (BCrypt)
}
// Attacker can detect if email exists by timing!
```

**Solution**: Always check password even if user doesn't exist:
```java
String dummyHash = "$2a$10$dummy...";
if (user == null) {
    passwordEncoder.matches(password, dummyHash);  // Waste same time
    return false;
}
return passwordEncoder.matches(password, user.getPassword());
```

**4. Secret key in Git**:
```properties
# application.properties (NEVER COMMIT THIS!)
jwt.secret.key=supersecretkey123
```

**Solution**: Use environment variables:
```properties
jwt.secret.key=${JWT_SECRET_KEY}
```

```bash
export JWT_SECRET_KEY="your-secret-key"
```

---

## 12. Performance Considerations

### 12.1 Bottlenecks

**1. BCrypt is slow (by design)**:
```java
// Takes ~100ms per hash!
passwordEncoder.encode(password);
```

**Login load**:
- 10 logins/second = 1 second CPU time/second = 100% CPU!
- Solution: Scale horizontally, use async processing

**2. Database query on every request**:
```java
UserDetails user = userDetailsService.loadUserByUsername(email);
// SELECT * FROM users WHERE email = ?
```

**100 req/sec = 100 DB queries/sec**

**Solutions**:

**Option A: Redis caching**:
```java
@Cacheable(value = "users", key = "#email")
public UserDetails loadUserByUsername(String email) {
    return userRepository.findByEmail(email).orElseThrow();
}

// First request: Queries DB, stores in Redis
// Subsequent requests: Reads from Redis (microseconds)
```

**Cache invalidation**:
```java
@CacheEvict(value = "users", key = "#user.email")
public void updateUser(User user) {
    userRepository.save(user);
}
```

**Option B: Stateless roles (no DB query)**:
```java
// Include roles in JWT
Map<String, Object> claims = new HashMap<>();
claims.put("roles", user.getRoles().toString());

// Extract roles from JWT (no DB hit)
String roles = jwtService.extractClaim(token, claims -> claims.get("roles", String.class));
```

**Tradeoff**: Stale roles until token expires

### 12.2 Optimization Strategies

**1. Connection pooling**:
```properties
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
```

**2. Async token generation**:
```java
@Async
public CompletableFuture<String> generateToken(User user) {
    return CompletableFuture.completedFuture(
        jwtService.generateAccessToken(user)
    );
}
```

**3. Token in Redis (alternative to database)**:
```java
@Autowired
private RedisTemplate<String, String> redisTemplate;

public void storeRefreshToken(String token, String userId) {
    redisTemplate.opsForValue().set(
        "refresh:" + userId,
        token,
        7,
        TimeUnit.DAYS
    );
}
```

**4. Batch operations**:
```java
// Instead of:
for (User user : users) {
    userRepository.save(user);  // N queries
}

// Use:
userRepository.saveAll(users);  // 1 batch query
```

---

## 13. Summary: The Complete Picture

Let's tie everything together with a complete request flow:

### Registration Request

```
POST /api/auth/register {"email": "john@example.com", ...}
    ↓
[Application startup beans already initialized]
    ↓
[Filter Chain]
├── JwtAuthenticationFilter
│   └── if (path.startsWith("/api/auth/")) skip ✓
├── [Other filters]
    ↓
[DispatcherServlet] Routes to AuthenticationController.register()
    ↓
[AuthenticationService.register()]
├── Check email exists (SELECT)
├── Create User entity
├── Hash password with BCrypt (~100ms)
├── Save to database (INSERT)
├── Generate access token (JWT)
├── Generate refresh token (JWT)
└── Return tokens
    ↓
[Transaction commits]
    ↓
Response: { "accessToken": "...", "refreshToken": "..." }
```

### Login Request

```
POST /api/auth/login {"email": "john@example.com", "password": "..."}
    ↓
[Filter Chain - skips JWT filter]
    ↓
[AuthenticationController.login()]
    ↓
[AuthenticationService.authenticate()]
├── Call authenticationManager.authenticate()
│   ├── DaoAuthenticationProvider
│   ├── Load user from DB (SELECT)
│   ├── Verify password with BCrypt (~100ms)
│   └── Check account status
├── Update login metadata (UPDATE)
├── Generate tokens
└── Return tokens
```

### Authenticated Request

```
GET /api/user/me
Authorization: Bearer eyJhbGc...
    ↓
[Filter Chain]
├── JwtAuthenticationFilter
│   ├── Extract token from header
│   ├── Parse and verify JWT signature
│   ├── Extract username from token
│   ├── Load user from DB (SELECT)
│   ├── Validate token (expiry, issuer, audience)
│   ├── Create Authentication object
│   └── SecurityContextHolder.setAuthentication()
├── [Other filters]
│   └── FilterSecurityInterceptor checks @PreAuthorize
    ↓
[UserController.getCurrentUser()]
├── Access SecurityContextHolder
├── Get authenticated user
└── Return user data
    ↓
[Response sent]
    ↓
[Cleanup filter clears SecurityContext]
```

### Token Refresh

```
POST /api/auth/refresh {"refreshToken": "..."}
    ↓
[Filter Chain - skips JWT filter]
    ↓
[AuthenticationService.refreshToken()]
├── Parse refresh token
├── Extract username
├── Load user from DB
├── Validate refresh token
├── Check IP address (anomaly detection)
├── Generate NEW access token
└── Return tokens (same refresh token)
```

---

## 14. Best Practices Checklist

✅ **Security**
- [ ] Use HTTPS in production
- [ ] Store JWT secret in environment variables (not in code)
- [ ] Implement token revocation for sensitive operations
- [ ] Add rate limiting on auth endpoints
- [ ] Use refresh token rotation
- [ ] Validate issuer and audience claims
- [ ] Implement IP/device checks
- [ ] Never log passwords or tokens

✅ **Performance**
- [ ] Cache user details in Redis
- [ ] Use connection pooling
- [ ] Consider including roles in JWT (accept stale data tradeoff)
- [ ] Implement async processing where appropriate
- [ ] Monitor database query performance

✅ **Code Quality**
- [ ] Use DTOs (don't expose entities)
- [ ] Global exception handling
- [ ] Consistent error messages (don't leak information)
- [ ] Comprehensive logging (but not sensitive data)
- [ ] Unit and integration tests

✅ **User Experience**
- [ ] Clear error messages
- [ ] Proper HTTP status codes
- [ ] Token refresh before expiration
- [ ] Graceful handling of expired tokens

---

## Conclusion

You now understand:

- ✅ Spring Security architecture and filter chain
- ✅ Every bean's purpose and initialization order
- ✅ Complete registration and login flows
- ✅ JWT generation, signing, and structure
- ✅ Token validation and consumption
- ✅ Refresh token mechanism
- ✅ SecurityContext and thread-local storage
- ✅ Token revocation strategies
- ✅ Every quirk, gotcha, and edge case
- ✅ Performance optimization strategies

**Next steps**:
1. Implement token revocation
2. Add refresh token rotation
3. Set up Redis caching
4. Add comprehensive logging
5. Write integration tests
6. Deploy with environment-based secrets

**Remember**: Security is not a feature, it's a continuous process. Keep your dependencies updated, monitor for vulnerabilities, and always question "what if an attacker does X?"

Happy coding! 🚀🔐
