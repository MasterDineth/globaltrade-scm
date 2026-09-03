# GlobalTrade Logistics — Supply Chain Management System

A Jakarta EE 10 / GlassFish 7 supply chain management platform built for GlobalTrade
Logistics Corporation's legacy-to-enterprise-Java modernization project (see
`assignment_guide.txt`). Demonstrates EJB Timer Service (declarative and
programmatic), interceptor-based cross-cutting concerns, container- and
bean-managed transactions, JAAS-based role security, structured exception handling
with bounded-retry/dead-letter recovery, and split multi-module deployment
packaging, applied to a working shipment-tracking, inventory, vendor-performance,
and customs-compliance domain.

## Documentation

| Document | Contents |
|---|---|
| [`docs/CRITICAL_ANALYSIS.md`](docs/CRITICAL_ANALYSIS.md) | The core deliverable: critical analysis of every timer, interceptor, transaction, security, EJB-practice, exception-handling, and deployment decision in this codebase, organized around the assignment's seven design-requirement areas. |
| [`docs/NFR_ANALYSIS.md`](docs/NFR_ANALYSIS.md) | Security, performance, reliability, and trade-compliance analysis against the assignment's non-functional requirements — including explicitly-flagged gaps and proposed (not-yet-built) mechanisms where the brief asks for more than this reference implementation covers. |
| [`docs/DEPLOYMENT_GUIDE.md`](docs/DEPLOYMENT_GUIDE.md) | Step-by-step build, database, GlassFish realm/resource setup, deployment, smoke test, and test-execution instructions. |

## Module structure

```
globaltrade-scm/
├── scm-common/    Shared DTOs and enums (no dependency on scm-ejb or scm-web)
├── scm-ejb/       Business tier: entities, exceptions, security (JAAS), interceptors,
│                  monitoring, session beans (service/local + service/remote),
│                  timers, exception recovery
├── scm-web/       JAX-RS REST facade over the EJB local interfaces
├── scm-ear/       Deployable EAR assembly (bundles scm-ejb + scm-web)
├── db/            MySQL DDL and seed data (schema.sql)
├── deploy/        GlassFish-side resource definitions (glassfish-resources.xml)
└── docs/          The three documents above
```

`scm-ejb` additionally produces a thin `scm-ejb-client.jar` (remote interfaces and
DTOs only, no entities/security/implementation classes) for out-of-EAR carrier or
vendor integration clients — see
[`CRITICAL_ANALYSIS.md`, "Remote vs. local interface selection"](docs/CRITICAL_ANALYSIS.md#remote-vs-local-interface-selection-for-carrier-and-vendor-system-integration).

## Quick start

Full detail in [`docs/DEPLOYMENT_GUIDE.md`](docs/DEPLOYMENT_GUIDE.md); the short
version:

```bash
mysql -u root -p < db/schema.sql
# ...configure deploy/glassfish-resources.xml and the supplyChainRealm JAAS realm...
mvn -pl scm-common,scm-ejb,scm-web,scm-ear -am clean package
asadmin deploy --contextroot scm scm-ear/target/globaltrade-scm.ear
```

Demo credentials (real, working PBKDF2 hashes seeded by `db/schema.sql`, one per
role) are listed in that file's header comment — change all of them before any
non-local deployment.

## A note on two phrases in the original assignment brief

The brief's Technology Stack section reads "Database: MySQL with **medical data**
transaction coordination" and "Build Tool: Maven with multi-module structure for
**clinical applications**." Both read as leftover artifacts from a different
assignment template — nothing else in the brief mentions healthcare, and every
other requirement is consistently supply-chain/logistics-domain. This project
treats both as supply-chain-domain requirements (transaction coordination for
*logistics* data; a multi-module structure for *supply chain* applications) rather
than implementing anything healthcare-specific, on the assumption that was the
intended reading.

## Scope notes

This is a reference implementation built to demonstrate the architectural patterns
the assignment asks for end-to-end and working together (see the BMT order
workflow in `OrderProcessingServiceBean`, which alone exercises CMT/BMT
interplay, `MANDATORY` transaction attributes, `@RunAs` identity propagation, and
three different services' interceptor bindings, in one call chain) — it is not a
production-hardened, feature-complete TMS. `docs/NFR_ANALYSIS.md` explicitly names
what is and is not built for each non-functional requirement area, including
concrete proposals for closing the gaps it identifies (per-vendor data scoping,
data-at-rest encryption, automated compliance reporting, and others) rather than
leaving them unaddressed or implicit.
