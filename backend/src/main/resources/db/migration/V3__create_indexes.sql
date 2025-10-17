-- ============================================================================
-- Planning Poker - Index Creation Migration
-- Version: V3
-- Description: Creates all indexes for optimal query performance
-- ============================================================================

-- ----------------------------------------------------------------------------
-- User Table Indexes
-- ----------------------------------------------------------------------------

-- OAuth login lookups
CREATE INDEX idx_user_email ON "user"(email);
CREATE INDEX idx_user_oauth_lookup ON "user"(oauth_provider, oauth_subject);

-- Active users query (excluding soft-deleted)
CREATE INDEX idx_user_active ON "user"(created_at DESC) WHERE deleted_at IS NULL;

-- Subscription tier filtering
CREATE INDEX idx_user_subscription_tier ON "user"(subscription_tier) WHERE deleted_at IS NULL;

-- ----------------------------------------------------------------------------
-- Organization Table Indexes
-- ----------------------------------------------------------------------------

-- Organization domain lookup
CREATE INDEX idx_organization_domain ON organization(domain);

-- Organization subscription lookup
CREATE INDEX idx_organization_subscription ON organization(subscription_id);

-- ----------------------------------------------------------------------------
-- OrgMember Table Indexes
-- ----------------------------------------------------------------------------

-- User's organizations lookup
CREATE INDEX idx_org_member_user ON org_member(user_id, joined_at DESC);

-- Organization members lookup
CREATE INDEX idx_org_member_org ON org_member(org_id, joined_at DESC);

-- Admin permission checks (composite partial index)
CREATE INDEX idx_org_member_admin ON org_member(user_id, org_id) WHERE role = 'ADMIN';

-- ----------------------------------------------------------------------------
-- Room Table Indexes
-- ----------------------------------------------------------------------------

-- User's recent rooms query (high-priority)
CREATE INDEX idx_room_owner_created ON room(owner_id, created_at DESC) WHERE deleted_at IS NULL;

-- Organization room listing (high-priority)
CREATE INDEX idx_room_org_active ON room(org_id, last_active_at DESC) WHERE deleted_at IS NULL;

-- Public room discovery (composite partial index)
CREATE INDEX idx_room_public_discovery ON room(privacy_mode, last_active_at DESC) WHERE deleted_at IS NULL;

-- Active rooms lookup
CREATE INDEX idx_room_last_active ON room(last_active_at DESC) WHERE deleted_at IS NULL;

-- Room privacy mode filtering
CREATE INDEX idx_room_privacy_mode ON room(privacy_mode, created_at DESC);

-- ----------------------------------------------------------------------------
-- RoomParticipant Table Indexes
-- ----------------------------------------------------------------------------

-- Active participants query (high-priority)
CREATE INDEX idx_room_participant_room ON room_participant(room_id, connected_at DESC) WHERE disconnected_at IS NULL;

-- User's active rooms
CREATE INDEX idx_room_participant_user ON room_participant(user_id, connected_at DESC) WHERE user_id IS NOT NULL AND disconnected_at IS NULL;

-- Participant role filtering
CREATE INDEX idx_room_participant_role ON room_participant(room_id, role);

-- ----------------------------------------------------------------------------
-- Round Table Indexes
-- ----------------------------------------------------------------------------

-- Round history retrieval (high-priority)
CREATE INDEX idx_round_room_number ON round(room_id, round_number DESC);

-- Round started time ordering
CREATE INDEX idx_round_started ON round(room_id, started_at DESC);

-- Revealed rounds query
CREATE INDEX idx_round_revealed ON round(room_id, revealed_at DESC) WHERE revealed_at IS NOT NULL;

-- Consensus filtering
CREATE INDEX idx_round_consensus ON round(room_id) WHERE consensus_reached = TRUE;

-- ----------------------------------------------------------------------------
-- Vote Table Indexes
-- ----------------------------------------------------------------------------

-- Vote aggregation for reveal (high-priority covering index)
CREATE INDEX idx_vote_round_participant ON vote(round_id, participant_id) INCLUDE (card_value);

-- Vote ordering by time (covering index)
CREATE INDEX idx_vote_round_voted ON vote(round_id, voted_at) INCLUDE (card_value);

-- Participant voting history
CREATE INDEX idx_vote_participant ON vote(participant_id, voted_at DESC);

-- ----------------------------------------------------------------------------
-- SessionHistory Table Indexes
-- ----------------------------------------------------------------------------

-- Partition pruning for date-range queries (high-priority)
CREATE INDEX idx_session_history_started ON session_history(started_at DESC);

-- Room session history
CREATE INDEX idx_session_history_room ON session_history(room_id, started_at DESC);

-- Story count filtering
CREATE INDEX idx_session_history_stories ON session_history(total_stories) WHERE total_stories > 0;

-- ----------------------------------------------------------------------------
-- Subscription Table Indexes
-- ----------------------------------------------------------------------------

-- Active subscription lookups (high-priority)
CREATE INDEX idx_subscription_entity ON subscription(entity_id, entity_type, status);

-- Stripe subscription ID lookup
CREATE INDEX idx_subscription_stripe ON subscription(stripe_subscription_id);

-- Subscription expiry monitoring
CREATE INDEX idx_subscription_period_end ON subscription(current_period_end) WHERE status = 'ACTIVE';

-- Canceled subscriptions
CREATE INDEX idx_subscription_canceled ON subscription(canceled_at DESC) WHERE canceled_at IS NOT NULL;

-- ----------------------------------------------------------------------------
-- PaymentHistory Table Indexes
-- ----------------------------------------------------------------------------

-- Payment lookup by subscription
CREATE INDEX idx_payment_subscription ON payment_history(subscription_id, paid_at DESC);

-- Stripe invoice lookup
CREATE INDEX idx_payment_stripe_invoice ON payment_history(stripe_invoice_id);

-- Payment status monitoring
CREATE INDEX idx_payment_status ON payment_history(status, created_at DESC);

-- Failed payments query
CREATE INDEX idx_payment_failed ON payment_history(subscription_id, created_at DESC) WHERE status = 'FAILED';

-- ----------------------------------------------------------------------------
-- AuditLog Table Indexes
-- ----------------------------------------------------------------------------

-- Enterprise audit trail queries (high-priority)
CREATE INDEX idx_audit_log_org_timestamp ON audit_log(org_id, timestamp DESC);

-- User activity audit
CREATE INDEX idx_audit_log_user_timestamp ON audit_log(user_id, timestamp DESC);

-- Action type filtering
CREATE INDEX idx_audit_log_action ON audit_log(action, timestamp DESC);

-- Resource audit trail
CREATE INDEX idx_audit_log_resource ON audit_log(resource_type, resource_id, timestamp DESC);

-- IP address tracking
CREATE INDEX idx_audit_log_ip ON audit_log(ip_address, timestamp DESC);

-- Recent audit events
CREATE INDEX idx_audit_log_timestamp ON audit_log(timestamp DESC);

-- ----------------------------------------------------------------------------
-- Additional Performance Indexes
-- ----------------------------------------------------------------------------

-- UserPreference lookup (should be fast via PK, but adding for completeness)
CREATE INDEX idx_user_preference_deck ON user_preference(default_deck_type);

-- Composite index for room discovery with multiple filters
CREATE INDEX idx_room_discovery_composite ON room(privacy_mode, org_id, last_active_at DESC) WHERE deleted_at IS NULL;

-- Vote card value analysis
CREATE INDEX idx_vote_card_value ON vote(round_id, card_value);

-- Round statistics queries
CREATE INDEX idx_round_stats ON round(room_id, revealed_at DESC) WHERE average IS NOT NULL;

-- ----------------------------------------------------------------------------
-- Index Statistics and Maintenance Notes
-- ----------------------------------------------------------------------------
--
-- PostgreSQL automatically maintains indexes during INSERT/UPDATE/DELETE operations.
-- However, periodic maintenance is recommended:
--
-- 1. Update statistics for query planner:
--    ANALYZE;  -- Run after bulk data loads
--
-- 2. Rebuild bloated indexes:
--    REINDEX INDEX CONCURRENTLY idx_name;  -- No downtime
--
-- 3. Monitor index usage:
--    SELECT schemaname, tablename, indexname, idx_scan, idx_tup_read, idx_tup_fetch
--    FROM pg_stat_user_indexes
--    WHERE schemaname = 'public'
--    ORDER BY idx_scan ASC;
--
-- 4. Identify unused indexes (candidates for removal):
--    SELECT schemaname, tablename, indexname
--    FROM pg_stat_user_indexes
--    WHERE idx_scan = 0 AND schemaname = 'public';
--
-- 5. Check index bloat:
--    SELECT schemaname, tablename, indexname, pg_size_pretty(pg_relation_size(indexrelid))
--    FROM pg_stat_user_indexes
--    WHERE schemaname = 'public'
--    ORDER BY pg_relation_size(indexrelid) DESC;
--
-- ----------------------------------------------------------------------------
