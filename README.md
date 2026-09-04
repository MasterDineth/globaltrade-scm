# GlobalTrade Logistics — Supply Chain Management System

A modernized Jakarta EE 10 / GlassFish 7.x supply chain management platform built for GlobalTrade Logistics Corporation. 

This project demonstrates enterprise Java capabilities including:
- **EJB Timer Service** (declarative and programmatic)
- **Interceptor-based** cross-cutting concerns
- **Container- and bean-managed transactions** (CMT/BMT)
- **JAAS-based role security**
- **Structured exception handling** with bounded-retry/dead-letter recovery
- **Split multi-module deployment packaging**

## Module Structure

The project is structured as a multi-module Maven build to cleanly separate concerns:

```text
globaltrade-scm/
├── scm-common/    Shared DTOs and enums (no dependency on EJB or Web modules)
├── scm-ejb/       Business tier: entities, exceptions, security (JAAS), interceptors, monitoring, timers, services
├── scm-web/       JAX-RS REST facade over the EJB local interfaces
├── scm-ear/       Deployable EAR assembly (bundles scm-ejb + scm-web)
├── db/            Database definitions and seed data
├── deploy/        GlassFish-side resource definitions (glassfish-resources.xml)
└── docs/          Additional project documentation and guides
```

The `scm-ejb` module additionally produces a thin `scm-ejb-client.jar` (remote interfaces and DTOs only) for out-of-EAR carrier or vendor integration clients.

## Quick Start

A comprehensive deployment walkthrough is provided in the [`docs/deployment_guide.md`](docs/deployment_guide.md). The short version of the build and deployment process is as follows:

1. Import the database schema from `db/db.sql`.
2. Configure your server resources using `deploy/glassfish-resources.xml`.
3. Build the project using Maven:
   ```bash
   mvn -pl scm-common,scm-ejb,scm-web,scm-ear -am clean package
   ```
4. Deploy the generated EAR:
   ```bash
   asadmin deploy --contextroot scm scm-ear/target/globaltrade-scm.ear
   ```

## Architecture Notes

This is a reference implementation built to demonstrate end-to-end architectural patterns for supply-chain systems. It features a working shipment-tracking, inventory, vendor-performance, and customs-compliance domain.

Key architectural choices include utilizing `READ COMMITTED` isolation for hot paths to avoid gap-locking overhead under concurrent writers, while relying on `@Version` optimistic locking to prevent lost updates, ensuring robustness in globally-distributed deployments.
