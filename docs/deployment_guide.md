# GlobalTrade Logistics - Deployment Guide

This guide provides step-by-step instructions on how to build and deploy the GlobalTrade Logistics SCM platform onto a local GlassFish 7 server with a MySQL database.

## Prerequisites

- **Java Development Kit (JDK):** Version 17+
- **Apache Maven:** Version 3.8+
- **MySQL Server:** Version 8.0+
- **GlassFish Server:** Version 7.x (Jakarta EE 10 compatible)

## 1. Database Setup

1. Start your local MySQL server.
2. Create a new database named `globaltrade_scm`:
   ```sql
   CREATE DATABASE globaltrade_scm CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```
3. Import the initial database schema and seed data:
   ```bash
   mysql -u root -p globaltrade_scm < db/db.sql
   ```
   *(Note: You may need to adjust the username and provide a password depending on your local MySQL setup).*

## 2. GlassFish Resource Configuration

The application requires a JDBC Connection Pool and a JDBC Resource to communicate with the database. These are defined in `deploy/glassfish-resources.xml`.

1. Ensure GlassFish is running:
   ```bash
   asadmin start-domain
   ```
2. Review `deploy/glassfish-resources.xml`. If your MySQL credentials differ from the defaults (`user=root`, `password=8GFG0LMFDD1`), edit this file and update them accordingly before proceeding.
3. Deploy the resources to GlassFish:
   ```bash
   asadmin add-resources deploy/glassfish-resources.xml
   ```
4. Verify the connection pool can successfully connect to the database:
   ```bash
   asadmin ping-connection-pool SCMConnectionPool
   ```

## 3. JAAS Security Realm Setup

The application uses a custom JAAS realm named `supplyChainRealm` for role-based security.

You must configure this realm in GlassFish to back onto your database users, or a file-based realm for testing. (Refer to standard GlassFish documentation on configuring JDBC realms if you want it connected directly to the application database).

## 4. Building the Project

Use Maven to build the multi-module project. From the root directory (`globaltrade-scm/`), run:

```bash
mvn clean package
```

This will compile all modules, run unit tests, and package the final deployable Enterprise Archive (EAR) at `scm-ear/target/globaltrade-scm.ear`.

## 5. Deployment

Deploy the built EAR file to your local GlassFish instance:

```bash
asadmin deploy --contextroot scm scm-ear/target/globaltrade-scm.ear
```

Alternatively, you can deploy it by copying the `.ear` file to the GlassFish autodeploy directory:
```bash
cp scm-ear/target/globaltrade-scm.ear <glassfish_install_dir>/glassfish/domains/domain1/autodeploy/
```

## 6. Verification & Smoke Test

Once deployed, the application REST endpoints will be available under the `/scm` context root. 

You can perform a quick smoke test by hitting a health or status endpoint if implemented, or by checking the GlassFish `server.log` to confirm that the EJB Timers and Interceptors started successfully without exceptions.
