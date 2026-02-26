# System Setup Instructions

Before running the application, you need to install Java 21 and ensure MySQL is installed.

## Installing Java 21 (Ubuntu/Debian)

```bash
# Update package index
sudo apt update

# Install Java 21
sudo apt install openjdk-21-jdk -y

# Verify installation
java --version

# Expected output: openjdk 21.x.x

# Set JAVA_HOME (add to ~/.bashrc or ~/.zshrc for persistence)
export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
export PATH=$JAVA_HOME/bin:$PATH

# Verify JAVA_HOME
echo $JAVA_HOME
```

## Installing MySQL (If not already installed)

```bash
# Install MySQL Server
sudo apt install mysql-server -y

# Start MySQL service
sudo systemctl start mysql
sudo systemctl enable mysql

# Secure MySQL installation (optional but recommended)
sudo mysql_secure_installation

# Login to MySQL
sudo mysql -u root

# Create database and user
CREATE DATABASE auth_db;
CREATE USER 'authuser'@'localhost' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON auth_db.* TO 'authuser'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

## Update Application Configuration

After installing MySQL, update `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/auth_db
spring.datasource.username=authuser
spring.datasource.password=your_password
```

## Build and Run

```bash
# Navigate to project directory
cd /home/brian/Desktop/authentication

# Make mvnw executable (if needed)
chmod +x mvnw

# Build the project
./mvnw clean install -DskipTests

# Run the application
./mvnw spring-boot:run
```

## Alternative: Using Docker (Recommended for Development)

### Create docker-compose.yml

```yaml
version: '3.8'

services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: rootpassword
      MYSQL_DATABASE: auth_db
      MYSQL_USER: authuser
      MYSQL_PASSWORD: authpass
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql

  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/auth_db
      SPRING_DATASOURCE_USERNAME: authuser
      SPRING_DATASOURCE_PASSWORD: authpass
    depends_on:
      - mysql

volumes:
  mysql_data:
```

### Create Dockerfile

```dockerfile
FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Run with Docker

```bash
# Start services
docker-compose up -d

# View logs
docker-compose logs -f app

# Stop services
docker-compose down
```

## Verify Installation

```bash
# Check Java
java --version

# Check Maven (bundled with project)
./mvnw --version

# Check MySQL
sudo systemctl status mysql
```

## Troubleshooting

### Maven Command Not Found
The project includes Maven Wrapper (mvnw), so you don't need to install Maven separately.

### Permission Denied on mvnw
```bash
chmod +x mvnw
```

### MySQL Connection Refused
```bash
sudo systemctl start mysql
sudo systemctl status mysql
```

### Port 8080 Already in Use
```bash
# Find process using port 8080
sudo lsof -ti:8080

# Kill the process
sudo kill -9 $(sudo lsof -ti:8080)

# Or change application port in application.properties
server.port=8081
```

---

Once setup is complete, refer to [QUICKSTART.md](QUICKSTART.md) for running the application.
