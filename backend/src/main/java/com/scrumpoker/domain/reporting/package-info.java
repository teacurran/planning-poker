/**
 * Reporting domain layer containing session history tracking and analytics
 * services, DTOs, and related components.
 *
 * <p>This package provides:
 * <ul>
 *   <li>SessionHistoryService for querying past sessions</li>
 *   <li>ParticipantSummary POJO for JSONB participant data</li>
 *   <li>SessionSummaryStats POJO for JSONB statistics</li>
 *   <li>Tier-based reporting (basic for Free, detailed for Pro/Enterprise)
 *   </li>
 *   <li>Partition-optimized queries for historical data</li>
 * </ul>
 */
package com.scrumpoker.domain.reporting;
