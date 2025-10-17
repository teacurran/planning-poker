-- ============================================================================
-- Planning Poker - Initial Schema Migration
-- Version: V1
-- Description: Creates ENUM types and all 11 core entity tables
-- ============================================================================

-- ----------------------------------------------------------------------------
-- ENUM Types
-- ----------------------------------------------------------------------------

CREATE TYPE subscription_tier_enum AS ENUM ('FREE', 'PRO', 'PRO_PLUS', 'ENTERPRISE');
CREATE TYPE subscription_status_enum AS ENUM ('ACTIVE', 'PAST_DUE', 'CANCELED', 'TRIALING');
CREATE TYPE privacy_mode_enum AS ENUM ('PUBLIC', 'INVITE_ONLY', 'ORG_RESTRICTED');
CREATE TYPE room_role_enum AS ENUM ('HOST', 'VOTER', 'OBSERVER');
CREATE TYPE org_role_enum AS ENUM ('ADMIN', 'MEMBER');
CREATE TYPE entity_type_enum AS ENUM ('USER', 'ORG');
CREATE TYPE payment_status_enum AS ENUM ('SUCCEEDED', 'PENDING', 'FAILED');

-- ----------------------------------------------------------------------------
-- Core Entity Tables (in dependency order)
-- ----------------------------------------------------------------------------

-- User: Registered user account with OAuth authentication
CREATE TABLE "user" (
    user_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL,
    oauth_provider VARCHAR(50) NOT NULL,
    oauth_subject VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    avatar_url VARCHAR(500),
    subscription_tier subscription_tier_enum NOT NULL DEFAULT 'FREE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_user_email UNIQUE (email),
    CONSTRAINT uq_user_oauth UNIQUE (oauth_provider, oauth_subject)
);

COMMENT ON TABLE "user" IS 'Registered user accounts with OAuth authentication';
COMMENT ON COLUMN "user".deleted_at IS 'Soft delete timestamp for audit trail and GDPR compliance';

-- Subscription: Stripe subscription record for users or organizations
CREATE TABLE subscription (
    subscription_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    stripe_subscription_id VARCHAR(100) NOT NULL,
    entity_id UUID NOT NULL,
    entity_type entity_type_enum NOT NULL,
    tier subscription_tier_enum NOT NULL DEFAULT 'FREE',
    status subscription_status_enum NOT NULL DEFAULT 'TRIALING',
    current_period_start TIMESTAMP WITH TIME ZONE NOT NULL,
    current_period_end TIMESTAMP WITH TIME ZONE NOT NULL,
    canceled_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_subscription_stripe_id UNIQUE (stripe_subscription_id),
    CONSTRAINT uq_subscription_entity UNIQUE (entity_id, entity_type)
);

COMMENT ON TABLE subscription IS 'Stripe subscription records for users and organizations';
COMMENT ON COLUMN subscription.entity_type IS 'Polymorphic reference: USER or ORG';

-- Organization: Enterprise SSO workspace
CREATE TABLE organization (
    org_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    domain VARCHAR(255) NOT NULL,
    sso_config JSONB,
    branding JSONB,
    subscription_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_organization_domain UNIQUE (domain),
    CONSTRAINT fk_organization_subscription FOREIGN KEY (subscription_id)
        REFERENCES subscription(subscription_id) ON DELETE RESTRICT
);

COMMENT ON TABLE organization IS 'Enterprise SSO workspaces with custom branding';
COMMENT ON COLUMN organization.sso_config IS 'JSONB: OIDC/SAML2 configuration';
COMMENT ON COLUMN organization.branding IS 'JSONB: logo_url, primary_color, secondary_color';

-- UserPreference: Saved user defaults and settings
CREATE TABLE user_preference (
    user_id UUID PRIMARY KEY,
    default_deck_type VARCHAR(50),
    default_room_config JSONB,
    theme VARCHAR(20) DEFAULT 'light',
    notification_settings JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_preference_user FOREIGN KEY (user_id)
        REFERENCES "user"(user_id) ON DELETE CASCADE
);

COMMENT ON TABLE user_preference IS 'User-specific preferences and default settings';
COMMENT ON COLUMN user_preference.default_room_config IS 'JSONB: deck_type, timer_enabled, reveal_behavior';
COMMENT ON COLUMN user_preference.notification_settings IS 'JSONB: email_enabled, push_enabled, notification_types';

-- OrgMember: User-organization membership with role
CREATE TABLE org_member (
    org_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role org_role_enum NOT NULL DEFAULT 'MEMBER',
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (org_id, user_id),
    CONSTRAINT fk_org_member_org FOREIGN KEY (org_id)
        REFERENCES organization(org_id) ON DELETE CASCADE,
    CONSTRAINT fk_org_member_user FOREIGN KEY (user_id)
        REFERENCES "user"(user_id) ON DELETE CASCADE
);

COMMENT ON TABLE org_member IS 'Many-to-many relationship between users and organizations';

-- PaymentHistory: Payment transaction log
CREATE TABLE payment_history (
    payment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id UUID NOT NULL,
    stripe_invoice_id VARCHAR(100) NOT NULL,
    amount INTEGER NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    status payment_status_enum NOT NULL,
    paid_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payment_subscription FOREIGN KEY (subscription_id)
        REFERENCES subscription(subscription_id) ON DELETE CASCADE,
    CONSTRAINT uq_payment_stripe_invoice UNIQUE (stripe_invoice_id)
);

COMMENT ON TABLE payment_history IS 'Immutable payment transaction log from Stripe';
COMMENT ON COLUMN payment_history.amount IS 'Amount in cents (e.g., 1999 = $19.99)';

-- Room: Estimation session
CREATE TABLE room (
    room_id VARCHAR(6) PRIMARY KEY,
    owner_id UUID,
    org_id UUID,
    title VARCHAR(255) NOT NULL,
    privacy_mode privacy_mode_enum NOT NULL DEFAULT 'PUBLIC',
    config JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_active_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_room_owner FOREIGN KEY (owner_id)
        REFERENCES "user"(user_id) ON DELETE SET NULL,
    CONSTRAINT fk_room_org FOREIGN KEY (org_id)
        REFERENCES organization(org_id) ON DELETE SET NULL,
    CONSTRAINT chk_room_id_length CHECK (LENGTH(room_id) = 6)
);

COMMENT ON TABLE room IS 'Estimation sessions with 6-character nanoid identifiers';
COMMENT ON COLUMN room.room_id IS 'Short 6-character nanoid for shareable URLs (e.g., abc123)';
COMMENT ON COLUMN room.owner_id IS 'Nullable to support anonymous room creation';
COMMENT ON COLUMN room.config IS 'JSONB: deck_type, timer_enabled, timer_duration_seconds, reveal_behavior, allow_observers';
COMMENT ON COLUMN room.deleted_at IS 'Soft delete timestamp for audit trail';

-- RoomParticipant: Active session participants
CREATE TABLE room_participant (
    participant_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id VARCHAR(6) NOT NULL,
    user_id UUID,
    anonymous_id VARCHAR(50),
    display_name VARCHAR(100) NOT NULL,
    role room_role_enum NOT NULL DEFAULT 'VOTER',
    connected_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    disconnected_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_room_participant_room FOREIGN KEY (room_id)
        REFERENCES room(room_id) ON DELETE CASCADE,
    CONSTRAINT fk_room_participant_user FOREIGN KEY (user_id)
        REFERENCES "user"(user_id) ON DELETE SET NULL,
    CONSTRAINT chk_room_participant_identity CHECK (
        (user_id IS NOT NULL AND anonymous_id IS NULL) OR
        (user_id IS NULL AND anonymous_id IS NOT NULL)
    ),
    CONSTRAINT uq_room_participant_room_user UNIQUE (room_id, user_id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT uq_room_participant_room_anon UNIQUE (room_id, anonymous_id) DEFERRABLE INITIALLY DEFERRED
);

COMMENT ON TABLE room_participant IS 'Active participants in estimation sessions';
COMMENT ON COLUMN room_participant.participant_id IS 'Surrogate UUID primary key for participant identity';
COMMENT ON COLUMN room_participant.user_id IS 'NULL for anonymous participants';
COMMENT ON COLUMN room_participant.anonymous_id IS 'Required when user_id IS NULL for anonymous guests';

-- Round: Estimation round within session
CREATE TABLE round (
    round_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id VARCHAR(6) NOT NULL,
    round_number INTEGER NOT NULL,
    story_title VARCHAR(500),
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revealed_at TIMESTAMP WITH TIME ZONE,
    average NUMERIC(5,2),
    median VARCHAR(10),
    consensus_reached BOOLEAN DEFAULT FALSE,
    CONSTRAINT fk_round_room FOREIGN KEY (room_id)
        REFERENCES room(room_id) ON DELETE CASCADE,
    CONSTRAINT uq_round_number UNIQUE (room_id, round_number)
);

COMMENT ON TABLE round IS 'Individual estimation rounds within a room session';
COMMENT ON COLUMN round.median IS 'VARCHAR to support non-numeric cards (?, ∞, ☕)';

-- Vote: Individual estimation vote
CREATE TABLE vote (
    vote_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    round_id UUID NOT NULL,
    participant_id UUID NOT NULL,
    card_value VARCHAR(10) NOT NULL,
    voted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_vote_round FOREIGN KEY (round_id)
        REFERENCES round(round_id) ON DELETE CASCADE,
    CONSTRAINT fk_vote_participant FOREIGN KEY (participant_id)
        REFERENCES room_participant(participant_id) ON DELETE CASCADE,
    CONSTRAINT uq_vote_participant UNIQUE (round_id, participant_id)
);

COMMENT ON TABLE vote IS 'Individual votes cast by participants in estimation rounds';
COMMENT ON COLUMN vote.participant_id IS 'Foreign key to room_participant.participant_id (UUID)';
COMMENT ON COLUMN vote.card_value IS 'Deck card value (0, 1, 2, 3, 5, 8, 13, ?, ∞, ☕)';

-- SessionHistory: Completed session record (partitioned table)
CREATE TABLE session_history (
    session_id UUID DEFAULT gen_random_uuid(),
    room_id VARCHAR(6) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ended_at TIMESTAMP WITH TIME ZONE NOT NULL,
    total_rounds INTEGER NOT NULL,
    total_stories INTEGER NOT NULL,
    participants JSONB NOT NULL,
    summary_stats JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (session_id, started_at),
    CONSTRAINT fk_session_room FOREIGN KEY (room_id)
        REFERENCES room(room_id) ON DELETE CASCADE
) PARTITION BY RANGE (started_at);

COMMENT ON TABLE session_history IS 'Completed session records partitioned by month for performance';
COMMENT ON COLUMN session_history.participants IS 'JSONB array of participant snapshots';
COMMENT ON COLUMN session_history.summary_stats IS 'JSONB: avg_estimation_time, consensus_rate, total_votes';

-- AuditLog: Compliance and security audit trail (partitioned table)
CREATE TABLE audit_log (
    log_id UUID DEFAULT gen_random_uuid(),
    org_id UUID,
    user_id UUID,
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    resource_id VARCHAR(100),
    ip_address INET,
    user_agent VARCHAR(500),
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB,
    PRIMARY KEY (log_id, timestamp),
    CONSTRAINT fk_audit_org FOREIGN KEY (org_id)
        REFERENCES organization(org_id) ON DELETE SET NULL,
    CONSTRAINT fk_audit_user FOREIGN KEY (user_id)
        REFERENCES "user"(user_id) ON DELETE SET NULL
) PARTITION BY RANGE (timestamp);

COMMENT ON TABLE audit_log IS 'Immutable audit trail for compliance and security monitoring';
COMMENT ON COLUMN audit_log.ip_address IS 'INET type for proper IPv4/IPv6 storage';
COMMENT ON COLUMN audit_log.metadata IS 'JSONB: additional context for the audited action';
