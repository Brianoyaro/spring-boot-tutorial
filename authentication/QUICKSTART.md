# Quick Start Guide - JWT Authentication

Follow these steps to get your JWT authentication service up and running in minutes.

## 1. Prerequisites Check

Ensure you have these installed:
```bash
java -version   # Should show Java 21
mvn -version    # Should show Maven 3.6+
mysql --version # Should show MySQL 8.0+
```

## 2. Database Setup (2 minutes)

```sql
-- Login to MySQL
mysql -u root -p

-- Create database
CREATE DATABASE auth_db;

-- Verify
SHOW DATABASES;

-- Exit
exit;
```

## 3. Configure Application (1 minute)

Edit `src/main/resources/application.properties`:

```properties
# Update these lines with your MySQL credentials
spring.datasource.username=root
spring.datasource.password=your_mysql_password

# Optional: Generate a new JWT secret key for production
# Run: openssl rand -base64 64
# Then replace jwt.secret.key value
```

## 4. Build & Run (2 minutes)

```bash
# Navigate to project directory
cd /home/brian/Desktop/authentication

# Clean and build
./mvnw clean install

# Run the application
./mvnw spring-boot:run
```

Wait for: `Started AuthenticationApplication in X seconds`

## 5. Test the API (3 minutes)

### Step 1: Register a user

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

**Expected Response:**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 900000
}
```

**Copy the `accessToken` value!**

### Step 2: Access protected endpoint

```bash
# Replace YOUR_ACCESS_TOKEN with the token from step 1
curl -X GET http://localhost:8080/api/user/me \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

**Expected Response:**
```json
{
  "id": 1,
  "email": "test@example.com",
  "firstName": "Test",
  "lastName": "User",
  "role": "USER",
  "createdAt": "2026-02-26T...",
  "lastLogin": "2026-02-26T..."
}
```

✅ **Success!** Your JWT authentication is working!

## 6. Next Steps

### Create an Admin User

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "admin@example.com",
    "password": "admin123",
    "firstName": "Admin",
    "lastName": "User",
    "role": "ADMIN"
  }'
```

### Test Admin Endpoint

```bash
# Login as admin first, then use the admin token
curl -X GET http://localhost:8080/api/admin/dashboard \
  -H "Authorization: Bearer ADMIN_ACCESS_TOKEN"
```

### Test Token Refresh

```bash
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "YOUR_REFRESH_TOKEN"
  }'
```

## 7. Using with Frontend Applications

### JavaScript/React Example

```javascript
// Register
const response = await fetch('http://localhost:8080/api/auth/register', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    email: 'user@example.com',
    password: 'password123',
    firstName: 'John',
    lastName: 'Doe',
    role: 'USER'
  })
});

const { accessToken, refreshToken } = await response.json();

// Store tokens (in memory, not localStorage!)
sessionStorage.setItem('accessToken', accessToken);

// Make authenticated request
const userResponse = await fetch('http://localhost:8080/api/user/me', {
  headers: {
    'Authorization': `Bearer ${accessToken}`
  }
});

const user = await userResponse.json();
console.log(user);
```

### Angular Example

```typescript
// auth.service.ts
import { HttpClient, HttpHeaders } from '@angular/common/http';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private apiUrl = 'http://localhost:8080/api/auth';

  constructor(private http: HttpClient) {}

  register(user: any) {
    return this.http.post(`${this.apiUrl}/register`, user);
  }

  login(credentials: any) {
    return this.http.post(`${this.apiUrl}/login`, credentials);
  }

  getCurrentUser(token: string) {
    const headers = new HttpHeaders({
      'Authorization': `Bearer ${token}`
    });
    return this.http.get('http://localhost:8080/api/user/me', { headers });
  }
}
```

## 8. Troubleshooting

### Issue: "Access Denied" or 401 Unauthorized
- Check token is included in Authorization header
- Verify token hasn't expired (15 min for access token)
- Use refresh token to get new access token

### Issue: Database Connection Failed
```bash
# Check MySQL is running
sudo systemctl status mysql

# Verify credentials in application.properties
# Ensure database 'auth_db' exists
```

### Issue: Port 8080 Already in Use
```bash
# Change port in application.properties
server.port=8081

# Or kill process using port 8080
lsof -ti:8080 | xargs kill -9
```

### Issue: JWT Signature Verification Failed
- Ensure secret key hasn't changed
- Generate new key: `openssl rand -base64 64`
- Update application.properties

## 9. Production Checklist

Before deploying to production:

- [ ] Generate strong JWT secret key
- [ ] Enable HTTPS/SSL
- [ ] Configure proper CORS origins
- [ ] Set up database backups
- [ ] Implement rate limiting
- [ ] Add monitoring and logging
- [ ] Set up token revocation mechanism
- [ ] Configure environment variables for secrets
- [ ] Test with production-like data
- [ ] Set up CI/CD pipeline

## 10. Additional Resources

- Full Documentation: [JWT_AUTHENTICATION_README.md](JWT_AUTHENTICATION_README.md)
- API Test Requests: [api-test-requests.http](api-test-requests.http)
- Spring Security Docs: https://spring.io/projects/spring-security
- JJWT Library: https://github.com/jwtk/jjwt

---

**🎉 Congratulations!** You now have a production-ready JWT authentication system.

Need help? Check the logs: `tail -f logs/spring-boot-application.log`
