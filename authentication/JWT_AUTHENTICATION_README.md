# JWT Authentication Service

A production-ready JWT-based authentication service built with Spring Boot 3.2+ (4.0.3), Spring Security 6+, and Java 21.

## Features

- ✅ JWT-based stateless authentication
- ✅ User registration and login
- ✅ Access tokens (15 minutes expiry)
- ✅ Refresh tokens (7 days expiry)
- ✅ Password encryption with BCrypt
- ✅ Role-based access control (USER, ADMIN)
- ✅ CORS configuration for SPAs
- ✅ IP/device metadata tracking
- ✅ Issuer and audience validation
- ✅ Global exception handling
- ✅ Protected endpoints demonstration

## Technology Stack

- **Spring Boot**: 4.0.3
- **Spring Security**: 6+
- **Java**: 21
- **JWT Library**: JJWT 0.13.0
- **Database**: MySQL
- **Build Tool**: Maven

## Project Structure

```
src/main/java/com/example/authentication/
├── config/
│   ├── JwtAuthenticationFilter.java  # JWT filter for request interception
│   └── SecurityConfig.java           # Spring Security configuration
├── controller/
│   ├── AdminController.java          # Admin-only endpoints
│   ├── AuthenticationController.java # Login/Register/Refresh endpoints
│   └── UserController.java           # User profile endpoints
├── dto/
│   ├── AuthenticationRequest.java
│   ├── AuthenticationResponse.java
│   ├── RefreshTokenRequest.java
│   └── RegisterRequest.java
├── entity/
│   ├── Role.java                     # User roles enum
│   └── User.java                     # User entity with UserDetails
├── exception/
│   └── GlobalExceptionHandler.java   # Global error handling
├── repository/
│   └── UserRepository.java           # JPA repository
└── service/
    ├── AuthenticationService.java    # Authentication business logic
    ├── CustomUserDetailsService.java # UserDetailsService implementation
    └── JwtService.java               # JWT generation and validation
```

## Configuration

### Database Setup

1. Create a MySQL database:
```sql
CREATE DATABASE auth_db;
```

2. Update `application.properties` with your database credentials:
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/auth_db
spring.datasource.username=your_username
spring.datasource.password=your_password
```

### JWT Configuration

The JWT settings are configured in `application.properties`:

```properties
# JWT secret key (base64 encoded - generate your own for production)
jwt.secret.key=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970

# Access token expiration: 15 minutes (900000 ms)
jwt.access.token.expiration=900000

# Refresh token expiration: 7 days (604800000 ms)
jwt.refresh.token.expiration=604800000

# Issuer and audience for production validation
jwt.issuer=authentication-service
jwt.audience=authentication-client
```

**⚠️ Security Note**: Generate a strong secret key for production using:
```bash
openssl rand -base64 64
```

## API Endpoints

### Authentication Endpoints (Public)

#### 1. Register New User
```http
POST /api/auth/register
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "securePassword123",
  "firstName": "John",
  "lastName": "Doe",
  "role": "USER"
}
```

**Response:**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 900000
}
```

#### 2. Login
```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "securePassword123"
}
```

**Response:** Same as registration response

#### 3. Refresh Token
```http
POST /api/auth/refresh
Content-Type: application/json

{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Response:**
```json
{
  "accessToken": "new_access_token...",
  "refreshToken": "same_refresh_token...",
  "tokenType": "Bearer",
  "expiresIn": 900000
}
```

#### 4. Health Check
```http
GET /api/auth/health
```

### Protected Endpoints (Requires Authentication)

Include JWT token in the `Authorization` header:
```
Authorization: Bearer <access_token>
```

#### 5. Get Current User Profile
```http
GET /api/user/me
Authorization: Bearer <access_token>
```

**Response:**
```json
{
  "id": 1,
  "email": "user@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "role": "USER",
  "createdAt": "2026-02-26T10:30:00",
  "lastLogin": "2026-02-26T15:45:00"
}
```

#### 6. Protected Profile Endpoint
```http
GET /api/user/profile
Authorization: Bearer <access_token>
```

### Admin Endpoints (Requires ADMIN Role)

#### 7. Admin Dashboard
```http
GET /api/admin/dashboard
Authorization: Bearer <admin_access_token>
```

#### 8. Admin Statistics
```http
GET /api/admin/stats
Authorization: Bearer <admin_access_token>
```

## Running the Application

### Prerequisites
- Java 21
- Maven 3.6+
- MySQL 8.0+

### Steps

1. **Clone and navigate to the project:**
```bash
cd /home/brian/Desktop/authentication
```

2. **Build the project:**
```bash
./mvnw clean install
```

3. **Run the application:**
```bash
./mvnw spring-boot:run
```

The application will start on `http://localhost:8080`

## Testing with cURL

### Register a new user:
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "password123",
    "firstName": "Test",
    "lastName": "User",
    "role": "USER"
  }'
```

### Login:
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "password123"
  }'
```

### Access protected endpoint:
```bash
curl -X GET http://localhost:8080/api/user/me \
  -H "Authorization: Bearer <your_access_token>"
```

### Refresh token:
```bash
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "<your_refresh_token>"
  }'
```

## Security Best Practices Implemented

### 2025-2026 Security Standards

1. **Short-lived Access Tokens**: 15-minute expiration for access tokens
2. **Long-lived Refresh Tokens**: 7-day expiration for refresh tokens
3. **Stateless Authentication**: No server-side session storage
4. **CSRF Protection**: Disabled for stateless JWT (appropriate for APIs)
5. **Password Encryption**: BCrypt with strong hashing
6. **Issuer/Audience Validation**: Validates JWT claims in production
7. **IP Tracking**: Monitors IP changes during token refresh for anomaly detection
8. **CORS Configuration**: Properly configured for SPA integration
9. **Role-Based Access Control**: Fine-grained authorization with Spring Security

### Production Recommendations

1. **Enable HTTPS**: Always use HTTPS in production
2. **Secure Secret Key**: Use a strong, environment-specific secret key
3. **Token Revocation**: Implement database or Redis-based token blacklisting
4. **Rate Limiting**: Add rate limiting to authentication endpoints
5. **HTTP-Only Cookies**: Consider storing refresh tokens in HTTP-only cookies
6. **Device Fingerprinting**: Add device metadata validation during refresh
7. **Audit Logging**: Log all authentication events
8. **Multi-Factor Authentication**: Consider adding MFA for sensitive operations

## Token Storage Recommendations

### Frontend Best Practices

1. **Access Token**: Store in memory (JavaScript variable)
2. **Refresh Token**: Store in HTTP-only cookie or secure storage
3. **Never store tokens in localStorage** (vulnerable to XSS attacks)

## Common Issues

### Database Connection Error
Ensure MySQL is running and credentials are correct in `application.properties`

### JWT Signature Verification Failed
Ensure the secret key is base64-encoded and consistent across restarts

### CORS Errors
Update allowed origins in `SecurityConfig.corsConfigurationSource()`

## License

This project is for educational and commercial use.

## Support

For issues or questions, please refer to the official documentation:
- Spring Security: https://spring.io/projects/spring-security
- JJWT: https://github.com/jwtk/jjwt

---

**Built with ❤️ using Spring Boot and JWT**
