# Critical Analysis: GlobalTrade Supply Chain Management System Modernization

This document is the critical-analysis deliverable for the GlobalTrade Logistics
Corporation supply chain modernization project. It is organized around the seven
"Comprehensive Design Requirements" areas from the assignment brief, and every
subsection below is cross-referenced by name from the source code comment that
promised it — the intent is that a reader following a `See docs/CRITICAL_ANALYSIS.md,
"..."` pointer from anywhere in `scm-ejb`, `scm-web`, or `scm-ear` lands on the
matching heading directly.

Where a design decision has a concrete class or file behind it, this document names
it; the goal is analysis grounded in an actual working implementation, not abstract
discussion of options never built.

## Table of Contents

1. [Supply Chain Timer Services Integration and Management](#1-supply-chain-timer-services-integration-and-management)
2. [Logistics Interceptor Architecture and Implementation](#2-logistics-interceptor-architecture-and-implementation)
3. [Logistics Transaction Demarcation and Management](#3-logistics-transaction-demarcation-and-management)
4. [Global Trade Security Architecture and Authorization](#4-global-trade-security-architecture-and-authorization)
5. [EJB Best Practices and Supply Chain Optimization](#5-ejb-best-practices-and-supply-chain-optimization)
6. [Supply Chain Exception Handling and System Resilience](#6-supply-chain-exception-handling-and-system-resilience)
7. [Supply Chain Component Organization and Deployment Strategy](#7-supply-chain-component-organization-and-deployment-strategy)

---

## 1. Supply Chain Timer Services Integration and Management

The platform uses six timer beans covering all five categories the brief calls out
— shipment status updates, inventory monitoring, vendor performance assessment,
customs deadline tracking, and route optimization — deliberately split across two
different EJB Timer Service creation styles rather than using one style uniformly.

### Programmatic vs. declarative timer creation for supply chain workflows

Three timers are **declarative** (`@Schedule`): `InventoryLevelMonitorTimerBean`
(hourly), `RouteOptimizationTimerBean` (nightly, weekdays), and
`ShipmentStatusUpdateTimerBean` (every 15 minutes). All three share a defining
property: the schedule itself is a fixed, deployment-time business rule — "check
inventory levels hourly" is true regardless of which SKUs exist or how many
shipments are active. A declarative timer lets the container own the entire timer
lifecycle: creation happens automatically at deployment (once, idempotently — the
container will not create a second timer for the same `@Schedule` on redeploy to
the same persistent store), and the schedule survives server restarts without any
bean code re-registering it.

Two timers are **programmatic**: `CustomsDeadlineTimerBean.scheduleDeadlineReminder`
(called by `CustomsDocumentationServiceBean.fileDocument` at the moment a document
with a business-specific deadline is created) and
`VendorPerformanceAssessmentTimerBean`'s calendar timer (created in `@PostConstruct`
via `TimerService.createCalendarTimer`, guarded by an idempotency check against
`TimerService.getTimers()` before creating a duplicate). The distinguishing property
here is the inverse of the declarative case: the schedule is *data*, not a fixed
rule. A customs document's submission deadline is a value that does not exist until
a specific shipment's specific document is filed — there is no `@Schedule`
expression that could encode "three hours before whatever `submissionDeadline` this
particular row ends up having." Programmatic creation via `TimerService.createSingleActionTimer`,
parameterized with the document's actual deadline and the document's id as the
timer's `info` payload, is the only mechanism that can express a schedule computed
from runtime data.

The general rule this project follows: **if the schedule can be written as a fixed
expression at design time, use `@Schedule`; if the schedule is itself a value read
from the database, the timer must be created programmatically at the point that
value becomes known.** Mixing the two within one system (rather than forcing
everything into one style) is itself the correct architectural choice — a
`@Schedule`-only design would have no way to express a per-document deadline
reminder, and an all-programmatic design would mean hand-rolling deployment-time
timer bootstrapping and idempotent re-registration logic that `@Schedule` already
provides for free.

### Timer persistence and reliability in globally distributed logistics environments

Every timer in this system is created with `persistent = true` (the default for
`@Schedule`, and explicit in the programmatic `createSingleActionTimer`/
`createCalendarTimer` calls), meaning the container durably records the timer in
its own persistent store and will fire a missed timeout on restart if the server
was down when it should have fired. For a 24/7 global logistics operation, a
non-persistent timer that simply vanishes on a server restart — silently dropping,
say, a customs deadline reminder — is not an acceptable failure mode.

Persistence alone does not solve the harder problem in a **clustered** deployment
(the actual target topology, given the 99.9% uptime requirement): GlassFish's
default persistent-timer behavior does not itself provide cluster-wide leader
election, so if the EAR is deployed to more than one clustered instance, each
instance's timer service can, depending on cluster configuration, attempt to fire
the *same* logical timer independently — a straightforward path to duplicate
customs reminders or double-counted inventory checks. `TimerCoordinatorSingleton`
addresses the visibility half of this problem: an `@Singleton @Startup` bean that
writes a per-instance heartbeat row on startup, giving operators (and, in a fuller
implementation, other application code) a place to observe which cluster members
are alive and processing timers, rather than discovering a duplicate-firing problem
only after it has already caused a duplicate customs filing. It is explicitly
**not** a full leader-election protocol — that is genuinely infrastructure-level
concern (a GlassFish cluster's own timer-service clustering configuration, or an
external coordinator like a distributed lock service) that application code alone
should not try to reimplement badly. `VendorPerformanceAssessmentTimerBean`'s
`scheduleAssessmentTimer()` idempotency check (querying existing timers by `info`
tag before creating a new one) is the concrete, narrower mitigation actually
implemented at the bean level: it guards specifically against *this bean* creating
a second calendar timer for itself across redeploys, which is a correctness
property this codebase can and does guarantee, distinct from the cluster-wide
firing question `TimerCoordinatorSingleton` merely surfaces.

### Timeout callback method optimization for time-critical shipment alerts

`ShipmentStatusUpdateTimerBean.pollCarrierSystemsAndUpdateStatuses` is the
time-critical path (a 15-minute cycle directly feeding customer-visible tracking
status), and its design reflects several optimizations layered together:

- **Per-shipment failure isolation.** Each shipment's carrier query and status
  update is wrapped in its own `try/catch`, so one unreachable carrier cannot abort
  the whole batch — the loop continues to the next shipment rather than the entire
  timeout callback failing and rolling back updates already applied to other
  shipments in the same run.
- **Bounded retry before falling back to the next cycle.** Each carrier query goes
  through `ExceptionRecoveryManager.executeWithRetry` (three attempts, short linear
  backoff) before being treated as a failure for this cycle — see
  ["Recovery strategies for different supply chain failure scenarios"](#recovery-strategies-for-different-supply-chain-failure-scenarios)
  below for why the retry is bounded rather than unbounded.
- **`REQUIRES_NEW` on the actual status write** (`ShipmentTrackingServiceBean.recordCarrierStatusUpdate`),
  so each shipment's update commits independently rather than all sharing one
  timer-wide transaction that a later failure could roll back in its entirety.
- **N+1 avoidance via fetch-joining.** The timer's own `findActiveShipments()` query
  uses `LEFT JOIN FETCH s.carrier` specifically because the loop body calls
  `shipment.getCarrier()` for every row — without the fetch join, a batch of, say,
  500 active shipments would issue 500 additional lazy-load queries for `carrier`
  alone.
- **`@Interceptors(PerformanceMonitoringInterceptor.class)`** on the callback
  method itself, so a cycle that starts running long (approaching or exceeding the
  15-minute interval to the *next* scheduled firing) is visible in
  `MetricsRegistry`'s slow-invocation counters before it becomes an operational
  incident, not after.

### Timer service performance implications for supply chain efficiency and customer satisfaction

The clearest customer-facing latency implication is the choice, in `persistence.xml`,
to **disable EclipseLink's shared (L2) entity cache** (`eclipselink.cache.shared.default=false`).
This is a direct trade of some read performance for correctness: in a
multi-node cluster, a shared L2 cache scoped to a single JVM can serve a stale
`Shipment.status` on node B for some time after node A's timer callback committed
a fresh status — exactly the kind of staleness that would make "check my shipment
status" show outdated information to a customer immediately after a carrier update
was already recorded. Re-enabling the cache with EclipseLink's RMI or JMS-based
cache coordination is a legitimate future optimization once the operational cost of
running that coordination infrastructure is justified by read volume — it is called
out explicitly as future work rather than silently left as an implicit assumption.

At the scheduling-interval level, the 15-minute shipment-poll and hourly
inventory-check intervals are themselves a performance/timeliness trade-off: tighter
intervals reduce the worst-case staleness a customer or warehouse manager could see,
at the cost of more carrier-API calls and more database load from more frequent
`findBelowReorderThreshold()`/`findActiveShipments()` scans. Neither interval is
derived from a load test in this reference implementation (there is no representative
production traffic to test against yet) — both are defensible starting points that
should be revisited against real carrier-API rate limits and real database
contention once the system is under actual load, and both are centralized in a
single `@Schedule` annotation each, making that revision a one-line change rather
than a scattered one.

---

## 2. Logistics Interceptor Architecture and Implementation

Four interceptors cover the cross-cutting concerns the brief names: general audit
logging, authorization-sensitive audit logging, performance monitoring, and
vendor-data validation. All four are bound using the classic EJB-specification
mechanism (`@Interceptors`, plus one XML default-interceptor binding) rather than
CDI's `@InterceptorBinding` annotations — see
["Deployment descriptor optimization"](#deployment-descriptor-management-for-logistics-workflows)
below for why.

### Interceptor lifecycle management in global logistics contexts

An interceptor instance's lifecycle is tied to the target bean instance it
intercepts, not managed independently — for a `@Stateless` target (every session
bean in this system except the one `@Singleton` timers), that means a fresh
interceptor instance per pooled bean instance, participating in the same
`@PostConstruct`/`@PreDestroy` lifecycle callbacks as the bean itself. This has a
direct, concrete implication for `AuditLoggingInterceptor` and
`VendorDataValidationInterceptor`: both are stateless with respect to any single
invocation (they read method parameters and container context on each call, never
accumulating state across calls), so pool-driven instance churn is harmless. It
would **not** be harmless for an interceptor that tried to accumulate
cross-invocation state (a naive request-counter incremented on an interceptor
instance field, for instance) — that state would be silently partitioned across
however many pooled instances exist and reset whenever the container recycles one,
producing numbers that look plausible but are wrong. `PerformanceMonitoringInterceptor`
avoids exactly this trap by delegating all counting to the injected
`MetricsRegistry` — a `@Singleton` with `@ConcurrencyManagement(BEAN)` — rather than
keeping any counter on the interceptor instance itself, so the metrics are correct
regardless of how many interceptor instances the pool happens to be cycling
through.

### Interceptor chain optimization for supply chain workflows

Binding order for any business method, from outermost to innermost, is: the one
XML-declared default interceptor (`AuditLoggingInterceptor`, bound via
`ejb-jar.xml`'s `<ejb-name>*</ejb-name>` wildcard), then class-level `@Interceptors`,
then method-level `@Interceptors` — the EJB-specification default, which this
project relies on rather than overriding via `<interceptor-order>`. Concretely, a
call to `CustomsDocumentationServiceBean.approveDocument` runs through:
`AuditLoggingInterceptor` (module default) → `VendorDataValidationInterceptor`
(class-level on this bean) → `SecurityAuditInterceptor` (method-level, this one
method only) → the business method body. This ordering is not arbitrary: audit
logging outermost means the audit record is written (or, on a validation failure,
*not* reached) around the full outcome of every inner interceptor and the business
method, giving the audit trail the widest possible view of "what actually happened
for this invocation," while the more specific, narrower-scoped checks
(vendor-data validation, then security-sensitive-operation logging) sit closer to
the business logic they are most relevant to.

Chain *length* is itself an optimization lever this project takes seriously:
`SecurityAuditInterceptor` and `PerformanceMonitoringInterceptor` are bound at the
method level specifically so that every business method does not pay for
interceptors it does not need — see the next subsection for the full method-vs-class
discussion. A bean with a long, unnecessarily broad interceptor chain pays chain
traversal cost (a handful of extra stack frames and, more significantly, whatever
I/O each interceptor performs — a database write for `AuditLoggingInterceptor`, a
`Logger` call for the others) on every single invocation whether or not that
invocation actually needed that concern addressed.

### Method-level vs. class-level interceptor strategies for different logistics processes

This project uses both strategies deliberately, not as an accident of two different
authors:

- **Class-level** (`VendorDataValidationInterceptor` on `VendorPerformanceServiceBean`
  and `CustomsDocumentationServiceBean`): chosen because *every* public method on
  these two beans either accepts a `Vendor` entity directly or a vendor id, so a
  uniform "validate before any business logic runs" policy is the bean's whole
  contract, not a property of any one method. Binding it at the class level also
  means a future method added to either bean automatically inherits the protection
  — a developer adding, say, `VendorPerformanceServiceBean.bulkImportVendors(...)`
  six months from now does not need to remember to annotate it; they would have to
  deliberately opt *out* (impossible with `@Interceptors`, short of restructuring
  the bean) to skip validation, which is the safer failure mode.
- **Method-level** (`SecurityAuditInterceptor` on `CustomsDocumentationServiceBean.approveDocument`
  only; `PerformanceMonitoringInterceptor` on `ShipmentTrackingServiceBean.trackShipment`,
  `ShipmentStatusUpdateTimerBean.pollCarrierSystemsAndUpdateStatuses`, and
  `RouteOptimizationTimerBean.recalculateRoutes`): chosen because these concerns
  are properties of *specific operations*, not of the beans that happen to host
  them. Not every method on `CustomsDocumentationServiceBean` is a
  legally-binding, security-sensitive action the way `approveDocument` specifically
  is — `findApproachingDeadlines`, a read-only query, has no business writing to
  the security-audit log stream on every call. Similarly, performance monitoring is
  applied only to the identified hot paths (shipment lookups, route optimization,
  the 15-minute carrier poll) rather than uniformly, because uniform application
  would mean paying `MetricsRegistry` interaction cost on low-volume administrative
  operations that were never a performance concern in the first place.

The general rule: **bind at the class level when the concern is an invariant of
everything the bean does; bind at the method level when the concern is a property
of specific operations that happens not to generalize to the whole bean.**
Defaulting everything to method-level bindings would be more "precise" on paper but
would reintroduce the exact risk class-level binding exists to close off — a
developer forgetting to annotate a new security- or validation-sensitive method.

### Interceptor performance impact

The most consequential performance decision among the four interceptors is
`AuditLoggingInterceptor`'s synchronous, same-transaction write to `AuditLogEntry`
on **every** business method in the whole EJB module (it is the one XML-bound
default interceptor, applying module-wide). This guarantees the audit record and
the business outcome it describes are atomically consistent — if the business
transaction rolls back, the audit row recording it (written within that same
transaction) rolls back with it, so there is never an audit log claiming an
operation succeeded when it did not, or vice versa. The cost is real: every single
business method invocation in the system now includes one additional `INSERT` and
pays whatever lock contention that table experiences under concurrent write load.
The alternative — firing the audit write asynchronously (a JMS-published event, or
a `REQUIRES_NEW`-attributed logging call, mirroring how `ExceptionRecoveryManager.recordFailure`
is written) — trades that same-transaction consistency guarantee away for lower
latency and less lock contention on the audit table, at the cost of a narrow window
where an audit record could be written for a business operation that, moments
later, is rolled back for an unrelated reason (e.g. a container crash between the
audit write and the business commit). For a customs/trade compliance audit trail,
where "does this audit log accurately reflect what actually happened" is closer to
a legal requirement than a nice-to-have, the synchronous, same-transaction write is
the correct default. If write-heavy production load ever makes the audit table a
measured bottleneck, the asynchronous alternative is the documented escape hatch —
but it should be adopted only after the synchronous approach is shown to actually
be the bottleneck, not pre-emptively.

`SecurityAuditInterceptor` and `PerformanceMonitoringInterceptor`'s method-level
scoping (discussed above) is itself the direct performance mitigation for those two
concerns specifically — narrow scope is what keeps their overhead off the hot path
of every method that does not need them.

---

## 3. Logistics Transaction Demarcation and Management

### Transaction attribute selection for different logistics scenarios

Every non-default `@TransactionAttribute` in this codebase was chosen for a
specific, stated reason rather than left at whatever the container would have
picked by inertia:

| Method | Attribute | Why |
|---|---|---|
| `ShipmentTrackingServiceBean.trackShipment` | `NOT_SUPPORTED` | Pure read; no reason to hold a JTA transaction (and the connection behind it) open for a lookup with nothing to protect. |
| `ShipmentTrackingServiceBean.recordCarrierStatusUpdate` | `REQUIRES_NEW` | Independently-committing inbound update; called in a loop from a timer where one shipment's failure must not affect any other shipment's already-applied update in the same run. |
| `ShipmentTrackingServiceBean.findActiveShipments` | `NOT_SUPPORTED` | Pure read, same reasoning as `trackShipment`. |
| `InventoryManagementServiceBean.reserveStock` | *(REQUIRED, default)* | Must participate in the caller's larger unit of work (typically `OrderProcessingServiceBean`'s order transaction) so a reservation is never left stranded if a later step in the same order fails. |
| `InventoryManagementServiceBean.replenishStock` | `REQUIRES_NEW` | A physical warehouse restock already happened in the real world by the time this is called; it must be durably recorded even if the broader transaction that triggered it (e.g. a batch run) later fails on an unrelated SKU. |
| `InventoryManagementServiceBean.getItem` / `checkAvailability` / `findBelowReorderThreshold` | `NOT_SUPPORTED` | Pure reads. |
| `CustomsDocumentationServiceBean.findApproachingDeadlines` | `NOT_SUPPORTED` | Pure read. |
| `CustomsDocumentationServiceBean.finalizeShipmentCustomsClearance` | `MANDATORY` | Must **never** run as its own standalone unit of work — exists specifically to be the last step inside `OrderProcessingServiceBean`'s bean-managed transaction, so shipment registration, inventory reservation and customs clearance commit or roll back together. |
| `VendorPerformanceServiceBean.getVendorPerformanceSummary` | `NOT_SUPPORTED` | Pure read. |
| `VendorPerformanceServiceBean.assessVendorAsync` | `REQUIRES_NEW` (explicit, though close to the effective default) | An `@Asynchronous` method runs on a container-managed thread with no caller transaction to propagate into it regardless. |
| `ExceptionRecoveryManager.recordFailure` | `REQUIRES_NEW` | The dead-letter row must survive even when the caller's own transaction is about to roll back — see [Section 6](#recovery-strategies-for-different-supply-chain-failure-scenarios). |
| `ExceptionRecoveryManager.findUnresolvedFailures` | `NOT_SUPPORTED` | Pure read. |

The pattern across every `NOT_SUPPORTED` read: none of these methods have
anything to protect transactionally (a single `SELECT`, or a handful of `SELECT`s
that do not need to be seen as one atomic unit by any other transaction), so
holding a JTA transaction — and the pooled database connection enlisted in it —
open for the duration is pure overhead. The pattern across every `REQUIRES_NEW`:
each protects a write that must survive independently of whatever the caller's
transaction ultimately does, whether because the write represents a real-world
fact that already happened (`replenishStock`), or because failure isolation across
a batch/loop is the point (`recordCarrierStatusUpdate`, `recordFailure`).
`MANDATORY` is used exactly once, and deliberately in the one place where the
correct behavior genuinely is "refuse to run standalone" rather than "silently
start a new transaction if none exists" (which is what `REQUIRED` would have done,
masking exactly the architectural mistake `MANDATORY` is meant to catch at
development time).

### Transaction isolation levels for concurrent global operations

The JDBC connection pool (`deploy/glassfish-resources.xml`) is configured for
**READ COMMITTED**, not InnoDB's own default of REPEATABLE READ. This is a
considered choice, not an oversight: this system's read-heavy hot paths
(`trackShipment`, `getVendorPerformanceSummary`) already run `NOT_SUPPORTED` —
effectively autocommit reads with no transaction-scoped snapshot to begin with —
while its writes are short, targeted, single-or-few-row updates protected by
`@Version` optimistic locking (`Shipment.version`, `InventoryItem.version`,
`Vendor.version`, `CustomsDocument.version`) rather than by long-held pessimistic
locks or by relying on REPEATABLE READ's snapshot semantics for correctness. Given
that, REPEATABLE READ's extra guarantees (a transaction sees a consistent snapshot
across multiple reads, InnoDB's gap-locking to prevent phantom rows) are not
actually load-bearing anywhere in this system's correctness story, while its
locking overhead — specifically gap locks, which can produce more lock contention
and more deadlocks under concurrent writers than READ COMMITTED — is a real cost
under the concurrent-global-operations load this platform is built for (multiple
regional warehouse operations and the automated timers all writing against
overlapping data at once). READ COMMITTED is the lighter-weight choice that still
satisfies every actual correctness requirement this system has, because those
requirements are enforced at the application/optimistic-locking layer, not at the
isolation-level layer.

### Distributed transaction handling across multiple supply chain systems

This platform's own JTA transactions are single-resource (one MySQL data source);
"distributed" in the assignment's sense — coordinating with the *external* systems
the business requirements name (carrier tracking APIs, customs authority gateways,
supplier portals) — is handled deliberately **outside** of two-phase commit rather
than by trying to enlist those external systems as XA resources in the same JTA
transaction. This is a considered trade-off, not a limitation overlooked: carrier
and customs-authority APIs are HTTP/REST services outside GlobalTrade's
administrative control, and XA requires a resource manager willing and able to
participate in prepare/commit/rollback — an assumption that does not hold for a
third party's public API. `ShipmentStatusUpdateTimerBean`'s design pattern (query
the external system for status, then commit the *local* database update as its own
transaction via `recordCarrierStatusUpdate`'s `REQUIRES_NEW`) is the standard,
correct response to that constraint: treat the external call as happening
*outside* any local transaction boundary, and make the local side of the
interaction resilient to the external call having succeeded-but-not-been-recorded
or failed-and-been-retried, rather than pretending a single atomic transaction
spanning both sides is achievable. `ExceptionRecoveryManager`'s bounded-retry and
dead-letter pattern is precisely the mechanism that makes this "outside 2PC"
approach safe in practice: an external call that fails does not corrupt local
state (nothing local was written before the retry-and-give-up decision), and a
call that is retried after a partial prior failure is naturally idempotent for
this system's specific operations (re-recording the same carrier status is a
no-op if the status has not actually changed again).

### Transaction performance optimization strategies with logistics data rollback scenarios

Beyond the attribute-level choices already covered, the two concrete rollback-scenario
optimizations in this codebase are:

1. **Per-shipment failure isolation in batch timer runs** (`ShipmentStatusUpdateTimerBean`):
   because each shipment's update runs in its own `REQUIRES_NEW` transaction, a
   rollback triggered by one bad shipment (an unhandled exception past the
   `try/catch`, or an `OptimisticLockException`) only ever discards *that*
   shipment's uncommitted work, never the whole batch's. The alternative design —
   one transaction wrapping the entire loop — would mean a single problematic
   shipment near the end of a 500-shipment run rolling back updates already applied
   to the other 499, which is both wasteful (their carrier-API calls, the most
   expensive part of the work, would have to be redone) and operationally opaque
   (an operator would see "the 3pm poll failed" rather than "shipment X failed, the
   other 499 succeeded").
2. **BMT's explicit rollback boundary in `OrderProcessingServiceBean`**: because
   this workflow spans three independently-transactional collaborators conditionally
   (the third, customs clearance, only participates for international shipments),
   `safeRollback()` gives this bean full, explicit control over exactly what gets
   rolled back and when — including checking `UserTransaction.getStatus()` before
   attempting a rollback, since some failure paths (`commit()` itself throwing
   `HeuristicRollbackException`) can leave no transaction associated with the
   thread at all. A CMT/`REQUIRED`-only composition of the same three steps would
   have rolled back correctly too (a runtime exception propagating up marks the
   ambient transaction for rollback automatically), but would have given this
   method no opportunity to decide, in code, whether the third step participates at
   all before the transaction is even opened.

---

## 4. Global Trade Security Architecture and Authorization

### Role-based access control implementation for logistics personnel

Six roles are declared, matching `UserRole` one-for-one:
`ADMIN`, `LOGISTICS_COORDINATOR`, `CUSTOMS_AGENT`, `WAREHOUSE_MANAGER`,
`VENDOR_REPRESENTATIVE`, `CUSTOMER` — covering the four groups the business
requirements name (logistics personnel, customs officials, vendors, and a
customer-facing portal role) plus a dedicated administrative role and a
warehouse-specific split out from "logistics personnel" generally, since warehouse
staff and coordinators have observably different, only partially overlapping
authorization needs in this system (compare `InventoryManagementServiceBean`'s
`@RolesAllowed` sets against `ShipmentTrackingServiceBean`'s).

Enforcement is **layered, deliberately redundant across tiers**, not merely
declared once. `web.xml`'s `security-constraint` on `/api/*` is intentionally
coarse — any of the six roles may reach the API at all — while the actual,
fine-grained "who can do what" decision is made by `@RolesAllowed` on the EJB
method each REST resource calls. This is not redundancy for its own sake: the web
constraint's job is only to guarantee a `Principal` exists before a request reaches
application code (so `@RolesAllowed`/`isCallerInRole` have something to evaluate at
all), while the EJB layer remains the single source of truth for the actual
authorization decision — including for callers that reach these EJB methods
**without** going through the web tier at all (an in-EAR local call from one
service bean to another, or a genuine remote EJB client using
`ShipmentTrackingServiceRemote`/`VendorPerformanceServiceRemote` directly). A web
constraint alone would leave those non-HTTP call paths completely unprotected;
`@RolesAllowed` at the EJB layer protects every call path uniformly.

Method-level `@RolesAllowed` overriding a class-level default is used precisely
once for a deliberate widening: `ShipmentTrackingServiceBean`'s class of operations
is generally restricted to internal staff, but `trackShipment` specifically also
allows `VENDOR_REPRESENTATIVE` and `CUSTOMER` — read-only tracking is exactly the
one capability those two external-facing roles should have, and the EJB
specification's rule that method-level annotations override class-level ones is
what makes expressing "this one method is broader than the rest of the bean" possible
without restructuring the bean into two separate beans.

### Session security and management strategies for global supply chain workflows

Authentication state in this system is entirely the JAAS `Subject` populated once
per login by `SupplyChainLoginModule`, carried forward by the container's own
session/security-context mechanism (HTTP session for the web tier, the RMI/IIOP
security context for remote EJB clients) — no custom session token, timeout, or
session-store code exists anywhere in this codebase, deliberately. Reimplementing
session management on top of what GlassFish (and the Servlet/EJB specifications)
already provide would be extra surface area with no corresponding benefit; the
container's session timeout configuration (`web.xml`/`glassfish-web.xml`, or the
domain-wide default) is the correct, single place to tune session lifetime for a
"logistics coordinator stepped away from their desk" scenario, rather than
duplicating that concern in application code.

`user-data-constraint`/`transport-guarantee=CONFIDENTIAL` in `web.xml` forces every
`/api/*` request over HTTPS, which matters specifically for **session security**
because `BASIC` authentication (configured in the same file) transmits credentials
base64-encoded, not encrypted, on every single request — CONFIDENTIAL transport is
not optional hardening here, it is the only thing standing between BASIC auth and
credentials traveling in effectively-plaintext over a network that, for a global
trade platform, cannot be assumed trusted end-to-end.

### Authentication mechanisms for different user types and emergency logistics scenarios

Every *interactive* user type (the six roles above) authenticates through the same
single mechanism — `SupplyChainLoginModule` against `system_user` — deliberately:
maintaining a second, parallel authentication path for any one role would be extra
attack surface and extra code to keep in sync with the primary path for no
corresponding benefit, since nothing about any of the six roles' authentication
*needs* to differ (they differ in authorization, which `@RolesAllowed` already
handles per-operation).

The one genuinely different "user type" this system authenticates is **not** a
human at all: automated components (the 15-minute carrier-status timer, the
bean-managed order-processing orchestrator) have no interactive login and
therefore no caller `Principal` of their own, yet both need to invoke methods
protected by `@RolesAllowed`. `@RunAs("ADMIN")` on `ShipmentStatusUpdateTimerBean`
and `OrderProcessingServiceBean` is the mechanism built specifically for this case
— it establishes a fixed, container-asserted identity for every outbound call a
component makes, independent of (and, for a timer, in the total absence of) any
interactive caller identity. This is the closest analogue in this system to the
brief's "emergency logistics scenarios" framing: an automated process that must be
able to act with elevated, trusted authority regardless of who or what triggered
it, precisely because it is the system itself — not a human whose personal
privileges should gate the action — performing a well-defined, narrowly-scoped
piece of automated work on the system's own behalf. `OrderProcessingServiceBean`'s
case is the more subtle of the two: its *caller* may only hold
`LOGISTICS_COORDINATOR` (not `CUSTOMS_AGENT`), yet its internal, conditional
customs-clearance branch must call `CustomsDocumentationServiceBean.approveDocument`,
which requires `CUSTOMS_AGENT` or `ADMIN`. Without `@RunAs`, that inner call would
fail with `EJBAccessException` for every caller except an administrator, even
though "approve as part of this already-authorized, orchestrated order workflow"
is exactly what the bean exists to do on the coordinator's behalf. Using a single
role (`ADMIN`) for both automated cases, rather than introducing a dedicated
`SYSTEM_PROCESS` role, is a deliberate scope trade-off documented here rather than
hidden: a dedicated automation-only role would be the more precise design (avoiding
the slight semantic conflation of "administrator" and "automated system") and is
flagged as a natural extension point, but was not worth the added role to thread
through every descriptor and `@RolesAllowed` list for a two-caller reference
implementation.

### Security interceptor integration with performance considerations for time-sensitive trade operations

`SecurityAuditInterceptor` writes to a **separate logger** (`com.globaltrade.scm.SECURITY_AUDIT`)
rather than the same `AuditLogEntry` table `AuditLoggingInterceptor` already writes
to on every method — a deliberate choice to avoid doubling the write cost on the
already-audited, security-sensitive operations it targets, and to let this stream
be routed, retained, and alerted-on independently (a SIEM/log-aggregation pipeline
watching specifically for `SECURITY_AUDIT`-tagged lines does not need to filter
GlassFish's general audit-table traffic to find them). Because it is bound at the
method level to a small, deliberately narrow set of genuinely security-sensitive
operations (customs-document approval; any future "security override" path), its
performance cost is paid only where the operation's own sensitivity already
justifies it — a time-sensitive, high-frequency operation like `trackShipment` was
never a candidate for this interceptor in the first place, so no trade-off between
"time-sensitive" and "security-audited" actually had to be made for it.

---

## 5. EJB Best Practices and Supply Chain Optimization

### EJB component lifecycle management in logistics contexts

Every session bean in this system is `@Stateless` except the timer singletons —
a deliberate default, not an oversight of when `@Stateful` might apply. None of
this system's workflows need conversational, per-client state held in a bean
instance across multiple calls (an order is fully described by the parameters
passed to `processSupplyChainOrder` in one call; nothing about it benefits from a
stateful bean remembering earlier calls from the same client). `@Stateless`
instances are pooled and interchangeable specifically because they hold no
client-specific state, which is what makes the pool-sizing tuning in
`glassfish-ejb-jar.xml` (see next subsection) a meaningful lever at all — pooling a
stateful bean would not offer the same throughput benefit, since a stateful
instance is bound to one client's conversation and cannot be handed to a different
client mid-conversation regardless of pool size.

The lazy-loading correctness work in `ShipmentTrackingServiceBean` and
`CustomsDocumentationServiceBean` (`LEFT JOIN FETCH` added to every query whose
results are read outside of a live transaction/persistence context — see each
method's own javadoc) is itself a lifecycle-management concern: a `NOT_SUPPORTED`
method's persistence context does not reliably outlive the single query call it
services, so any caller — including this class's own DTO-mapping code, and
definitely the REST layer, which reads the returned entity strictly after the EJB
call that produced it has already returned — that touches a `FetchType.LAZY`
association after that point is touching a proxy the container can no longer
service. This is a case where understanding *when* a container-managed persistence
context actually exists (as opposed to when a bean method syntactically appears to
still be "in scope") was necessary to avoid a bug that would not show up in casual
testing (it depends on which associations happen to already be initialized, which
can look "fine" until the code path or data shape changes) but would fail
unpredictably in production.

### Resource pooling strategies for supply chain applications

`glassfish-ejb-jar.xml` tunes `<bean-pool>` sizing for three beans specifically,
rather than leaving every bean at the server-wide default:

- `ShipmentTrackingServiceBean`: the hottest path in the system (every carrier
  webhook, every tracking lookup, and the 15-minute timer all funnel through it) —
  given a larger steady pool (`10`, growable to `64`) so a burst of concurrent
  carrier webhooks does not pay instance-creation latency on the marginal request.
- `CustomsDocumentationServiceBean` and `OrderProcessingServiceBean`: lower-volume,
  staff-driven workflows (a customs agent or coordinator working through a queue,
  not a high-frequency automated feed) — given a small steady pool (`2`) so the
  server is not holding idle pooled instances, and the `EntityManager` /
  `UserTransaction` resources each instance carries, for capacity this bean rarely
  actually needs.

This is the concrete instance of a general principle: **default pool sizing is a
reasonable choice for beans with no distinguishing load profile, but a bean known
in advance to be either unusually hot or unusually cold is worth an explicit,
justified override.** Both directions matter — under-provisioning the hot path
costs latency under real load; over-provisioning the cold paths costs memory and
open database connections for capacity that sits idle.

### Remote vs. local interface selection for carrier and vendor system integration

Two beans expose a `@Remote` interface — `ShipmentTrackingServiceBean` and
`VendorPerformanceServiceBean` — and every other session bean is `@Local`-only.
This split follows directly from which beans have a genuine, currently-real reason
for an out-of-process caller: carriers push status updates and customers/vendors
query tracking from outside this EAR (`ShipmentTrackingServiceRemote`); vendor
portal clients query performance summaries and trigger async assessments from
outside this EAR (`VendorPerformanceServiceRemote`). `InventoryManagementServiceBean`,
`CustomsDocumentationServiceBean`, and `OrderProcessingServiceBean` have no such
external caller in this system's actual design — nothing outside the EAR mutates
warehouse stock, files customs paperwork, or orchestrates an order directly — so
each stays `@Local`-only.

This is not a default-to-remote-just-in-case choice, and the reasoning for staying
local is as deliberate as the reasoning for going remote: RMI/IIOP invocation pays
real, measurable cost on every call (argument marshalling, a coarser-grained
network security boundary to defend) that a same-JVM local call simply does not
pay, and every additional method exposed remotely is one more entry point that must
be defended against a caller the container trusts less than an in-process one. The
two remote interfaces are also **narrower** than their local counterparts on the
same bean — `ShipmentTrackingServiceRemote` exposes only `trackShipment` and
`recordCarrierStatusUpdate`, not the full local API (`registerShipment`,
`findActiveShipments`, `cancelShipment` stay local-only) — for the same reason:
expose exactly the operations an external caller legitimately needs, not
everything the bean happens to implement.

The "split directory structure" deliverable is the mechanical enforcement of this
same boundary at the build level: `scm-ejb`'s `maven-ejb-plugin` configuration
generates `scm-ejb-client.jar` (`generateClient=true`), a thin artifact containing
only the `@Remote` business interfaces and the `scm-common` DTOs/enums they use —
no entities, no bean implementation classes, no security or recovery internals. A
genuinely external, standalone carrier-integration client (not co-deployed in this
EAR) would depend on `scm-ejb-client` rather than the full `scm-ejb` artifact:

```xml
<dependency>
    <groupId>com.globaltrade.scm</groupId>
    <artifactId>scm-ejb</artifactId>
    <classifier>client</classifier>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

```java
Properties env = new Properties();
env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.enterprise.naming.SerialInitContextFactory");
env.put(Context.PROVIDER_URL, "iiop://scm-host:3700");
InitialContext ctx = new InitialContext(env);
ShipmentTrackingServiceRemote shipmentTracking = (ShipmentTrackingServiceRemote)
        ctx.lookup("java:global/globaltrade-scm/scm-ejb/ShipmentTrackingServiceBean!"
                + "com.globaltrade.scm.service.remote.ShipmentTrackingServiceRemote");
```

That client's classpath never pulls in `SupplyChainLoginModule`, `Shipment`
(the JPA entity), or any of the exception/recovery internals it has no business
seeing — exactly the isolation the split is meant to provide.

### Deployment descriptor optimization with packaging strategies

Covered together with the parallel Section 7 discussion — see
["Deployment descriptor management for logistics workflows"](#deployment-descriptor-management-for-logistics-workflows)
and
["JAR/WAR/EAR packaging strategies for supply chain applications"](#jarwarear-packaging-strategies-for-supply-chain-applications)
below, to avoid repeating the same material under two headings.

---

## 6. Supply Chain Exception Handling and System Resilience

### Application exceptions vs. system exceptions in supply chain contexts

This project uses two genuinely distinct exception hierarchies, not one hierarchy
with inconsistent handling:

- **`SupplyChainException`** (abstract, checked, extends `Exception`): the root for
  expected, business-meaningful failures the *caller* is expected to catch and
  handle as normal control flow — insufficient stock
  (`InsufficientInventoryException`), a rejected customs filing
  (`CustomsComplianceException`), invalid vendor data
  (`VendorDataValidationException`), an unknown tracking number
  (`ShipmentTrackingException`), an unreachable carrier
  (`CarrierSystemUnavailableException`). Every concrete subtype carries an explicit
  `@ApplicationException(rollback=...)` reflecting its own specific semantics rather
  than a blanket default — `InsufficientInventoryException` and
  `CustomsComplianceException` are `rollback=true` (a partially-reserved order or a
  non-compliant filing left half-committed is worse than the whole unit of work
  failing cleanly), while `ShipmentTrackingException` and
  `CarrierSystemUnavailableException` are `rollback=false` (a "not found" on a
  read-only lookup, or one unreachable carrier out of a batch of many being
  processed, has nothing that *needs* to be undone, and should not force an
  otherwise-successful surrounding transaction to fail).
- **`SupplyChainSystemException`** (unchecked, extends `RuntimeException`): reserved
  for failures the caller cannot meaningfully recover from — data corruption
  (`InventoryDataCorruptionException`), or, concretely in this codebase, an
  `OptimisticLockException` surfaced by `ShipmentTrackingServiceBean.mergeWithOptimisticLockHandling`
  when a concurrent write conflict means the in-memory entity the caller was
  working with is already stale. Per EJB semantics, an unchecked exception thrown
  from a business method automatically marks the ambient transaction for rollback
  and, for remote clients, arrives wrapped as `jakarta.ejb.EJBException` — exactly
  the behavior wanted for a condition where continuing with stale data would be
  actively wrong, and where the correct recovery (re-read current state, recompute
  the intended change) is a decision only the original caller can make, not
  something this exception type should try to paper over with an automatic retry.

The dividing line the codebase actually applies, concretely: **if a reasonable
caller could write a `catch` block that does something business-meaningful with
this failure (retry with different input, show the user a specific message, try a
different vendor), it is a checked `SupplyChainException` subtype. If the only
reasonable response is "log this, alert someone, and fail the request," it is
unchecked.**

At the REST boundary, this distinction is preserved rather than collapsed:
`SupplyChainExceptionMapper` maps each checked subtype to a status code reflecting
its actual business meaning (404 for "not found," 409 for a stock conflict, 422 for
a compliance/validation failure, 503 for an unreachable external dependency), while
the separate `SupplyChainSystemExceptionMapper` deliberately returns a generic
message and a correlation id rather than the exception's own message or stack
trace — an unchecked system exception's message may describe internal
implementation details not appropriate to hand to an HTTP client, and the
correlation id (logged server-side at `SEVERE`) is what lets a support engineer
connect a client's bug report back to the actual failure without exposing that
detail externally.

### Exception propagation in distributed logistics environments

Two propagation paths are exercised in this system, and each behaves correctly for
different reasons:

1. **Local-to-local, within the EAR** (the overwhelmingly common case — every
   `@EJB`-injected local interface call): a checked `SupplyChainException` subtype
   propagates as itself, unmodified, all the way to the REST layer, where
   `SupplyChainExceptionMapper` catches it. Nothing wraps or translates it in
   between (in particular, this is *why* `OrderProcessingServiceBean.processSupplyChainOrder`'s
   own checked-exception catch block can pattern-match on the exact business
   exception types it expects and simply re-throw them after rolling back — no
   unwrapping is needed because nothing wrapped them on the way through).
2. **Remote, across a process boundary** (`ShipmentTrackingServiceRemote`,
   `VendorPerformanceServiceRemote`): checked application exceptions declared on
   the remote interface still propagate to the remote client largely as
   themselves (RMI/IIOP serializes and rethrows them), which is why every checked
   exception type in this system is a plain, small, easily-serializable class with
   no non-serializable fields — a design constraint that was already satisfied by
   how these exceptions were written, not retrofitted after the fact. **Unchecked**
   exceptions crossing this same remote boundary do *not* propagate as themselves —
   the EJB container wraps them as `jakarta.ejb.EJBException` for a remote caller,
   which is exactly why `SupplyChainSystemException` is documented as something
   application code should catch only at a true boundary (a `@Provider`
   `ExceptionMapper`) to translate into a response, never to inspect and "handle"
   further inside business logic that might run locally or remotely depending on
   which interface a given caller happens to be using.

### Recovery strategies for different supply chain failure scenarios

`ExceptionRecoveryManager.executeWithRetry` implements one strategy — bounded
retry with a short linear backoff, falling through to a persistent
`FailedOperation` dead-letter row once the retry budget (three attempts) is
exhausted — applied specifically to **transient failures calling external systems**
(currently: `ShipmentStatusUpdateTimerBean`'s carrier-status polling). This
strategy is deliberately *not* applied uniformly to every failure type in the
system, because it is only the right strategy for a specific failure shape:

- **Right fit — carrier API calls**: a carrier's API returning an error is
  plausibly transient (a brief network blip, a momentary rate limit), the
  operation is naturally idempotent (re-querying status is a read, and
  re-recording an unchanged status is a no-op), and a bounded number of fast
  retries meaningfully increases the odds of success without meaningfully
  delaying the rest of the batch.
- **Wrong fit — optimistic-lock conflicts** (`ShipmentTrackingServiceBean.mergeWithOptimisticLockHandling`):
  deliberately does **not** go through `ExceptionRecoveryManager`. A version
  conflict means the in-memory entity was built from a row version another writer
  has since superseded; blindly retrying the *same* `merge()` call would fail
  identically against the new version every time, since nothing about the retry
  re-reads current state. The only correct recovery is a fresh read followed by
  recomputing the intended change — a decision that belongs to the original caller
  (who alone knows what change it actually wanted to make), so this path instead
  wraps and surfaces `SupplyChainSystemException` immediately, with a message
  written to communicate "safe to retry the *whole operation* from scratch" rather
  than attempting an automatic retry that could not succeed.
- **Wrong fit — validation failures**: `VendorDataValidationException`,
  `CustomsComplianceException` and similar are not transient at all — retrying the
  exact same invalid input three times would fail three times identically. These
  simply propagate immediately; "recovery" for a validation failure is the
  caller supplying different, valid input, not a system-level retry.

The dead-letter table (`FailedOperation`) is the deliberate "last line of defense"
distinct from the retry loop itself: `ExceptionRecoveryManager.recordFailure` runs
`REQUIRES_NEW` specifically so the dead-letter record survives even when the
caller's own transaction is, moments later, going to roll back after re-throwing
the same failure — a `FailedOperation` row written in the *same* transaction as the
failure it records would itself be undone by that transaction's rollback, which
would defeat the entire purpose of a dead-letter queue (there would be no durable
trace that the failure happened at all). This row is reviewed and manually replayed
by logistics operations staff (`ExceptionRecoveryManager.findUnresolvedFailures`
backs that review workflow) — the appropriate human-in-the-loop response for a
failure category (a carrier down for an entire 15-minute cycle, or longer) that
automated retry within one timer run has already been given a fair, bounded chance
to resolve and did not.

### Exception handling performance impact with optimization techniques ensuring supply chain continuity

The performance-relevant design choice throughout this exception hierarchy is
**where the cost is paid**: checked `SupplyChainException` construction is cheap
(a message string, sometimes a cause) and happens only on the actual failure path,
never on the success path — none of this system's exception types pre-build
expensive diagnostic payloads (a full entity graph dump, a formatted stack trace
string) speculatively before it is known whether the exception will actually be
thrown. `ExceptionRecoveryManager`'s bounded retry is itself a direct continuity
optimization: three fast attempts with backoff measured in tens of milliseconds
(worst case objectively bounded, well under a quarter of a second total) is a cost
the 15-minute polling cycle can absorb many times over per run without meaningfully
threatening the next cycle's start time, while still meaningfully improving the
odds a transient blip does not need to wait a full 15 minutes for the next
attempt. The explicit choice *not* to use a longer, timer-based delayed-retry
mechanism for this same use case (discussed in `ExceptionRecoveryManager`'s own
javadoc) is the corresponding continuity trade-off in the other direction: a
sustained, minutes-long carrier outage is handled by the dead-letter record and the
*next scheduled polling cycle* — which already exists and needs no new
infrastructure — rather than by this method holding a thread and retrying for
longer, which would directly threaten the timer's own ability to move on to the
next shipment (and, at scale, the next scheduled firing) in a timely way.

---

## 7. Supply Chain Component Organization and Deployment Strategy

### JAR/WAR/EAR packaging strategies for supply chain applications

Four Maven modules, each with a distinct packaging responsibility:

- **`scm-common`** (`jar`): DTOs (`ShipmentTrackingResult`, `VendorPerformanceSummary`)
  and enums (`ShipmentStatus`, `CustomsDocumentStatus`, `CustomsDocumentType`,
  `UserRole`) shared between the EJB and web tiers, with **no** dependency on
  either — this is what lets `scm-web` depend on the same status/type vocabulary
  the EJB tier uses without depending on the EJB tier's implementation classes,
  entities, or security internals at all.
- **`scm-ejb`** (`ejb`): the business tier described throughout this document, plus
  a generated thin client jar (`scm-ejb-client`, discussed under
  ["Remote vs. local interface selection"](#remote-vs-local-interface-selection-for-carrier-and-vendor-system-integration)
  above) for out-of-EAR consumers.
- **`scm-web`** (`war`): the JAX-RS facade, depending on `scm-ejb` with
  `<scope>provided</scope>` — compiled against for its local interfaces, but
  **not** bundled into `WEB-INF/lib`, because the sibling EJB module already
  provides those classes on the shared application classloader once both are
  assembled into the same EAR. Packaging a second copy into the WAR would be a
  classic duplicate-class Java EE classloading failure, not a redundant-but-harmless
  belt-and-suspenders choice.
- **`scm-ear`** (`ear`): the deployable assembly, bundling `scm-ejb.jar` and
  `scm-web.war` (explicit `bundleFileName`s on both, kept in sync with the module
  filenames hand-written into `application.xml` — see the next subsection) behind
  one context root, `/scm`.

This four-module split *is* the assignment's "split directory structure for EJB
components" requirement in concrete form: interfaces and shared contracts
(`scm-common`) are packaged separately from implementation (`scm-ejb`), which is in
turn packaged separately from its own remote-consumable subset
(`scm-ejb-client`), which is in turn separate from the presentation tier
(`scm-web`) and the final deployable assembly (`scm-ear`) — each boundary chosen
because a real, different consumer exists on each side of it (the web tier needs
the DTOs but not carrier-integration internals; an external carrier client needs
the remote interfaces but nothing else; the EAR needs both deployable modules but
neither module needs to know about the other's packaging).

### Deployment descriptor management for logistics workflows

This project's descriptors are deliberately minimal and reserved for what
annotations genuinely cannot express, rather than duplicating what `@Stateless`,
`@Local`/`@Remote`, `@Schedule`, `@RolesAllowed` and friends already declare:

- **`ejb-jar.xml`** exists for exactly two things annotations cannot do: declaring
  `AuditLoggingInterceptor` as a true module-wide default interceptor (the
  `<ejb-name>*</ejb-name>` wildcard binding has no annotation equivalent — the
  closest annotation-based tool, CDI's `@Priority`-based global interceptor
  enablement, is a different mechanism tied to CDI's own interceptor-binding model,
  which this project deliberately does not use for its interceptors — see below),
  and declaring the `<security-role>` set once, portably.
- **`glassfish-ejb-jar.xml`** exists for bean-pool tuning (server-specific,
  discussed under [Section 5](#resource-pooling-strategies-for-supply-chain-applications))
  — there is no portable, standard-Jakarta-EE annotation for steady/max pool size.
- **`glassfish-application.xml`** exists for principal-to-role mapping (mapping the
  portable `<security-role>` names to the `SupplyChainPrincipal` names
  `SupplyChainLoginModule` actually produces, via `<principal-name>` rather than
  `<group-name>` — see that file's own header comment for why a Group-marker-typed
  principal was tried, found to depend on a JDK package removed since Java 9, and
  reverted) — centralized once, EAR-wide, rather than duplicated per module, so a
  security review has exactly one table to audit rather than the same six mappings
  copy-pasted across `glassfish-ejb-jar.xml` and `glassfish-web.xml` independently.
- **`application.xml`** exists because *some* file has to declare which modules
  compose the EAR and at what context root — this is inherent to EAR packaging, not
  optional, though `maven-ear-plugin` could have generated a default one; a
  hand-written version is used instead specifically to also carry the
  `<security-role>` declarations and the descriptive metadata a generated file
  would omit.

The interceptor-binding choice above is worth stating explicitly: this project uses
the classic EJB-specification `@Interceptors` annotation (plus the one XML default
binding) rather than CDI's `@InterceptorBinding` custom-annotation mechanism
throughout. This means none of the four interceptors *require* `beans.xml` to
function at all — `@Interceptors` binding is resolved by the EJB container
directly, independent of CDI bean-archive discovery. `beans.xml` is still present
in both `scm-ejb` and `scm-web` (minimal, `bean-discovery-mode="annotated"`) purely
for forward compatibility, so that any future component wanting a genuine CDI
feature (`@Inject`, an `@ApplicationScoped` bean) has a bean archive to be
discovered within, without that being a precondition this project's actual
cross-cutting concerns depend on today.

### Class loader implications for carrier and customs system integration

The `provided`-scope dependency from `scm-web` to `scm-ejb` (discussed above) is
this project's direct, concrete encounter with Java EE classloader hierarchy rules:
a GlassFish EAR gives every module deployed within it access to a shared
application-level classloader, and a class present on *both* a WAR's private
`WEB-INF/lib` classloader and the shared application classloader is a well-known
source of `ClassCastException`s and bean-resolution ambiguity (two distinct
`Class` objects for what should be "the same" type, from the JVM's perspective,
loaded by two different classloaders). This is precisely why `scm-web`'s `pom.xml`
carries an explicit code comment on this exact point rather than leaving the
`provided` scope unexplained: it is easy for a future contributor, adding a new
inter-module dependency later, to reach for `compile` scope by habit and
reintroduce this exact class of bug.

For actual external carrier/customs system integration specifically, the relevant
classloader boundary is the one the `scm-ejb-client` thin jar exists to respect —
an external client's own classloader never needs to (and, using the client jar
rather than the full `scm-ejb` artifact, structurally cannot easily) load this
application's entity classes, security internals, or interceptor implementations,
which is both a classloading-cleanliness property and, incidentally, a security
one (an external client has no code-level path to those internals to begin with).

### Deployment tool selection with performance considerations for 24/7 global operations

Deployment tooling in this project is entirely `asadmin` (GlassFish's own CLI,
scriptable and the natural fit for CI/CD automation) plus Maven for the build —
deliberately not GlassFish's web-based admin console for anything beyond
one-off, interactive inspection. For a 24/7 global operation, deployment needs to
be scriptable, repeatable, and auditable (the exact `asadmin` commands run,
including resource setup via `deploy/glassfish-resources.xml`, are documented
verbatim in `docs/DEPLOYMENT_GUIDE.md`) rather than dependent on a human clicking
through a UI consistently the same way every time across every environment and
every on-call engineer. The `deploy/glassfish-resources.xml` split from the EAR
itself (discussed in that file's own header comment) is the concrete performance
and operability implication of this choice: JDBC pool sizing and connection
details are environment configuration, applied once per domain via `asadmin
add-resources`, completely independent of and unaffected by how often the
application EAR itself is redeployed — a rolling application update during a
maintenance window does not need to (and should not) touch, and risk misconfiguring,
already-tuned, already-warmed-up connection pool settings.

---

*Related documents: [`docs/NFR_ANALYSIS.md`](./NFR_ANALYSIS.md) (security,
performance, reliability, and trade-compliance analysis against the assignment's
non-functional requirements) and [`docs/DEPLOYMENT_GUIDE.md`](./DEPLOYMENT_GUIDE.md)
(concrete build and deployment steps).*
