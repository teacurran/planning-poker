# System Architecture Blueprint: Scrum Poker Platform

---

<!-- anchor: system-context-diagram -->
### 3.3. System Context Diagram (C4 Level 1)

#### Description

The System Context Diagram illustrates the Scrum Poker Platform within its operational environment, showing the primary actors (users and external systems) and their interactions with the system boundary. This high-level view establishes the scope of the platform and its integration points with third-party services.

**Key Elements:**
- **Anonymous Players:** Users who join estimation sessions without authentication via shareable room links
- **Authenticated Users:** Registered users with Google/Microsoft OAuth2 accounts accessing premium features
- **Organization Admins:** Enterprise tier users managing SSO-enabled organizations and team members
- **External Systems:** OAuth2 providers (Google, Microsoft), SSO identity providers (OIDC/SAML2), Stripe payment gateway, and email delivery services

#### Diagram (PlantUML)

~~~plantuml
@startuml
!include https://raw.githubusercontent.com/plantuml-stdlib/C4-PlantUML/master/C4_Context.puml

LAYOUT_WITH_LEGEND()

title System Context Diagram - Scrum Poker Platform

Person(anonymous_player, "Anonymous Player", "Joins estimation sessions without registration")
Person(authenticated_user, "Authenticated User", "Registered user with saved preferences and session history")
Person(org_admin, "Organization Admin", "Enterprise administrator managing SSO org and members")

System_Boundary(scrum_poker_boundary, "Scrum Poker Platform") {
  System(scrum_poker_app, "Scrum Poker Application", "Real-time collaborative estimation platform with WebSocket-based gameplay, user management, and subscription billing")
}

System_Ext(google_oauth, "Google OAuth2", "Identity provider for social login")
System_Ext(microsoft_oauth, "Microsoft OAuth2", "Identity provider for social login")
System_Ext(sso_provider, "SSO Identity Provider", "Enterprise OIDC/SAML2 provider (Okta, Azure AD)")
System_Ext(stripe, "Stripe Payment Gateway", "Subscription billing and payment processing")
System_Ext(email_service, "Email Service", "Transactional email delivery (SendGrid/SES)")
System_Ext(marketing_site, "Marketing Website", "Static site with pricing, demo, blog content")

Rel(anonymous_player, scrum_poker_app, "Joins rooms, casts votes", "HTTPS/WSS")
Rel(authenticated_user, scrum_poker_app, "Manages preferences, views reports, creates rooms", "HTTPS/WSS")
Rel(org_admin, scrum_poker_app, "Configures org settings, views dashboards", "HTTPS/WSS")

Rel(scrum_poker_app, google_oauth, "Authenticates users via", "OAuth2/HTTPS")
Rel(scrum_poker_app, microsoft_oauth, "Authenticates users via", "OAuth2/HTTPS")
Rel(scrum_poker_app, sso_provider, "Federates identity via", "OIDC/SAML2 over HTTPS")
Rel(scrum_poker_app, stripe, "Processes payments, manages subscriptions", "Stripe API/HTTPS")
Rel(scrum_poker_app, email_service, "Sends notifications, receipts", "SMTP/API")

Rel(anonymous_player, marketing_site, "Learns about product, views pricing", "HTTPS")
Rel(authenticated_user, marketing_site, "Accesses help docs, blog", "HTTPS")
Rel_Back(marketing_site, scrum_poker_app, "Links to app for trial/signup", "HTTPS")

@enduml
~~~

---

<!-- anchor: container-diagram -->
### 3.4. Container Diagram (C4 Level 2)

#### Description

The Container Diagram decomposes the Scrum Poker Platform into its major deployable units and runtime environments. This view reveals the physical distribution of application components, data stores, and their primary communication pathways.

**Key Containers:**
- **Web Application (SPA):** React-based single-page application served via CDN, communicating with backend via REST and WebSocket
- **API Gateway / Load Balancer:** Kubernetes Ingress or cloud load balancer providing TLS termination and sticky session routing
- **Quarkus Application (Reactive):** Core backend containing business logic, WebSocket handlers, REST controllers, and integration adapters
- **PostgreSQL Database:** Primary persistent store for users, rooms, votes, subscriptions, and audit logs
- **Redis Cluster:** In-memory cache for session state, WebSocket message broadcasting (Pub/Sub), and transient room data
- **Background Job Processor:** Asynchronous worker consuming Redis Streams for report generation, email notifications, and analytics aggregation
- **Static Marketing Site:** Separate deployment (Next.js/Astro) for SEO-optimized content and conversion funnels

#### Diagram (PlantUML)

~~~plantuml
@startuml
!include https://raw.githubusercontent.com/plantuml-stdlib/C4-PlantUML/master/C4_Container.puml

LAYOUT_WITH_LEGEND()

title Container Diagram - Scrum Poker Platform

Person(player, "Player/User", "Anonymous or authenticated participant")
Person(admin, "Organization Admin", "Enterprise administrator")

System_Ext(google_oauth, "Google OAuth2")
System_Ext(microsoft_oauth, "Microsoft OAuth2")
System_Ext(sso_provider, "SSO Provider (OIDC/SAML2)")
System_Ext(stripe, "Stripe API")
System_Ext(email_service, "Email Service (SendGrid/SES)")

System_Boundary(scrum_poker_boundary, "Scrum Poker Platform") {
  Container(cdn, "CDN / Static Hosting", "CloudFront/Cloud CDN", "Serves React SPA bundle, static assets")
  Container(web_app, "Web Application (SPA)", "React 18, TypeScript, Tailwind CSS", "User interface for gameplay, settings, reports")
  Container(ingress, "Load Balancer / Ingress", "Kubernetes Ingress (Nginx/Traefik)", "TLS termination, sticky session routing, health checks")
  Container(api_app, "Quarkus Application", "Java 17, Quarkus Reactive, Vert.x", "REST APIs, WebSocket handlers, business logic, integrations")
  ContainerDb(postgres, "PostgreSQL Database", "PostgreSQL 15", "Persistent storage: users, rooms, votes, sessions, subscriptions, orgs, audit logs")
  ContainerDb(redis, "Redis Cluster", "Redis 7", "Session cache, WebSocket Pub/Sub, rate limiting, transient room state")
  Container(background_worker, "Background Job Processor", "Quarkus (worker mode)", "Async jobs: report generation, email dispatch, analytics aggregation")
  Container(object_storage, "Object Storage", "S3/Cloud Storage", "Exported reports (CSV/PDF), user avatars, org logos")
}

Container_Ext(marketing_site, "Marketing Website", "Next.js/Astro", "SEO content, pricing, blog, demo links")

Rel(player, cdn, "Loads SPA", "HTTPS")
Rel(cdn, web_app, "Delivers", "Static files")
Rel(player, web_app, "Interacts with")
Rel(admin, web_app, "Configures org, views dashboards")

Rel(web_app, ingress, "API requests, WebSocket connections", "HTTPS/WSS")
Rel(ingress, api_app, "Routes traffic (sticky sessions)", "HTTP/WS")

Rel(api_app, postgres, "Reads/writes data", "Reactive PostgreSQL driver")
Rel(api_app, redis, "Caches sessions, publishes WebSocket events", "Redis client")
Rel(api_app, google_oauth, "Authenticates users", "OAuth2/HTTPS")
Rel(api_app, microsoft_oauth, "Authenticates users", "OAuth2/HTTPS")
Rel(api_app, sso_provider, "Federates enterprise users", "OIDC/SAML2")
Rel(api_app, stripe, "Manages subscriptions, processes payments", "Stripe SDK/HTTPS")
Rel(api_app, object_storage, "Stores/retrieves files", "S3 API")

Rel(redis, api_app, "Broadcasts messages to nodes", "Pub/Sub")
Rel(redis, background_worker, "Delivers job messages", "Redis Streams")

Rel(background_worker, postgres, "Reads session data, writes reports", "JDBC")
Rel(background_worker, email_service, "Sends emails", "SMTP/API")
Rel(background_worker, object_storage, "Uploads reports", "S3 API")

Rel(player, marketing_site, "Discovers product", "HTTPS")
Rel_Back(marketing_site, web_app, "Links to app", "HTTPS")

@enduml
~~~

---

<!-- anchor: component-diagram -->
### 3.5. Component Diagram(s) (C4 Level 3 or UML)

#### Description

This Component Diagram zooms into the **Quarkus Application** container to reveal its internal modular structure. The application follows a hexagonal (ports and adapters) architecture with clear separation between domain logic, infrastructure, and API layers.

**Key Modules:**
- **REST Controllers:** HTTP endpoint handlers exposing RESTful APIs for user management, room CRUD, subscriptions, and reporting
- **WebSocket Handlers:** Real-time connection managers processing vote events, room state changes, and participant actions
- **Domain Services:** Core business logic implementing estimation rules, room lifecycle, user preferences, billing logic
- **Repository Layer:** Data access abstractions using Hibernate Reactive Panache for PostgreSQL interactions
- **Integration Adapters:** External service clients (OAuth2, Stripe, email) following the adapter pattern
- **Event Publisher:** Redis Pub/Sub integration for broadcasting WebSocket messages across application nodes

#### Diagram (PlantUML)

~~~plantuml
@startuml
!include https://raw.githubusercontent.com/plantuml-stdlib/C4-PlantUML/master/C4_Component.puml

LAYOUT_TOP_DOWN()

title Component Diagram - Quarkus Application Container

Container_Boundary(api_app, "Quarkus Application") {
  Component(rest_controllers, "REST Controllers", "JAX-RS / Quarkus RESTEasy Reactive", "HTTP endpoints: /api/users, /api/rooms, /api/subscriptions, /api/reports")
  Component(websocket_handlers, "WebSocket Handlers", "Quarkus WebSockets", "Real-time handlers: /ws/room/{roomId}, connection lifecycle, message routing")
  Component(auth_filter, "Authentication Filter", "Quarkus Security", "JWT validation, OAuth2 token exchange, session creation")

  Component(user_service, "User Service", "Domain Logic", "User registration, profile management, preference storage")
  Component(room_service, "Room Service", "Domain Logic", "Room creation, join logic, deck configuration, privacy controls")
  Component(voting_service, "Voting Service", "Domain Logic", "Vote casting, reveal logic, consensus calculation, round lifecycle")
  Component(billing_service, "Billing Service", "Domain Logic", "Subscription tier enforcement, feature gating, Stripe webhook handling")
  Component(reporting_service, "Reporting Service", "Domain Logic", "Session aggregation, analytics queries, export generation")
  Component(org_service, "Organization Service", "Domain Logic", "Org creation, SSO config, admin controls, member management")

  Component(user_repository, "User Repository", "Panache Repository", "User, UserPreference entity persistence")
  Component(room_repository, "Room Repository", "Panache Repository", "Room, RoomConfig, Vote entity persistence")
  Component(session_repository, "Session Repository", "Panache Repository", "SessionHistory, Round, VoteRecord persistence")
  Component(subscription_repository, "Subscription Repository", "Panache Repository", "Subscription, PaymentHistory persistence")
  Component(org_repository, "Organization Repository", "Panache Repository", "Organization, OrgMember, SSOConfig persistence")

  Component(oauth_adapter, "OAuth2 Adapter", "Integration Adapter", "Google/Microsoft OAuth2 client, token validation")
  Component(sso_adapter, "SSO Adapter", "Integration Adapter", "OIDC/SAML2 protocol handler, identity federation")
  Component(stripe_adapter, "Stripe Adapter", "Integration Adapter", "Stripe API client, subscription CRUD, webhook verification")
  Component(email_adapter, "Email Adapter", "Integration Adapter", "SendGrid/SES client, template rendering")

  Component(event_publisher, "Event Publisher", "Redis Pub/Sub Client", "Publishes WebSocket events to channel: room:{roomId}")
  Component(event_subscriber, "Event Subscriber", "Redis Pub/Sub Client", "Subscribes to channels, routes to WebSocket connections")
  Component(cache_manager, "Cache Manager", "Redis Client", "Session caching, rate limiting, room state TTL")
  Component(job_queue, "Job Queue Producer", "Redis Streams", "Enqueues async jobs: report generation, email notifications")
}

ContainerDb(postgres, "PostgreSQL Database")
ContainerDb(redis, "Redis Cluster")
System_Ext(google_oauth, "Google OAuth2")
System_Ext(stripe, "Stripe API")
System_Ext(email_service, "Email Service")

Rel(rest_controllers, auth_filter, "Passes requests through")
Rel(websocket_handlers, auth_filter, "Validates WebSocket handshake")

Rel(auth_filter, oauth_adapter, "Exchanges OAuth2 tokens")
Rel(auth_filter, sso_adapter, "Validates SAML/OIDC assertions")

Rel(rest_controllers, user_service, "Invokes")
Rel(rest_controllers, room_service, "Invokes")
Rel(rest_controllers, billing_service, "Invokes")
Rel(rest_controllers, reporting_service, "Invokes")
Rel(rest_controllers, org_service, "Invokes")

Rel(websocket_handlers, room_service, "Invokes")
Rel(websocket_handlers, voting_service, "Invokes")
Rel(websocket_handlers, event_subscriber, "Receives broadcasted events from")
Rel(websocket_handlers, cache_manager, "Reads room state from")

Rel(user_service, user_repository, "Persists via")
Rel(room_service, room_repository, "Persists via")
Rel(voting_service, room_repository, "Persists via")
Rel(voting_service, session_repository, "Persists via")
Rel(billing_service, subscription_repository, "Persists via")
Rel(reporting_service, session_repository, "Queries via")
Rel(org_service, org_repository, "Persists via")

Rel(voting_service, event_publisher, "Publishes vote/reveal events")
Rel(room_service, event_publisher, "Publishes room state changes")
Rel(event_publisher, redis, "Publishes to Pub/Sub channel")
Rel(redis, event_subscriber, "Delivers messages to")

Rel(billing_service, stripe_adapter, "Manages subscriptions via")
Rel(billing_service, job_queue, "Enqueues receipt emails")
Rel(reporting_service, job_queue, "Enqueues export jobs")

Rel(user_repository, postgres, "Executes SQL via Reactive driver")
Rel(room_repository, postgres, "Executes SQL")
Rel(session_repository, postgres, "Executes SQL")
Rel(subscription_repository, postgres, "Executes SQL")
Rel(org_repository, postgres, "Executes SQL")

Rel(oauth_adapter, google_oauth, "Calls OAuth2 API")
Rel(stripe_adapter, stripe, "Calls Stripe API")
Rel(email_adapter, email_service, "Sends emails via API")
Rel(job_queue, redis, "Writes to Redis Stream")

@enduml
~~~

---

<!-- anchor: data-model-overview-erd -->
### 3.6. Data Model Overview & ERD

#### Description

The data model follows a relational schema leveraging PostgreSQL's ACID properties for transactional consistency and JSONB columns for flexible configuration storage (room settings, deck definitions). The model is optimized for both transactional writes (vote casting, room creation) and analytical reads (session history, organizational reporting).

**Design Principles:**
1. **Normalized Core Entities:** Users, Rooms, Organizations follow 3NF to prevent update anomalies
2. **Denormalized Read Models:** SessionSummary and VoteStatistics tables precompute aggregations for reporting performance
3. **JSONB for Flexibility:** RoomConfig, DeckDefinition, UserPreferences stored as JSONB to support customization without schema migrations
4. **Soft Deletes:** Critical entities (Users, Rooms) use `deleted_at` timestamp for audit trail and GDPR compliance
5. **Partitioning Strategy:** SessionHistory and AuditLog partitioned by month for query performance and data lifecycle management

#### Key Entities

| Entity | Purpose | Key Attributes |
|--------|---------|----------------|
| **User** | Registered user account | `user_id` (PK), `email`, `oauth_provider`, `oauth_subject`, `display_name`, `avatar_url`, `subscription_tier`, `created_at` |
| **UserPreference** | Saved user defaults | `user_id` (FK), `default_deck_type`, `default_room_config` (JSONB), `theme`, `notification_settings` (JSONB) |
| **Organization** | Enterprise SSO workspace | `org_id` (PK), `name`, `domain`, `sso_config` (JSONB: OIDC/SAML2 settings), `branding` (JSONB), `subscription_id` (FK) |
| **OrgMember** | User-organization membership | `org_id` (FK), `user_id` (FK), `role` (ADMIN/MEMBER), `joined_at` |
| **Room** | Estimation session | `room_id` (PK, nanoid 6-char), `owner_id` (FK nullable for anonymous), `org_id` (FK nullable), `title`, `privacy_mode` (PUBLIC/INVITE_ONLY/ORG_RESTRICTED), `config` (JSONB: deck, rules, timer), `created_at`, `last_active_at` |
| **RoomParticipant** | Active session participants | `room_id` (FK), `user_id` (FK nullable), `anonymous_id`, `display_name`, `role` (HOST/VOTER/OBSERVER), `connected_at` |
| **Vote** | Individual estimation vote | `vote_id` (PK), `room_id` (FK), `round_number`, `participant_id`, `card_value`, `voted_at` |
| **Round** | Estimation round within session | `round_id` (PK), `room_id` (FK), `round_number`, `story_title`, `started_at`, `revealed_at`, `average`, `median`, `consensus_reached` |
| **SessionHistory** | Completed session record | `session_id` (PK), `room_id` (FK), `started_at`, `ended_at`, `total_rounds`, `total_stories`, `participants` (JSONB array), `summary_stats` (JSONB) |
| **Subscription** | Stripe subscription record | `subscription_id` (PK), `stripe_subscription_id`, `entity_id` (user_id or org_id), `entity_type` (USER/ORG), `tier` (FREE/PRO/PRO_PLUS/ENTERPRISE), `status`, `current_period_end`, `canceled_at` |
| **PaymentHistory** | Payment transaction log | `payment_id` (PK), `subscription_id` (FK), `stripe_invoice_id`, `amount`, `currency`, `status`, `paid_at` |
| **AuditLog** | Compliance and security audit trail | `log_id` (PK), `org_id` (FK nullable), `user_id` (FK nullable), `action`, `resource_type`, `resource_id`, `ip_address`, `user_agent`, `timestamp` |

#### Entity Relationship Diagram (PlantUML)

~~~plantuml
@startuml

' User and Authentication
entity User {
  *user_id : UUID <<PK>>
  --
  email : VARCHAR(255) <<UNIQUE>>
  oauth_provider : VARCHAR(50)
  oauth_subject : VARCHAR(255)
  display_name : VARCHAR(100)
  avatar_url : VARCHAR(500)
  subscription_tier : ENUM(FREE, PRO)
  created_at : TIMESTAMP
  deleted_at : TIMESTAMP
}

entity UserPreference {
  *user_id : UUID <<PK, FK>>
  --
  default_deck_type : VARCHAR(50)
  default_room_config : JSONB
  theme : VARCHAR(20)
  notification_settings : JSONB
}

' Organization and Membership
entity Organization {
  *org_id : UUID <<PK>>
  --
  name : VARCHAR(200)
  domain : VARCHAR(100)
  sso_config : JSONB
  branding : JSONB
  subscription_id : UUID <<FK>>
  created_at : TIMESTAMP
}

entity OrgMember {
  *org_id : UUID <<PK, FK>>
  *user_id : UUID <<PK, FK>>
  --
  role : ENUM(ADMIN, MEMBER)
  joined_at : TIMESTAMP
}

' Room and Session
entity Room {
  *room_id : VARCHAR(6) <<PK>>
  --
  owner_id : UUID <<FK>> nullable
  org_id : UUID <<FK>> nullable
  title : VARCHAR(200)
  privacy_mode : ENUM(PUBLIC, INVITE_ONLY, ORG_RESTRICTED)
  config : JSONB
  created_at : TIMESTAMP
  last_active_at : TIMESTAMP
  deleted_at : TIMESTAMP
}

entity RoomParticipant {
  *participant_id : UUID <<PK>>
  --
  room_id : VARCHAR(6) <<FK>>
  user_id : UUID <<FK>> nullable
  anonymous_id : VARCHAR(50)
  display_name : VARCHAR(100)
  role : ENUM(HOST, VOTER, OBSERVER)
  connected_at : TIMESTAMP
  disconnected_at : TIMESTAMP
}

entity Round {
  *round_id : UUID <<PK>>
  --
  room_id : VARCHAR(6) <<FK>>
  round_number : INTEGER
  story_title : VARCHAR(500)
  started_at : TIMESTAMP
  revealed_at : TIMESTAMP
  average : DECIMAL(5,2)
  median : VARCHAR(10)
  consensus_reached : BOOLEAN
}

entity Vote {
  *vote_id : UUID <<PK>>
  --
  round_id : UUID <<FK>>
  participant_id : UUID <<FK>>
  card_value : VARCHAR(10)
  voted_at : TIMESTAMP
}

entity SessionHistory {
  *session_id : UUID <<PK>>
  --
  room_id : VARCHAR(6) <<FK>>
  started_at : TIMESTAMP <<PARTITION KEY>>
  ended_at : TIMESTAMP
  total_rounds : INTEGER
  total_stories : INTEGER
  participants : JSONB
  summary_stats : JSONB
}

' Billing
entity Subscription {
  *subscription_id : UUID <<PK>>
  --
  stripe_subscription_id : VARCHAR(100) <<UNIQUE>>
  entity_id : UUID
  entity_type : ENUM(USER, ORG)
  tier : ENUM(FREE, PRO, PRO_PLUS, ENTERPRISE)
  status : VARCHAR(50)
  current_period_end : TIMESTAMP
  canceled_at : TIMESTAMP
  created_at : TIMESTAMP
}

entity PaymentHistory {
  *payment_id : UUID <<PK>>
  --
  subscription_id : UUID <<FK>>
  stripe_invoice_id : VARCHAR(100)
  amount : INTEGER
  currency : VARCHAR(3)
  status : VARCHAR(50)
  paid_at : TIMESTAMP
}

' Audit
entity AuditLog {
  *log_id : UUID <<PK>>
  --
  org_id : UUID <<FK>> nullable
  user_id : UUID <<FK>> nullable
  action : VARCHAR(100)
  resource_type : VARCHAR(50)
  resource_id : VARCHAR(100)
  ip_address : INET
  user_agent : TEXT
  timestamp : TIMESTAMP <<PARTITION KEY>>
}

' Relationships
User ||--o| UserPreference : has
User ||--o{ OrgMember : belongs_to
User ||--o{ Room : owns
User ||--o| Subscription : subscribes

Organization ||--o{ OrgMember : contains
Organization ||--o{ Room : restricts
Organization ||--|| Subscription : pays_via
Organization ||--o{ AuditLog : generates

Room ||--o{ RoomParticipant : hosts
Room ||--o{ Round : contains
Room ||--o{ SessionHistory : records

Round ||--o{ Vote : collects
RoomParticipant ||--o{ Vote : casts

Subscription ||--o{ PaymentHistory : has

User ||--o{ AuditLog : performs

@enduml
~~~

#### Database Indexing Strategy

**High-Priority Indexes:**
- `User(email)` - OAuth login lookups
- `User(oauth_provider, oauth_subject)` - OAuth subject resolution
- `Room(owner_id, created_at DESC)` - User's recent rooms query
- `Room(org_id, last_active_at DESC)` - Organization room listing
- `RoomParticipant(room_id, connected_at)` - Active participants query
- `Vote(round_id, participant_id)` - Vote aggregation for reveal
- `Round(room_id, round_number)` - Round history retrieval
- `SessionHistory(started_at)` - Partition pruning for date-range queries
- `Subscription(entity_id, entity_type, status)` - Active subscription lookups
- `AuditLog(org_id, timestamp DESC)` - Enterprise audit trail queries

**Composite Indexes:**
- `Room(privacy_mode, last_active_at DESC) WHERE deleted_at IS NULL` - Public room discovery
- `OrgMember(user_id, org_id) WHERE role = 'ADMIN'` - Admin permission checks
- `Vote(round_id, voted_at) INCLUDE (card_value)` - Covering index for vote ordering

**Partitioning:**
- `SessionHistory` partitioned by `started_at` (monthly range partitions)
- `AuditLog` partitioned by `timestamp` (monthly range partitions)
- Automated partition creation via scheduled job or pg_partman extension
