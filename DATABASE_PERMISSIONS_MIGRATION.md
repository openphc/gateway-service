# Database-Backed Permissions Migration Guide

This document describes the migration from YAML-based permissions to database-backed permissions in the Gateway Service.

## 🎯 Overview

The gateway service now loads permission configurations from a PostgreSQL database instead of `application.yml`. This enables:
- ✅ **Dynamic permission management** - Update permissions without restarting the service
- ✅ **Centralized configuration** - Single source of truth in the database
- ✅ **Easier maintenance** - Add/modify permissions via SQL or API
- ✅ **Audit trail** - Track permission changes with timestamps
- ✅ **Scalability** - Share permissions across multiple gateway instances

---


### Components Added

1. **`ApiPermission.java`** - Entity representing database table
2. **`ApiPermissionRepository.java`** - R2DBC reactive repository
3. **`DatabasePermissionLoader.java`** - Service to load permissions on startup
4. **Database configuration** - R2DBC connection settings

---

## 📦 Database Schema

### Table: `api_permissions`

```sql
CREATE TABLE api_permissions (
	id BIGSERIAL PRIMARY KEY,
	permission_name VARCHAR(100) NOT NULL,
	http_method VARCHAR(10) NOT NULL,
	uri_pattern VARCHAR(255) NOT NULL,
	description TEXT,
	resource_category VARCHAR(50),
	created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
	updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
	CONSTRAINT unique_permission_method_uri UNIQUE (permission_name, http_method, uri_pattern)
);
```

### Indexes
- `idx_permission_name` - Fast lookups by permission name
- `idx_resource_category` - Group by resource category

### Trigger
- Automatically updates `updated_at` timestamp on row modification

---

## 🚀 Setup Instructions

### Step 1: Database Configuration

The database connection is configured in `application.yml`:

```yaml
spring:
r2dbc:
	url: ${DATABASE_URL:r2dbc:postgresql://localhost:5432/admin_service}
	username: ${DATABASE_USERNAME:opensrp}
	password: ${DATABASE_PASSWORD:opensrp123}
```

**Environment Variables:**
```bash
DATABASE_URL=r2dbc:postgresql://localhost:5432/admin_service
DATABASE_USERNAME=opensrp
DATABASE_PASSWORD=opensrp123
```

### Step 2: Run Database Migration

Execute the SQL migration script to create the table and populate initial data:

```bash
# Using psql
psql -U opensrp -d admin_service -f src/main/resources/db/migration/V1__create_api_permissions_table.sql

# Or using connection string
psql "postgresql://opensrp:opensrp123@localhost:5432/admin_service" -f src/main/resources/db/migration/V1__create_api_permissions_table.sql
```

### Step 3: Verify Data

```sql
-- Check total permissions
SELECT COUNT(*) FROM api_permissions;

-- View permissions by category
SELECT resource_category, COUNT(*)
FROM api_permissions
GROUP BY resource_category
ORDER BY resource_category;

-- View all USER permissions
SELECT permission_name, http_method, uri_pattern, description
FROM api_permissions
WHERE permission_name LIKE 'USER_%'
ORDER BY permission_name, http_method;
```

### Step 4: Start the Gateway Service

```bash
# Build the project
./gradlew build

# Run the service
./gradlew bootRun

# Or with environment variables
DATABASE_URL=r2dbc:postgresql://localhost:5432/admin_service \
DATABASE_USERNAME=opensrp \
DATABASE_PASSWORD=opensrp123 \
./gradlew bootRun
```

---

## 🔄 How It Works

### Startup Flow

1. **Application starts** → `GatewayServiceApplication.main()`
2. **Spring Boot initializes** → R2DBC connection established
3. **`ApplicationReadyEvent` fired** → `DatabasePermissionLoader.loadPermissionsFromDatabase()`
4. **Permissions loaded** → Converted to `PermissionConfig` format
5. **Authorization active** → `AuthorizationService` uses loaded permissions

### Runtime Flow

```
Request → JwtValidationFilter
	→ Extract JWT
	→ Validate Token
	→ Extract Permissions (from JWT)
	→ AuthorizationService.isAuthorized()
	→ Check against database-loaded permissions
	→ Allow/Deny
```

---

## 📊 Data Examples

### Sample Permission Entries

```sql
-- USER_READ permission (2 rules)
INSERT INTO api_permissions (permission_name, http_method, uri_pattern, description, resource_category) VALUES
('USER_READ', 'GET', '/admin/users/*', 'View specific user details', 'Users'),
('USER_READ', 'GET', '/admin/users', 'List all users', 'Users');

-- USER_CREATE_UPDATE permission (3 rules)
INSERT INTO api_permissions (permission_name, http_method, uri_pattern, description, resource_category) VALUES
('USER_CREATE_UPDATE', 'POST', '/admin/users', 'Create new user', 'Users'),
('USER_CREATE_UPDATE', 'PUT', '/admin/users/*', 'Update user details', 'Users'),
('USER_CREATE_UPDATE', 'PATCH', '/admin/users/*', 'Partially update user', 'Users');
```
---

## 🔧 Adding New Permissions

### Option 1: Direct SQL Insert

```sql
INSERT INTO api_permissions (permission_name, http_method, uri_pattern, description, resource_category)
VALUES ('REPORT_READ', 'GET', '/admin/reports/*', 'View reports', 'Reports');
```

### Option 2: Bulk Insert

```sql
INSERT INTO api_permissions (permission_name, http_method, uri_pattern, description, resource_category) VALUES
('REPORT_READ', 'GET', '/admin/reports', 'List all reports', 'Reports'),
('REPORT_READ', 'GET', '/admin/reports/*', 'View specific report', 'Reports'),
('REPORT_CREATE_UPDATE', 'POST', '/admin/reports', 'Create report', 'Reports'),
('REPORT_CREATE_UPDATE', 'PUT', '/admin/reports/*', 'Update report', 'Reports'),
('REPORT_DELETE', 'DELETE', '/admin/reports/*', 'Delete report', 'Reports');
```

### After Adding Permissions

**⚠️ Important:** Currently, you need to restart the gateway service to load new permissions.

```bash
# Restart the service
./gradlew bootRun
```
