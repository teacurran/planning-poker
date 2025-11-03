package com.scrumpoker.domain.billing;

import com.scrumpoker.domain.user.SubscriptionTier;
import com.scrumpoker.domain.user.User;
import com.scrumpoker.integration.stripe.StripeAdapter;
import com.scrumpoker.integration.stripe.StripeException;
import com.scrumpoker.repository.SubscriptionRepository;
import com.scrumpoker.repository.UserRepository;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for BillingService using mocked dependencies.
 * Tests all subscription lifecycle management operations in isolation
 * without database or Stripe API dependencies.
 */
@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    @Mock
    SubscriptionRepository subscriptionRepository;

    @Mock
    StripeAdapter stripeAdapter;

    @Mock
    UserRepository userRepository;

    @InjectMocks
    BillingService billingService;

    private User testUser;
    private UUID testUserId;
    private Subscription testSubscription;
    private String testStripeSubscriptionId;
    private Instant testPeriodStart;
    private Instant testPeriodEnd;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testStripeSubscriptionId = "sub_test123";

        // Create test user with FREE tier
        testUser = new User();
        testUser.userId = testUserId;
        testUser.email = "test@example.com";
        testUser.displayName = "Test User";
        testUser.oauthProvider = "google";
        testUser.oauthSubject = "oauth-subject-123";
        testUser.subscriptionTier = SubscriptionTier.FREE;
        testUser.createdAt = Instant.now();
        testUser.updatedAt = Instant.now();

        // Create test subscription
        testPeriodStart = Instant.now();
        testPeriodEnd = testPeriodStart.plus(30, ChronoUnit.DAYS);

        testSubscription = new Subscription();
        testSubscription.subscriptionId = UUID.randomUUID();
        testSubscription.stripeSubscriptionId = testStripeSubscriptionId;
        testSubscription.entityId = testUserId;
        testSubscription.entityType = EntityType.USER;
        testSubscription.tier = SubscriptionTier.PRO;
        testSubscription.status = SubscriptionStatus.ACTIVE;
        testSubscription.currentPeriodStart = testPeriodStart;
        testSubscription.currentPeriodEnd = testPeriodEnd;
        testSubscription.createdAt = Instant.now();
        testSubscription.updatedAt = Instant.now();
    }

    // ===== Create Subscription Tests =====

    @Test
    void testCreateSubscription_ValidProTier_Success() {
        // Given
        when(userRepository.findById(any(UUID.class)))
            .thenReturn(Uni.createFrom().item(testUser));
        when(subscriptionRepository.findActiveByEntityIdAndType(testUserId, EntityType.USER))
            .thenReturn(Uni.createFrom().nullItem());
        when(subscriptionRepository.persist(any(Subscription.class)))
            .thenAnswer(invocation -> {
                Subscription sub = invocation.getArgument(0);
                sub.subscriptionId = UUID.randomUUID();
                return Uni.createFrom().item(sub);
            });
        when(userRepository.persist(any(User.class)))
            .thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                return Uni.createFrom().item(user);
            });

        // When
        Subscription result = billingService.createSubscription(testUserId, SubscriptionTier.PRO)
            .await().indefinitely();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.entityId).isEqualTo(testUserId);
        assertThat(result.entityType).isEqualTo(EntityType.USER);
        assertThat(result.tier).isEqualTo(SubscriptionTier.PRO);
        assertThat(result.status).isEqualTo(SubscriptionStatus.TRIALING);
        assertThat(result.stripeSubscriptionId).startsWith("pending-checkout-");
        assertThat(result.currentPeriodStart).isNotNull();
        assertThat(result.currentPeriodEnd).isNotNull();

        // Verify period is 30 days
        long daysBetween = ChronoUnit.DAYS.between(result.currentPeriodStart, result.currentPeriodEnd);
        assertThat(daysBetween).isEqualTo(30);

        // Verify repository interactions
        verify(userRepository, times(2)).findById(testUserId); // Called twice: once in createSubscription, once in updateUserTier
        verify(subscriptionRepository).findActiveByEntityIdAndType(testUserId, EntityType.USER);
        verify(subscriptionRepository).persist(any(Subscription.class));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).persist(userCaptor.capture());
        assertThat(userCaptor.getValue().subscriptionTier).isEqualTo(SubscriptionTier.PRO);
    }

    @Test
    void testCreateSubscription_UserNotFound_ThrowsException() {
        // Given
        when(userRepository.findById(testUserId))
            .thenReturn(Uni.createFrom().nullItem());

        // When/Then
        assertThatThrownBy(() ->
            billingService.createSubscription(testUserId, SubscriptionTier.PRO)
                .await().indefinitely()
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("User not found");

        verify(userRepository).findById(testUserId);
        verify(subscriptionRepository, never()).persist(any(Subscription.class));
    }

    @Test
    void testCreateSubscription_FreeTier_ThrowsException() {
        // Given
        when(userRepository.findById(testUserId))
            .thenReturn(Uni.createFrom().item(testUser));

        // When/Then
        assertThatThrownBy(() ->
            billingService.createSubscription(testUserId, SubscriptionTier.FREE)
                .await().indefinitely()
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Cannot create subscription for FREE tier");

        verify(userRepository).findById(testUserId);
        verify(subscriptionRepository, never()).persist(any(Subscription.class));
    }

    @Test
    void testCreateSubscription_UserAlreadyHasSubscription_ThrowsException() {
        // Given
        when(userRepository.findById(testUserId))
            .thenReturn(Uni.createFrom().item(testUser));
        when(subscriptionRepository.findActiveByEntityIdAndType(testUserId, EntityType.USER))
            .thenReturn(Uni.createFrom().item(testSubscription));

        // When/Then
        assertThatThrownBy(() ->
            billingService.createSubscription(testUserId, SubscriptionTier.PRO_PLUS)
                .await().indefinitely()
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("User already has an active subscription");

        verify(userRepository).findById(testUserId);
        verify(subscriptionRepository).findActiveByEntityIdAndType(testUserId, EntityType.USER);
        verify(subscriptionRepository, never()).persist(any(Subscription.class));
    }

    @Test
    void testCreateSubscription_RepositoryPersistFailure_PropagatesException() {
        // Given
        when(userRepository.findById(testUserId))
            .thenReturn(Uni.createFrom().item(testUser));
        when(subscriptionRepository.findActiveByEntityIdAndType(testUserId, EntityType.USER))
            .thenReturn(Uni.createFrom().nullItem());
        when(subscriptionRepository.persist(any(Subscription.class)))
            .thenReturn(Uni.createFrom().failure(new RuntimeException("Database error")));

        // When/Then
        assertThatThrownBy(() ->
            billingService.createSubscription(testUserId, SubscriptionTier.PRO)
                .await().indefinitely()
        )
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Database error");

        verify(subscriptionRepository).persist(any(Subscription.class));
    }

    // ===== Upgrade Subscription Tests =====

    @Test
    void testUpgradeSubscription_ProToProPlus_Success() throws StripeException {
        // Given
        testSubscription.tier = SubscriptionTier.PRO;
        testUser.subscriptionTier = SubscriptionTier.PRO;

        when(userRepository.findById(any(UUID.class)))
            .thenReturn(Uni.createFrom().item(testUser));
        when(subscriptionRepository.findActiveByEntityIdAndType(testUserId, EntityType.USER))
            .thenReturn(Uni.createFrom().item(testSubscription));
        when(subscriptionRepository.persist(any(Subscription.class)))
            .thenAnswer(invocation -> {
                Subscription sub = invocation.getArgument(0);
                return Uni.createFrom().item(sub);
            });
        when(userRepository.persist(any(User.class)))
            .thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                return Uni.createFrom().item(user);
            });
        doNothing().when(stripeAdapter).updateSubscription(anyString(), any(SubscriptionTier.class));

        // When
        Subscription result = billingService.upgradeSubscription(testUserId, SubscriptionTier.PRO_PLUS)
            .await().indefinitely();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.tier).isEqualTo(SubscriptionTier.PRO_PLUS);

        verify(stripeAdapter).updateSubscription(testStripeSubscriptionId, SubscriptionTier.PRO_PLUS);

        ArgumentCaptor<Subscription> subCaptor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).persist(subCaptor.capture());
        assertThat(subCaptor.getValue().tier).isEqualTo(SubscriptionTier.PRO_PLUS);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).persist(userCaptor.capture());
        assertThat(userCaptor.getValue().subscriptionTier).isEqualTo(SubscriptionTier.PRO_PLUS);
    }

    @Test
    void testUpgradeSubscription_ProPlusToEnterprise_Success() throws StripeException {
        // Given
        testSubscription.tier = SubscriptionTier.PRO_PLUS;
        testUser.subscriptionTier = SubscriptionTier.PRO_PLUS;

        when(userRepository.findById(any(UUID.class)))
            .thenReturn(Uni.createFrom().item(testUser));
        when(subscriptionRepository.findActiveByEntityIdAndType(testUserId, EntityType.USER))
            .thenReturn(Uni.createFrom().item(testSubscription));
        when(subscriptionRepository.persist(any(Subscription.class)))
            .thenAnswer(invocation -> {
                Subscription sub = invocation.getArgument(0);
                return Uni.createFrom().item(sub);
            });
        when(userRepository.persist(any(User.class)))
            .thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                return Uni.createFrom().item(user);
            });
        doNothing().when(stripeAdapter).updateSubscription(anyString(), any(SubscriptionTier.class));

        // When
        Subscription result = billingService.upgradeSubscription(testUserId, SubscriptionTier.ENTERPRISE)
            .await().indefinitely();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.tier).isEqualTo(SubscriptionTier.ENTERPRISE);

        verify(stripeAdapter).updateSubscription(testStripeSubscriptionId, SubscriptionTier.ENTERPRISE);

        ArgumentCaptor<Subscription> subCaptor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).persist(subCaptor.capture());
        assertThat(subCaptor.getValue().tier).isEqualTo(SubscriptionTier.ENTERPRISE);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).persist(userCaptor.capture());
        assertThat(userCaptor.getValue().subscriptionTier).isEqualTo(SubscriptionTier.ENTERPRISE);
    }

    @Test
    void testUpgradeSubscription_UserNotFound_ThrowsException() {
        // Given
        when(userRepository.findById(testUserId))
            .thenReturn(Uni.createFrom().nullItem());

        // When/Then
        assertThatThrownBy(() ->
            billingService.upgradeSubscription(testUserId, SubscriptionTier.PRO_PLUS)
                .await().indefinitely()
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("User not found");

        verify(userRepository).findById(testUserId);
        verify(subscriptionRepository, never()).persist(any(Subscription.class));
    }

    @Test
    void testUpgradeSubscription_NoActiveSubscription_ThrowsException() {
        // Given
        when(userRepository.findById(testUserId))
            .thenReturn(Uni.createFrom().item(testUser));
        when(subscriptionRepository.findActiveByEntityIdAndType(testUserId, EntityType.USER))
            .thenReturn(Uni.createFrom().nullItem());

        // When/Then
        assertThatThrownBy(() ->
            billingService.upgradeSubscription(testUserId, SubscriptionTier.PRO)
                .await().indefinitely()
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("User has no active subscription");

        verify(subscriptionRepository).findActiveByEntityIdAndType(testUserId, EntityType.USER);
        verify(subscriptionRepository, never()).persist(any(Subscription.class));
    }

    @Test
    void testUpgradeSubscription_InvalidDowngrade_ThrowsException() {
        // Given - user has PRO_PLUS, trying to downgrade to PRO
        testSubscription.tier = SubscriptionTier.PRO_PLUS;

        when(userRepository.findById(testUserId))
            .thenReturn(Uni.createFrom().item(testUser));
        when(subscriptionRepository.findActiveByEntityIdAndType(testUserId, EntityType.USER))
            .thenReturn(Uni.createFrom().item(testSubscription));

        // When/Then
        assertThatThrownBy(() ->
            billingService.upgradeSubscription(testUserId, SubscriptionTier.PRO)
                .await().indefinitely()
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid tier transition")
            .hasMessageContaining("Downgrades not allowed");

        verify(subscriptionRepository, never()).persist(any(Subscription.class));
    }

    @Test
    void testUpgradeSubscription_InvalidLateralMove_ThrowsException() {
        // Given - user has PRO, trying to "upgrade" to PRO
        testSubscription.tier = SubscriptionTier.PRO;

        when(userRepository.findById(testUserId))
            .thenReturn(Uni.createFrom().item(testUser));
        when(subscriptionRepository.findActiveByEntityIdAndType(testUserId, EntityType.USER))
            .thenReturn(Uni.createFrom().item(testSubscription));

        // When/Then
        assertThatThrownBy(() ->
            billingService.upgradeSubscription(testUserId, SubscriptionTier.PRO)
                .await().indefinitely()
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid tier transition");

        verify(subscriptionRepository, never()).persist(any(Subscription.class));
    }

    @Test
    void testUpgradeSubscription_StripeUpdateFails_ThrowsException() throws StripeException {
        // Given
        testSubscription.tier = SubscriptionTier.PRO;

        when(userRepository.findById(testUserId))
            .thenReturn(Uni.createFrom().item(testUser));
        when(subscriptionRepository.findActiveByEntityIdAndType(testUserId, EntityType.USER))
            .thenReturn(Uni.createFrom().item(testSubscription));
        doThrow(new StripeException("Stripe API error"))
            .when(stripeAdapter).updateSubscription(anyString(), any(SubscriptionTier.class));

        // When/Then
        assertThatThrownBy(() ->
            billingService.upgradeSubscription(testUserId, SubscriptionTier.PRO_PLUS)
                .await().indefinitely()
        )
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to update Stripe subscription");

        verify(stripeAdapter).updateSubscription(testStripeSubscriptionId, SubscriptionTier.PRO_PLUS);
        verify(subscriptionRepository, never()).persist(any(Subscription.class));
    }

    // ===== Cancel Subscription Tests =====

    @Test
    void testCancelSubscription_ValidSubscription_Success() throws StripeException {
        // Given
        when(userRepository.findById(testUserId))
            .thenReturn(Uni.createFrom().item(testUser));
        when(subscriptionRepository.findActiveByEntityIdAndType(testUserId, EntityType.USER))
            .thenReturn(Uni.createFrom().item(testSubscription));
        when(subscriptionRepository.persist(any(Subscription.class)))
            .thenAnswer(invocation -> {
                Subscription sub = invocation.getArgument(0);
                return Uni.createFrom().item(sub);
            });
        doNothing().when(stripeAdapter).cancelSubscription(anyString());

        // When
        billingService.cancelSubscription(testUserId)
            .await().indefinitely();

        // Then
        ArgumentCaptor<Subscription> subscriptionCaptor = ArgumentCaptor.forClass(Subscription.class);
        verify(stripeAdapter).cancelSubscription(testStripeSubscriptionId);
        verify(subscriptionRepository).persist(subscriptionCaptor.capture());

        Subscription canceledSub = subscriptionCaptor.getValue();
        assertThat(canceledSub.canceledAt).isNotNull();
        assertThat(canceledSub.canceledAt).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void testCancelSubscription_UserNotFound_ThrowsException() {
        // Given
        when(userRepository.findById(testUserId))
            .thenReturn(Uni.createFrom().nullItem());

        // When/Then
        assertThatThrownBy(() ->
            billingService.cancelSubscription(testUserId)
                .await().indefinitely()
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("User not found");

        verify(userRepository).findById(testUserId);
        verify(subscriptionRepository, never()).persist(any(Subscription.class));
    }

    @Test
    void testCancelSubscription_NoActiveSubscription_Idempotent() {
        // Given
        when(userRepository.findById(testUserId))
            .thenReturn(Uni.createFrom().item(testUser));
        when(subscriptionRepository.findActiveByEntityIdAndType(testUserId, EntityType.USER))
            .thenReturn(Uni.createFrom().nullItem());

        // When - should not throw exception
        billingService.cancelSubscription(testUserId)
            .await().indefinitely();

        // Then - verify no Stripe call was made
        verifyNoInteractions(stripeAdapter);
        verify(subscriptionRepository, never()).persist(any(Subscription.class));
    }

    @Test
    void testCancelSubscription_StripeCancelFails_ThrowsException() throws StripeException {
        // Given
        when(userRepository.findById(testUserId))
            .thenReturn(Uni.createFrom().item(testUser));
        when(subscriptionRepository.findActiveByEntityIdAndType(testUserId, EntityType.USER))
            .thenReturn(Uni.createFrom().item(testSubscription));
        doThrow(new StripeException("Stripe API error"))
            .when(stripeAdapter).cancelSubscription(anyString());

        // When/Then
        assertThatThrownBy(() ->
            billingService.cancelSubscription(testUserId)
                .await().indefinitely()
        )
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to cancel Stripe subscription");

        verify(stripeAdapter).cancelSubscription(testStripeSubscriptionId);
        verify(subscriptionRepository, never()).persist(any(Subscription.class));
    }

    // ===== Sync Subscription Status Tests =====

    @Test
    void testSyncSubscriptionStatus_ActiveStatus_UpdatesUserTier() {
        // Given
        testSubscription.status = SubscriptionStatus.TRIALING;
        testSubscription.tier = SubscriptionTier.PRO;

        when(subscriptionRepository.findByStripeSubscriptionId(testStripeSubscriptionId))
            .thenReturn(Uni.createFrom().item(testSubscription));
        when(subscriptionRepository.persist(any(Subscription.class)))
            .thenAnswer(invocation -> {
                Subscription sub = invocation.getArgument(0);
                return Uni.createFrom().item(sub);
            });
        when(userRepository.findById(testUserId))
            .thenReturn(Uni.createFrom().item(testUser));
        when(userRepository.persist(any(User.class)))
            .thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                return Uni.createFrom().item(user);
            });

        // When
        billingService.syncSubscriptionStatus(testStripeSubscriptionId, SubscriptionStatus.ACTIVE)
            .await().indefinitely();

        // Then
        ArgumentCaptor<Subscription> subCaptor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).persist(subCaptor.capture());
        assertThat(subCaptor.getValue().status).isEqualTo(SubscriptionStatus.ACTIVE);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).persist(userCaptor.capture());
        assertThat(userCaptor.getValue().subscriptionTier).isEqualTo(SubscriptionTier.PRO);
    }

    @Test
    void testSyncSubscriptionStatus_CanceledWithPeriodEnded_DowngradesToFree() {
        // Given - period ended in the past
        testSubscription.currentPeriodEnd = Instant.now().minus(1, ChronoUnit.DAYS);
        testSubscription.tier = SubscriptionTier.PRO;

        when(subscriptionRepository.findByStripeSubscriptionId(testStripeSubscriptionId))
            .thenReturn(Uni.createFrom().item(testSubscription));
        when(subscriptionRepository.persist(any(Subscription.class)))
            .thenAnswer(invocation -> {
                Subscription sub = invocation.getArgument(0);
                return Uni.createFrom().item(sub);
            });
        when(userRepository.findById(testUserId))
            .thenReturn(Uni.createFrom().item(testUser));
        when(userRepository.persist(any(User.class)))
            .thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                return Uni.createFrom().item(user);
            });

        // When
        billingService.syncSubscriptionStatus(testStripeSubscriptionId, SubscriptionStatus.CANCELED)
            .await().indefinitely();

        // Then
        ArgumentCaptor<Subscription> subCaptor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).persist(subCaptor.capture());
        assertThat(subCaptor.getValue().status).isEqualTo(SubscriptionStatus.CANCELED);
        assertThat(subCaptor.getValue().canceledAt).isNotNull();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).persist(userCaptor.capture());
        assertThat(userCaptor.getValue().subscriptionTier).isEqualTo(SubscriptionTier.FREE);
    }

    @Test
    void testSyncSubscriptionStatus_CanceledWithPeriodNotEnded_KeepsTier() {
        // Given - period ends in the future
        testSubscription.currentPeriodEnd = Instant.now().plus(15, ChronoUnit.DAYS);
        testSubscription.tier = SubscriptionTier.PRO_PLUS;
        testUser.subscriptionTier = SubscriptionTier.PRO_PLUS;

        when(subscriptionRepository.findByStripeSubscriptionId(testStripeSubscriptionId))
            .thenReturn(Uni.createFrom().item(testSubscription));
        when(subscriptionRepository.persist(any(Subscription.class)))
            .thenAnswer(invocation -> {
                Subscription sub = invocation.getArgument(0);
                return Uni.createFrom().item(sub);
            });

        // When
        billingService.syncSubscriptionStatus(testStripeSubscriptionId, SubscriptionStatus.CANCELED)
            .await().indefinitely();

        // Then
        ArgumentCaptor<Subscription> subCaptor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).persist(subCaptor.capture());
        assertThat(subCaptor.getValue().status).isEqualTo(SubscriptionStatus.CANCELED);
        assertThat(subCaptor.getValue().canceledAt).isNotNull();

        // User tier should NOT be updated (period not ended)
        verify(userRepository, never()).persist(any(User.class));
    }

    @Test
    void testSyncSubscriptionStatus_UnknownSubscriptionId_NoError() {
        // Given
        when(subscriptionRepository.findByStripeSubscriptionId(anyString()))
            .thenReturn(Uni.createFrom().nullItem());

        // When - should not throw exception
        billingService.syncSubscriptionStatus("unknown_sub_id", SubscriptionStatus.ACTIVE)
            .await().indefinitely();

        // Then - verify no updates attempted
        verify(subscriptionRepository, never()).persist(any(Subscription.class));
        verify(userRepository, never()).persist(any(User.class));
    }

    @Test
    void testSyncSubscriptionStatus_PastDueStatus_KeepsTierNoPersist() {
        // Given
        testSubscription.tier = SubscriptionTier.PRO;

        when(subscriptionRepository.findByStripeSubscriptionId(testStripeSubscriptionId))
            .thenReturn(Uni.createFrom().item(testSubscription));
        when(subscriptionRepository.persist(any(Subscription.class)))
            .thenAnswer(invocation -> {
                Subscription sub = invocation.getArgument(0);
                return Uni.createFrom().item(sub);
            });

        // When
        billingService.syncSubscriptionStatus(testStripeSubscriptionId, SubscriptionStatus.PAST_DUE)
            .await().indefinitely();

        // Then
        ArgumentCaptor<Subscription> subCaptor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).persist(subCaptor.capture());
        assertThat(subCaptor.getValue().status).isEqualTo(SubscriptionStatus.PAST_DUE);

        // User tier should NOT be changed for PAST_DUE
        verify(userRepository, never()).persist(any(User.class));
    }

    // ===== Get Active Subscription Tests =====

    @Test
    void testGetActiveSubscription_SubscriptionExists_ReturnsSubscription() {
        // Given
        when(subscriptionRepository.findActiveByEntityIdAndType(testUserId, EntityType.USER))
            .thenReturn(Uni.createFrom().item(testSubscription));

        // When
        Subscription result = billingService.getActiveSubscription(testUserId)
            .await().indefinitely();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.subscriptionId).isEqualTo(testSubscription.subscriptionId);
        assertThat(result.tier).isEqualTo(SubscriptionTier.PRO);

        verify(subscriptionRepository).findActiveByEntityIdAndType(testUserId, EntityType.USER);
    }

    @Test
    void testGetActiveSubscription_NoSubscription_ReturnsNull() {
        // Given
        when(subscriptionRepository.findActiveByEntityIdAndType(testUserId, EntityType.USER))
            .thenReturn(Uni.createFrom().nullItem());

        // When
        Subscription result = billingService.getActiveSubscription(testUserId)
            .await().indefinitely();

        // Then
        assertThat(result).isNull();

        verify(subscriptionRepository).findActiveByEntityIdAndType(testUserId, EntityType.USER);
    }
}
