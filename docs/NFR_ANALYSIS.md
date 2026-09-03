# Non-Functional Requirements Analysis

Companion to [`docs/CRITICAL_ANALYSIS.md`](./CRITICAL_ANALYSIS.md), which covers the
*functional* design-requirement areas from the assignment brief in depth. This
document addresses the brief's four Non-Functional Requirements Analysis areas
directly, cross-referencing the critical-analysis document rather than repeating it
wherever the same implementation detail is already covered there, and going further
where the brief specifically asks the solution to **propose** something beyond what
is currently built.

## Table of Contents

1. [Supply Chain Security Analysis](#1-supply-chain-security-analysis)
2. [Logistics Performance Analysis](#2-logistics-performance-analysis)
3. [Supply Chain Reliability Assessment](#3-supply-chain-reliability-assessment)
4. [Trade Compliance Evaluation](#4-trade-compliance-evaluation)

---

## 1. Supply Chain Security Analysis

### Vendor data authentication and authorization strategies

Authentication is a single JAAS path (`SupplyChainLoginModule`, PBKDF2-hashed
credentials in `system_user`) for every role including `VENDOR_REPRESENTATIVE`;
authorization is `@RolesAllowed`-gated per operation, layered with a coarse
web-tier constraint — the full detail is in
[`CRITICAL_ANALYSIS.md`, "Role-based access control implementation"](./CRITICAL_ANALYSIS.md#role-based-access-control-implementation-for-logistics-personnel)
and
["Authentication mechanisms for different user types"](./CRITICAL_ANALYSIS.md#authentication-mechanisms-for-different-user-types-and-emergency-logistics-scenarios).
Specific to *vendor* data in particular: `VendorDataValidationInterceptor` is
class-bound to both beans that accept vendor-supplied input
(`VendorPerformanceServiceBean`, `CustomsDocumentationServiceBean`), so validation
runs before authorization-gated business logic executes on any vendor-shaped
parameter, and a `VENDOR_REPRESENTATIVE` can update their own vendor's profile
(`updateVendorProfile`) but cannot register a new vendor or submit a performance
review of any vendor including their own (`registerVendor`,
`submitVendorPerformanceReview` are `ADMIN`/`LOGISTICS_COORDINATOR`-only) — a
vendor should never be able to write their own performance record.

**Gap not yet closed**: this system has no notion of "vendor representative X may
only act on vendor X's own data" at the authorization layer — `@RolesAllowed`
answers "does this caller hold `VENDOR_REPRESENTATIVE`" but not "is this the
specific vendor's own representative." Today, `updateVendorProfile(Long vendorId, ...)`
trusts the caller-supplied `vendorId` rather than cross-checking it against the
authenticated principal's own vendor affiliation. Closing this requires either (a)
extending `SystemUser`/`SupplyChainPrincipal` to carry a vendor-id claim and adding
a programmatic `sessionContext.getCallerPrincipal()`-based check in
`VendorPerformanceServiceBean`, or (b) a dedicated per-vendor API key scheme
separate from the shared JAAS realm. Recommendation: (a), since it reuses the
existing JAAS/RBAC infrastructure rather than introducing a second authentication
mechanism (see the "one authentication path" reasoning in
[`CRITICAL_ANALYSIS.md`](./CRITICAL_ANALYSIS.md#authentication-mechanisms-for-different-user-types-and-emergency-logistics-scenarios)).
This is flagged explicitly as a known, currently-open gap rather than left
implicit.

### Logistics session management approaches

Covered in
[`CRITICAL_ANALYSIS.md`, "Session security and management strategies"](./CRITICAL_ANALYSIS.md#session-security-and-management-strategies-for-global-supply-chain-workflows):
container-managed session/security-context, `CONFIDENTIAL` transport guarantee
forcing HTTPS given `BASIC` auth's cleartext-adjacent credential transmission.

### Supply chain data encryption requirements

**In transit**: enforced for the web tier (`user-data-constraint` /
`CONFIDENTIAL` in `web.xml`, requiring an HTTPS listener — see
`docs/DEPLOYMENT_GUIDE.md`). The JDBC connection to MySQL is configured with
`useSSL=true` (`deploy/glassfish-resources.xml`), so vendor contact details,
customs documentation, and credentials in transit between the application tier and
the database are also encrypted, not just the browser/client-facing hop.

**At rest**: this is the one encryption dimension this reference implementation
does **not** currently address, and it is called out here rather than left silent,
given the brief's explicit "vendor data protection" and "international trade
regulations" framing. `password_hash` in `system_user` is a salted PBKDF2 derivation
— irreversible by design, so "at rest encryption" does not apply to it the way it
applies to genuinely sensitive plaintext fields. What *is* currently stored in
plaintext at the database layer: vendor contact emails, customs `compliance_notes`
(free text that may reference sensitive shipment/trade details), and audit log
`details`. Two complementary mechanisms would close this gap without changing
application code structure: (1) MySQL's InnoDB tablespace encryption
(`innodb_encrypt_tables`), covering data-at-rest for the whole database
transparently to the application, and (2) column-level application-managed
encryption (a `jakarta.crypto`-based `AttributeConverter` on specific JPA fields —
`Vendor.contactEmail`, `CustomsDocument.complianceNotes`) for the smaller set of
fields where encryption needs to survive a database-level backup/restore or
export that bypasses InnoDB's own encryption. Recommendation: (1) as the baseline
(low implementation cost, broad coverage), with (2) reserved for fields a future
compliance review specifically flags as needing it, rather than applying column
encryption speculatively everywhere and paying its query/indexing cost (an
encrypted column cannot be efficiently searched/filtered by the database) for
fields that do not need it.

### Global trade security monitoring mechanisms

**Built today**: `SecurityAuditInterceptor` writes security-sensitive operations
(currently: customs-document approval) to a dedicated logger stream
(`com.globaltrade.scm.SECURITY_AUDIT`), separate from the general `AuditLogEntry`
table every operation already gets from `AuditLoggingInterceptor` — see
[`CRITICAL_ANALYSIS.md`](./CRITICAL_ANALYSIS.md#security-interceptor-integration-with-performance-considerations-for-time-sensitive-trade-operations).
Both streams are queryable today (`AuditLogEntry` via JPQL; the security logger via
whatever log-aggregation GlassFish is configured to ship to).

**Proposed, not yet built**: a genuine "security monitoring mechanism" implies
active alerting, not just a queryable/greppable trail after the fact. The natural
extension, reusing infrastructure this project already has rather than introducing
new infrastructure, is: (a) route the `SECURITY_AUDIT` logger through a
log-aggregation pipeline with alert rules for specific patterns — repeated
authorization failures from one principal (a credential-stuffing signal),
after-hours customs approvals outside the reviewing agent's normal pattern, or a
spike in `FailedOperation` rows tagged with a security-relevant `operationType`;
and (b) a scheduled reporting timer (following the exact `@Schedule` pattern
already established by `InventoryLevelMonitorTimerBean`) that periodically
summarizes `AuditLogEntry` and the security logger's recent activity into a digest
for a compliance officer, rather than requiring anyone to proactively query either
stream. Neither is built in this reference implementation because both are
genuinely operational/infrastructure concerns (what log pipeline, what alerting
thresholds, who receives the digest) that depend on decisions outside this
codebase's scope — the application-side hooks (structured, consistently-tagged log
output; a queryable audit table) that any such mechanism would consume already
exist and do not need to be re-architected to support it.

---

## 2. Logistics Performance Analysis

### Timer service optimization strategies

Covered in full in
[`CRITICAL_ANALYSIS.md`, Section 1](./CRITICAL_ANALYSIS.md#1-supply-chain-timer-services-integration-and-management)
(programmatic-vs-declarative selection, per-shipment isolation, N+1 avoidance via
fetch-joining, the L2-cache trade-off). The one point worth restating here in
performance-specific terms: every timer interval in this system
(15 minutes, hourly, nightly, weekly) is a **static** configuration value today —
none adapt to observed load (e.g. slowing the shipment-poll interval automatically
if `MetricsRegistry`'s slow-invocation counter for that callback starts climbing).
Static intervals are the right starting point for a system with no production
traffic history to tune against, but adaptive scheduling (reading a threshold from
configuration, or from `MetricsRegistry` itself, to widen an interval under
sustained load rather than letting overlapping runs stack up) is a natural
performance optimization to revisit once real load data exists.

### Interceptor performance impact on logistics workflows

Covered in full in
[`CRITICAL_ANALYSIS.md`, "Interceptor performance impact"](./CRITICAL_ANALYSIS.md#interceptor-performance-impact)
and
["Interceptor chain optimization"](./CRITICAL_ANALYSIS.md#interceptor-chain-optimization-for-supply-chain-workflows).

### Transaction timeout optimization for trade processes

`OrderProcessingServiceBean` sets an explicit 60-second `UserTransaction` timeout
(`TRANSACTION_TIMEOUT_SECONDS`) rather than relying on the container-wide default —
deliberately, because this specific transaction's worst case (an international
order: reserve stock, register a shipment, file/submit/approve a customs document,
finalize clearance, all as one unit of work) is measurably longer than a typical
single-bean CMT operation, and a container-wide default tuned for the common case
would either time out this legitimately-longer workflow prematurely or, if raised
globally to accommodate it, leave every *other*, genuinely-short transaction in the
system with an unnecessarily generous timeout that delays detecting a truly stuck
transaction. Every other transaction in the system (every CMT method) uses the
container-wide default rather than a per-method override, because none of them
have a comparably distinct duration profile to justify one — this is the same
"override only where a bean's profile genuinely differs" principle applied to
timeout tuning that
[bean-pool sizing](./CRITICAL_ANALYSIS.md#resource-pooling-strategies-for-supply-chain-applications)
already applies to pool sizing.

### Performance monitoring mechanisms for global supply chain operations

`MetricsRegistry` (a `@Singleton`, `@ConcurrencyManagement(BEAN)` for
low-contention concurrent counter updates) is the built mechanism:
`PerformanceMonitoringInterceptor` records invocation counts and slow-invocation
counts against it for every method it is bound to (see
[`CRITICAL_ANALYSIS.md`, Section 2](./CRITICAL_ANALYSIS.md#2-logistics-interceptor-architecture-and-implementation)
for exactly which methods and why). `MetricsRegistry.snapshotCounters()` exposes
the current state for inspection. What is proposed but not built: a REST endpoint
(a natural `MetricsResource` alongside the existing five resources in `scm-web`,
gated to `ADMIN` only) exposing `snapshotCounters()` as JSON for an external
dashboard/monitoring tool to poll, and/or a periodic timer that logs a formatted
snapshot on a fixed interval so metrics are visible in the same log stream
operators are already watching without needing a separate polling client. Both are
small, low-risk additions given `MetricsRegistry` already centralizes the data;
neither was built because "expose metrics for external consumption" is a decision
about *which* monitoring tool this deploys alongside (Prometheus-style pull,
CloudWatch-style push, plain log-based) that belongs to the operations team
choosing that tooling, not to this reference implementation.

---

## 3. Supply Chain Reliability Assessment

### Exception handling strategies for logistics emergencies

Covered in full in
[`CRITICAL_ANALYSIS.md`, Section 6](./CRITICAL_ANALYSIS.md#6-supply-chain-exception-handling-and-system-resilience).
In reliability terms specifically: the checked/unchecked split means a genuine
emergency (data corruption, a lost-update conflict) is structurally guaranteed to
roll back its transaction and surface loudly (an unchecked exception cannot be
accidentally swallowed by a narrow `catch` block the way a checked exception's
`catch (SpecificException e) { /* ignored */ }` sometimes can be), while routine,
expected business conditions (insufficient stock, an unreachable carrier) do not
unnecessarily escalate to the same severity.

### Timer service reliability for critical supply chain processes

Covered in full in
[`CRITICAL_ANALYSIS.md`, "Timer persistence and reliability in globally distributed logistics environments"](./CRITICAL_ANALYSIS.md#timer-persistence-and-reliability-in-globally-distributed-logistics-environments).

### Transaction recovery approaches for logistics data

Two distinct recovery approaches, applied to two distinct failure shapes — the
full comparison (why each is the right tool for its specific case, and explicitly
why the two are *not* interchangeable) is in
[`CRITICAL_ANALYSIS.md`, "Recovery strategies for different supply chain failure scenarios"](./CRITICAL_ANALYSIS.md#recovery-strategies-for-different-supply-chain-failure-scenarios):
bounded retry-then-dead-letter (`ExceptionRecoveryManager`) for transient external-system
failures, and immediate wrap-and-surface (`SupplyChainSystemException`) for
optimistic-lock conflicts, where retrying the identical operation cannot succeed
and only a caller-driven re-read-and-recompute can.

### Comprehensive availability measures for global trade systems

This reference implementation's availability posture rests on three pillars, one
fully built, one partially built, one explicitly out of scope for application code:

1. **Application-level resilience to partial failure** (fully built): per-shipment
   isolation in batch timer runs, `REQUIRES_NEW` for independently-committing
   writes, bounded retry for transient external calls, dead-letter recording rather
   than silent failure — see
   [`CRITICAL_ANALYSIS.md`, Section 3](./CRITICAL_ANALYSIS.md#3-logistics-transaction-demarcation-and-management)
   and [Section 6](./CRITICAL_ANALYSIS.md#6-supply-chain-exception-handling-and-system-resilience).
   The system is designed so that one shipment, one carrier, or one SKU having a
   bad day degrades gracefully rather than taking an entire batch operation down
   with it.
2. **Data-tier resilience** (partially built): the JDBC pool is sized for
   sustained concurrent load (`steady-pool-size=16`, `max-pool-size=64` in
   `deploy/glassfish-resources.xml`) and validates connections
   (`is-connection-validation-required=true`) before handing them out, so a
   transient database blip does not hand the application a dead connection. What
   is *not* built or configured here, because it is infrastructure the application
   layer deliberately does not own: MySQL replication/failover topology, and
   GlassFish cluster configuration for the application tier itself. Both are named
   explicitly in `docs/DEPLOYMENT_GUIDE.md` as environment setup the deploying
   organization must provide; the application's own resilience patterns (above)
   are what let it take advantage of that infrastructure once it exists, but this
   codebase cannot itself stand up a MySQL replica set or a multi-instance
   GlassFish cluster.
3. **The 99.9%-uptime requirement's actual arithmetic**: 99.9% allows roughly 8.76
   hours of downtime per year (about 43 minutes per month). Achieving that
   specifically requires the clustered, multi-instance deployment topology named in
   pillar 2 — a single GlassFish instance, however well-tuned the application
   running on it, has a hard ceiling on availability set by that one instance's own
   patching/restart cadence. This project's design choices (particularly the
   explicit non-reliance on in-memory session/conversational state — see
   [`CRITICAL_ANALYSIS.md`'s stateless-bean discussion](./CRITICAL_ANALYSIS.md#ejb-component-lifecycle-management-in-logistics-contexts))
   are what make horizontal scaling to that topology possible without an
   application redesign, but standing the topology up is a deployment-time,
   infrastructure decision, not something this codebase does on its own.

---

## 4. Trade Compliance Evaluation

### Supply chain audit trail requirements (customs regulations, trade agreements)

`AuditLogEntry` (written by `AuditLoggingInterceptor`, module-wide, on every
business method) is the general-purpose trail; `SecurityAuditInterceptor`'s
separate logger stream is the narrower, specifically security/compliance-sensitive
one (customs-document approval today). Both are **append-only by design** — see
`AuditLogEntry`'s own javadoc and the DDL comment in `db/schema.sql`: no service
method in this codebase updates or deletes an `AuditLogEntry` row, which matters
for compliance specifically because a mutable audit trail is not meaningfully an
audit trail at all (it can be edited to hide what actually happened). The same
same-transaction-write property discussed in
[`CRITICAL_ANALYSIS.md`](./CRITICAL_ANALYSIS.md#interceptor-performance-impact)
is itself a compliance property as much as a performance/consistency one: an audit
record that could exist for a transaction that never actually committed (or vice
versa) would be exactly the kind of inconsistency a customs audit would flag.

### International trade compliance measures

`CustomsDocumentationServiceBean` encodes the concrete compliance workflow this
system automates: a document cannot be submitted past its deadline or with a
shipment missing required origin/destination data (`validateReadyForSubmission`),
approval is a distinct, security-audited step from submission
(`approveDocument`'s `SecurityAuditInterceptor` binding), and
`finalizeShipmentCustomsClearance`'s `MANDATORY` transaction attribute enforces, at
the transaction-management level, that a shipment can never be marked cleared
independently of the order transaction that shipment's clearance was actually
required for — see
[`CRITICAL_ANALYSIS.md`'s discussion of that specific attribute choice](./CRITICAL_ANALYSIS.md#transaction-attribute-selection-for-different-logistics-scenarios).
`CustomsDeadlineTimerBean`'s programmatic, per-document timer (created the moment
a document with a real deadline exists) is the automated-monitoring half of
compliance: a document approaching its deadline is escalated (logged at `WARNING`/
`SEVERE` and recorded to the audit trail) without requiring a human to be watching
for it.

**Not built, and explicitly named as a gap**: this system does not currently model
country-specific or trade-agreement-specific document *requirements* (which
document types are mandatory for a CN→SE shipment vs. a CL→US shipment, tariff
classification, restricted-party screening). `CustomsDocumentType` is a fixed
enum of document *kinds*; deciding which kinds are actually required for a given
trade lane is business logic this reference implementation leaves to the
customs agent's own judgment when calling `fileDocument`, not something the system
enforces or suggests. A fuller implementation would introduce a rules table (trade
lane → required document types) that `fileDocument` or a dedicated
pre-check method consults, flagging a shipment as non-compliant if a required
document type has not been filed by some point in the workflow — named here as the
most consequential missing piece for genuine international-trade-compliance
completeness, rather than left unmentioned.

### Logistics data retention strategies

No table in this schema currently has an automated retention/purge policy — every
row, once written, persists indefinitely. For `AuditLogEntry` and
`CustomsDocument` specifically, this is very likely the *correct* default absent a
specific retention requirement narrowing it (trade/customs audit records
commonly have multi-year regulatory retention minimums, and deleting one early to
save storage is a materially worse failure mode than keeping one slightly longer
than strictly necessary). It is not obviously correct for `AuditLogEntry` rows
generated by high-frequency, low-compliance-value operations (a `trackShipment`
read, audited by the same module-wide interceptor as everything else) — those
rows have real storage cost at scale with comparatively little of the audit
table's compliance value concentrated in them specifically. Recommendation: a
retention policy differentiated by `AuditLogEntry.action`/`entityName` (short
retention, e.g. 90 days, for read-only/low-sensitivity actions; multi-year
retention for anything touching `CustomsDocument`, `Shipment` status changes, or
the `SECURITY_AUDIT` stream), implemented as a scheduled archival timer (moving
aged rows to cold storage rather than deleting them outright) following the same
`@Schedule` pattern the rest of this system's periodic work already uses. Not
built in this reference implementation because the actual retention *periods* are
a regulatory/legal determination specific to which jurisdictions' trade
regulations apply to a given deployment, not a technical decision this codebase
can make on GlobalTrade's behalf.

### Compliance monitoring mechanisms for global operations

`CustomsResource.approachingDeadlines` (REST) and
`CustomsDocumentationServiceBean.findApproachingDeadlines` (the service method
backing it) are today's compliance-monitoring surface: a customs agent or
coordinator can query "what needs attention in the next N hours" on demand.
Combined with `CustomsDeadlineTimerBean`'s automated escalation logging (above),
this covers reactive monitoring (something is approaching a deadline) but not yet
proactive compliance reporting (a periodic summary of overall compliance posture —
documents filed vs. required per the trade-lane rules table proposed above, average
time-to-approval, rejection rate by document type) that a trade-compliance officer
would want without having to construct that view themselves from raw
`CustomsDocument` rows. That reporting layer is a natural extension of the
scheduled-reporting-timer pattern proposed under
["Global trade security monitoring mechanisms"](#global-trade-security-monitoring-mechanisms)
above — the same mechanism (a `@Schedule` timer summarizing recent activity into a
digest) applies equally to security monitoring and compliance monitoring, and a
production system would very plausibly implement both as two configurations of one
shared reporting pattern rather than two independent pieces of code.

---

*See also: [`docs/CRITICAL_ANALYSIS.md`](./CRITICAL_ANALYSIS.md) for the
functional-requirements critical analysis, and
[`docs/DEPLOYMENT_GUIDE.md`](./DEPLOYMENT_GUIDE.md) for build and deployment
steps.*
