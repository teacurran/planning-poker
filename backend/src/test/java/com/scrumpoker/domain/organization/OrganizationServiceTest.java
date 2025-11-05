package com.scrumpoker.domain.organization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrumpoker.domain.user.SubscriptionTier;
import com.scrumpoker.domain.user.User;
import com.scrumpoker.integration.sso.OidcConfig;
import com.scrumpoker.integration.sso.SsoConfig;
import com.scrumpoker.repository.OrgMemberRepository;
import com.scrumpoker.repository.OrganizationRepository;
import com.scrumpoker.repository.UserRepository;
import com.scrumpoker.security.FeatureGate;
import com.scrumpoker.security.FeatureNotAvailableException;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for OrganizationService using mocked dependencies.
 * Tests organization creation, member management, SSO/branding configuration,
 * and edge cases including domain validation and admin lockout prevention.
 * <p>
 * This test suite uses pure Mockito-based unit testing without Quarkus
 * integration to ensure fast execution and complete isolation from
 * infrastructure dependencies.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private OrgMemberRepository orgMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FeatureGate featureGate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OrganizationService organizationService;

    private User testOwner;
    private UUID testOwnerId;
    private Organization testOrg;
    private UUID testOrgId;
    private OrgMember testMember;

    @BeforeEach
    void setUp() {
        // Create test owner with Enterprise tier and matching email domain
        testOwnerId = UUID.randomUUID();
        testOwner = new User();
        testOwner.userId = testOwnerId;
        testOwner.email = "owner@acme.com";
        testOwner.displayName = "Test Owner";
        testOwner.subscriptionTier = SubscriptionTier.ENTERPRISE;
        testOwner.createdAt = Instant.now();
        testOwner.updatedAt = Instant.now();

        // Create test organization
        testOrgId = UUID.randomUUID();
        testOrg = new Organization();
        testOrg.orgId = testOrgId;
        testOrg.name = "Acme Corporation";
        testOrg.domain = "acme.com";
        testOrg.ssoConfig = null;
        testOrg.branding = null;
        testOrg.createdAt = Instant.now();
        testOrg.updatedAt = Instant.now();

        // Create test member
        testMember = new OrgMember();
        testMember.id = new OrgMemberId(testOrgId, testOwnerId);
        testMember.organization = testOrg;
        testMember.user = testOwner;
        testMember.role = OrgRole.ADMIN;
        testMember.joinedAt = Instant.now();
    }

    // ===== Organization Creation Tests =====

    @Test
    void testCreateOrganization_Success_ValidDomainAndEnterpriseTier() {
        // Given
        String name = "Acme Corp";
        String domain = "acme.com";

        when(userRepository.findById(testOwnerId))
            .thenReturn(Uni.createFrom().item(testOwner));
        doNothing().when(featureGate).requireCanManageOrganization(testOwner);
        when(organizationRepository.persist(any(Organization.class)))
            .thenAnswer(invocation -> {
                Organization org = invocation.getArgument(0);
                org.orgId = testOrgId;
                return Uni.createFrom().item(org);
            });
        when(orgMemberRepository.persist(any(OrgMember.class)))
            .thenReturn(Uni.createFrom().item(testMember));

        // When
        Organization result = organizationService.createOrganization(name, domain, testOwnerId)
            .await().indefinitely();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.name).isEqualTo(name);
        assertThat(result.domain).isEqualTo(domain);
        assertThat(result.orgId).isEqualTo(testOrgId);

        // Verify feature gate enforcement
        verify(featureGate).requireCanManageOrganization(testOwner);

        // Verify organization persisted
        ArgumentCaptor<Organization> orgCaptor = ArgumentCaptor.forClass(Organization.class);
        verify(organizationRepository).persist(orgCaptor.capture());
        assertThat(orgCaptor.getValue().name).isEqualTo(name);
        assertThat(orgCaptor.getValue().domain).isEqualTo(domain);

        // Verify owner added as ADMIN
        ArgumentCaptor<OrgMember> memberCaptor = ArgumentCaptor.forClass(OrgMember.class);
        verify(orgMemberRepository).persist(memberCaptor.capture());
        OrgMember capturedMember = memberCaptor.getValue();
        assertThat(capturedMember.role).isEqualTo(OrgRole.ADMIN);
        assertThat(capturedMember.id.userId).isEqualTo(testOwnerId);
        assertThat(capturedMember.id.orgId).isEqualTo(testOrgId);
    }

    @Test
    void testCreateOrganization_Failure_EmailDomainMismatch() {
        // Given - user email is owner@different.com but org domain is acme.com
        testOwner.email = "owner@different.com";
        String name = "Acme Corp";
        String domain = "acme.com";

        when(userRepository.findById(testOwnerId))
            .thenReturn(Uni.createFrom().item(testOwner));
        doNothing().when(featureGate).requireCanManageOrganization(testOwner);

        // When/Then
        assertThatThrownBy(() ->
            organizationService.createOrganization(name, domain, testOwnerId)
                .await().indefinitely()
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("User email domain does not match organization domain")
            .hasMessageContaining("Expected: acme.com")
            .hasMessageContaining("but user has: different.com");

        // Verify no persistence occurred
        verify(organizationRepository, never()).persist(any(Organization.class));
        verify(orgMemberRepository, never()).persist(any(OrgMember.class));
    }

    @Test
    void testCreateOrganization_Failure_MissingEnterpriseTier() {
        // Given - user has PRO tier, not ENTERPRISE
        testOwner.subscriptionTier = SubscriptionTier.PRO;
        String name = "Acme Corp";
        String domain = "acme.com";

        when(userRepository.findById(testOwnerId))
            .thenReturn(Uni.createFrom().item(testOwner));
        doThrow(new FeatureNotAvailableException(
            SubscriptionTier.ENTERPRISE,
            SubscriptionTier.PRO,
            "Organization Management"
        )).when(featureGate).requireCanManageOrganization(testOwner);

        // When/Then
        assertThatThrownBy(() ->
            organizationService.createOrganization(name, domain, testOwnerId)
                .await().indefinitely()
        )
            .isInstanceOf(FeatureNotAvailableException.class)
            .hasMessageContaining("Organization Management")
            .hasMessageContaining("Enterprise tier");

        // Verify no persistence occurred
        verify(organizationRepository, never()).persist(any(Organization.class));
        verify(orgMemberRepository, never()).persist(any(OrgMember.class));
    }

    @Test
    void testCreateOrganization_Failure_UserNotFound() {
        // Given
        UUID unknownUserId = UUID.randomUUID();
        String name = "Acme Corp";
        String domain = "acme.com";

        when(userRepository.findById(unknownUserId))
            .thenReturn(Uni.createFrom().nullItem());

        // When/Then
        assertThatThrownBy(() ->
            organizationService.createOrganization(name, domain, unknownUserId)
                .await().indefinitely()
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("User not found")
            .hasMessageContaining(unknownUserId.toString());

        verify(organizationRepository, never()).persist(any(Organization.class));
    }

    // ===== Member Management Tests =====

    @Test
    void testAddMember_Success_CreatesOrgMemberWithRole() {
        // Given
        UUID newMemberId = UUID.randomUUID();
        User newMember = new User();
        newMember.userId = newMemberId;
        newMember.email = "member@acme.com";

        OrgMember expectedMember = new OrgMember();
        expectedMember.id = new OrgMemberId(testOrgId, newMemberId);
        expectedMember.role = OrgRole.MEMBER;

        when(organizationRepository.findById(testOrgId))
            .thenReturn(Uni.createFrom().item(testOrg));
        when(userRepository.findById(newMemberId))
            .thenReturn(Uni.createFrom().item(newMember));
        when(orgMemberRepository.findByOrgIdAndUserId(testOrgId, newMemberId))
            .thenReturn(Uni.createFrom().nullItem());
        when(orgMemberRepository.persist(any(OrgMember.class)))
            .thenReturn(Uni.createFrom().item(expectedMember));

        // When
        OrgMember result = organizationService.addMember(testOrgId, newMemberId, OrgRole.MEMBER)
            .await().indefinitely();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.role).isEqualTo(OrgRole.MEMBER);

        ArgumentCaptor<OrgMember> memberCaptor = ArgumentCaptor.forClass(OrgMember.class);
        verify(orgMemberRepository).persist(memberCaptor.capture());
        OrgMember capturedMember = memberCaptor.getValue();
        assertThat(capturedMember.id.orgId).isEqualTo(testOrgId);
        assertThat(capturedMember.id.userId).isEqualTo(newMemberId);
        assertThat(capturedMember.role).isEqualTo(OrgRole.MEMBER);
    }

    @Test
    void testAddMember_Failure_DuplicateMember() {
        // Given - member already exists
        UUID existingMemberId = UUID.randomUUID();
        OrgMember existingMember = new OrgMember();
        existingMember.id = new OrgMemberId(testOrgId, existingMemberId);
        existingMember.role = OrgRole.MEMBER;

        when(organizationRepository.findById(testOrgId))
            .thenReturn(Uni.createFrom().item(testOrg));
        when(userRepository.findById(existingMemberId))
            .thenReturn(Uni.createFrom().item(testOwner));
        when(orgMemberRepository.findByOrgIdAndUserId(testOrgId, existingMemberId))
            .thenReturn(Uni.createFrom().item(existingMember));

        // When/Then
        assertThatThrownBy(() ->
            organizationService.addMember(testOrgId, existingMemberId, OrgRole.MEMBER)
                .await().indefinitely()
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("User is already a member of this organization");

        verify(orgMemberRepository, never()).persist(any(OrgMember.class));
    }

    @Test
    void testAddMember_Failure_OrganizationNotFound() {
        // Given
        UUID unknownOrgId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();

        User validUser = new User();
        validUser.userId = memberId;

        when(organizationRepository.findById(unknownOrgId))
            .thenReturn(Uni.createFrom().nullItem());
        when(userRepository.findById(memberId))
            .thenReturn(Uni.createFrom().item(validUser));

        // When/Then
        assertThatThrownBy(() ->
            organizationService.addMember(unknownOrgId, memberId, OrgRole.MEMBER)
                .await().indefinitely()
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Organization not found")
            .hasMessageContaining(unknownOrgId.toString());

        verify(orgMemberRepository, never()).persist(any(OrgMember.class));
    }

    @Test
    void testAddMember_Failure_UserNotFound() {
        // Given
        UUID unknownUserId = UUID.randomUUID();

        when(organizationRepository.findById(testOrgId))
            .thenReturn(Uni.createFrom().item(testOrg));
        when(userRepository.findById(unknownUserId))
            .thenReturn(Uni.createFrom().nullItem());

        // When/Then
        assertThatThrownBy(() ->
            organizationService.addMember(testOrgId, unknownUserId, OrgRole.MEMBER)
                .await().indefinitely()
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("User not found")
            .hasMessageContaining(unknownUserId.toString());

        verify(orgMemberRepository, never()).persist(any(OrgMember.class));
    }

    @Test
    void testRemoveMember_Success_RemovesMember() {
        // Given - organization has 2 admins, so safe to remove one
        UUID memberToRemoveId = UUID.randomUUID();
        OrgMember regularMember = new OrgMember();
        regularMember.id = new OrgMemberId(testOrgId, memberToRemoveId);
        regularMember.role = OrgRole.MEMBER;

        when(orgMemberRepository.findById(regularMember.id))
            .thenReturn(Uni.createFrom().item(regularMember));
        when(orgMemberRepository.delete(regularMember))
            .thenReturn(Uni.createFrom().voidItem());

        // When
        organizationService.removeMember(testOrgId, memberToRemoveId)
            .await().indefinitely();

        // Then
        verify(orgMemberRepository).findById(regularMember.id);
        verify(orgMemberRepository).delete(regularMember);
    }

    @Test
    void testRemoveMember_Failure_RemoveLastAdmin() {
        // Given - only 1 admin remains
        UUID lastAdminId = UUID.randomUUID();
        OrgMember lastAdmin = new OrgMember();
        lastAdmin.id = new OrgMemberId(testOrgId, lastAdminId);
        lastAdmin.role = OrgRole.ADMIN;

        when(orgMemberRepository.findById(lastAdmin.id))
            .thenReturn(Uni.createFrom().item(lastAdmin));
        when(orgMemberRepository.count(eq("id.orgId = ?1 and role = ?2"), eq(testOrgId), eq(OrgRole.ADMIN)))
            .thenReturn(Uni.createFrom().item(1L));

        // When/Then
        assertThatThrownBy(() ->
            organizationService.removeMember(testOrgId, lastAdminId)
                .await().indefinitely()
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot remove the last admin from organization");

        verify(orgMemberRepository, never()).delete(any(OrgMember.class));
    }

    @Test
    void testRemoveMember_Success_RemoveAdminWhenMultipleExist() {
        // Given - 2 admins exist, safe to remove one
        UUID adminToRemoveId = UUID.randomUUID();
        OrgMember adminMember = new OrgMember();
        adminMember.id = new OrgMemberId(testOrgId, adminToRemoveId);
        adminMember.role = OrgRole.ADMIN;

        when(orgMemberRepository.findById(adminMember.id))
            .thenReturn(Uni.createFrom().item(adminMember));
        when(orgMemberRepository.count(eq("id.orgId = ?1 and role = ?2"), eq(testOrgId), eq(OrgRole.ADMIN)))
            .thenReturn(Uni.createFrom().item(2L));
        when(orgMemberRepository.delete(adminMember))
            .thenReturn(Uni.createFrom().voidItem());

        // When
        organizationService.removeMember(testOrgId, adminToRemoveId)
            .await().indefinitely();

        // Then
        verify(orgMemberRepository).count(eq("id.orgId = ?1 and role = ?2"), eq(testOrgId), eq(OrgRole.ADMIN));
        verify(orgMemberRepository).delete(adminMember);
    }

    @Test
    void testRemoveMember_Failure_MemberNotFound() {
        // Given
        UUID unknownMemberId = UUID.randomUUID();
        OrgMemberId compositeId = new OrgMemberId(testOrgId, unknownMemberId);

        when(orgMemberRepository.findById(compositeId))
            .thenReturn(Uni.createFrom().nullItem());

        // When/Then
        assertThatThrownBy(() ->
            organizationService.removeMember(testOrgId, unknownMemberId)
                .await().indefinitely()
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Member not found in organization")
            .hasMessageContaining(unknownMemberId.toString());

        verify(orgMemberRepository, never()).delete(any(OrgMember.class));
    }

    // ===== SSO Configuration Tests =====

    @Test
    void testUpdateSsoConfig_Success_SerializesToJsonb() throws JsonProcessingException {
        // Given
        OidcConfig oidcConfig = new OidcConfig();
        oidcConfig.setIssuer("https://idp.example.com");
        oidcConfig.setClientId("client-123");
        oidcConfig.setClientSecret("secret-456");

        SsoConfig ssoConfig = new SsoConfig();
        ssoConfig.setProtocol("oidc");
        ssoConfig.setOidc(oidcConfig);
        String expectedJson = "{\"protocol\":\"oidc\",\"oidc\":{\"issuer\":\"https://idp.example.com\",\"clientId\":\"client-123\"}}";

        when(organizationRepository.findById(testOrgId))
            .thenReturn(Uni.createFrom().item(testOrg));
        when(objectMapper.writeValueAsString(ssoConfig))
            .thenReturn(expectedJson);
        when(organizationRepository.persist(any(Organization.class)))
            .thenAnswer(invocation -> {
                Organization org = invocation.getArgument(0);
                return Uni.createFrom().item(org);
            });

        // When
        Organization result = organizationService.updateSsoConfig(testOrgId, ssoConfig)
            .await().indefinitely();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.ssoConfig).isEqualTo(expectedJson);

        verify(objectMapper).writeValueAsString(ssoConfig);

        ArgumentCaptor<Organization> orgCaptor = ArgumentCaptor.forClass(Organization.class);
        verify(organizationRepository).persist(orgCaptor.capture());
        assertThat(orgCaptor.getValue().ssoConfig).isEqualTo(expectedJson);
    }

    @Test
    void testUpdateSsoConfig_Failure_OrganizationNotFound() {
        // Given
        UUID unknownOrgId = UUID.randomUUID();
        SsoConfig ssoConfig = new SsoConfig();

        when(organizationRepository.findById(unknownOrgId))
            .thenReturn(Uni.createFrom().nullItem());

        // When/Then
        assertThatThrownBy(() ->
            organizationService.updateSsoConfig(unknownOrgId, ssoConfig)
                .await().indefinitely()
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Organization not found")
            .hasMessageContaining(unknownOrgId.toString());

        verify(organizationRepository, never()).persist(any(Organization.class));
    }

    @Test
    void testUpdateSsoConfig_Failure_JsonSerializationFailure() throws JsonProcessingException {
        // Given
        SsoConfig ssoConfig = new SsoConfig();

        when(organizationRepository.findById(testOrgId))
            .thenReturn(Uni.createFrom().item(testOrg));
        when(objectMapper.writeValueAsString(ssoConfig))
            .thenThrow(new JsonProcessingException("Serialization error") {});

        // When/Then
        assertThatThrownBy(() ->
            organizationService.updateSsoConfig(testOrgId, ssoConfig)
                .await().indefinitely()
        )
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to serialize SSO config to JSON");

        verify(organizationRepository, never()).persist(any(Organization.class));
    }

    // ===== Branding Configuration Tests =====

    @Test
    void testUpdateBranding_Success_SerializesToJsonb() throws JsonProcessingException {
        // Given
        String logoUrl = "https://example.com/logo.png";
        String primaryColor = "#FF5733";
        String secondaryColor = "#33FF57";
        String expectedJson = "{\"logo_url\":\"https://example.com/logo.png\",\"primary_color\":\"#FF5733\",\"secondary_color\":\"#33FF57\"}";

        when(organizationRepository.findById(testOrgId))
            .thenReturn(Uni.createFrom().item(testOrg));
        when(objectMapper.writeValueAsString(any(BrandingConfig.class)))
            .thenReturn(expectedJson);
        when(organizationRepository.persist(any(Organization.class)))
            .thenAnswer(invocation -> {
                Organization org = invocation.getArgument(0);
                return Uni.createFrom().item(org);
            });

        // When
        Organization result = organizationService.updateBranding(
            testOrgId, logoUrl, primaryColor, secondaryColor
        ).await().indefinitely();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.branding).isEqualTo(expectedJson);

        ArgumentCaptor<BrandingConfig> brandingCaptor = ArgumentCaptor.forClass(BrandingConfig.class);
        verify(objectMapper).writeValueAsString(brandingCaptor.capture());
        BrandingConfig capturedBranding = brandingCaptor.getValue();
        assertThat(capturedBranding.getLogoUrl()).isEqualTo(logoUrl);
        assertThat(capturedBranding.getPrimaryColor()).isEqualTo(primaryColor);
        assertThat(capturedBranding.getSecondaryColor()).isEqualTo(secondaryColor);

        ArgumentCaptor<Organization> orgCaptor = ArgumentCaptor.forClass(Organization.class);
        verify(organizationRepository).persist(orgCaptor.capture());
        assertThat(orgCaptor.getValue().branding).isEqualTo(expectedJson);
    }

    @Test
    void testUpdateBranding_Failure_OrganizationNotFound() {
        // Given
        UUID unknownOrgId = UUID.randomUUID();

        when(organizationRepository.findById(unknownOrgId))
            .thenReturn(Uni.createFrom().nullItem());

        // When/Then
        assertThatThrownBy(() ->
            organizationService.updateBranding(
                unknownOrgId,
                "https://example.com/logo.png",
                "#FF5733",
                "#33FF57"
            ).await().indefinitely()
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Organization not found")
            .hasMessageContaining(unknownOrgId.toString());

        verify(organizationRepository, never()).persist(any(Organization.class));
    }

    @Test
    void testUpdateBranding_Failure_JsonSerializationFailure() throws JsonProcessingException {
        // Given
        when(organizationRepository.findById(testOrgId))
            .thenReturn(Uni.createFrom().item(testOrg));
        when(objectMapper.writeValueAsString(any(BrandingConfig.class)))
            .thenThrow(new JsonProcessingException("Serialization error") {});

        // When/Then
        assertThatThrownBy(() ->
            organizationService.updateBranding(
                testOrgId,
                "https://example.com/logo.png",
                "#FF5733",
                "#33FF57"
            ).await().indefinitely()
        )
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to serialize branding config to JSON");

        verify(organizationRepository, never()).persist(any(Organization.class));
    }

    // ===== Query Tests =====

    @Test
    void testGetOrganization_Success_ReturnsOrganization() {
        // Given
        when(organizationRepository.findById(testOrgId))
            .thenReturn(Uni.createFrom().item(testOrg));

        // When
        Organization result = organizationService.getOrganization(testOrgId)
            .await().indefinitely();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.orgId).isEqualTo(testOrgId);
        assertThat(result.name).isEqualTo(testOrg.name);
        assertThat(result.domain).isEqualTo(testOrg.domain);

        verify(organizationRepository).findById(testOrgId);
    }

    @Test
    void testGetOrganization_NotFound_ReturnsNull() {
        // Given
        UUID unknownOrgId = UUID.randomUUID();

        when(organizationRepository.findById(unknownOrgId))
            .thenReturn(Uni.createFrom().nullItem());

        // When
        Organization result = organizationService.getOrganization(unknownOrgId)
            .await().indefinitely();

        // Then
        assertThat(result).isNull();

        verify(organizationRepository).findById(unknownOrgId);
    }

    @Test
    void testGetUserOrganizations_Success_ReturnsUserOrgs() {
        // Given
        UUID userId = UUID.randomUUID();

        // Create multiple organization memberships
        Organization org1 = new Organization();
        org1.orgId = UUID.randomUUID();
        org1.name = "Organization 1";
        org1.domain = "org1.com";

        Organization org2 = new Organization();
        org2.orgId = UUID.randomUUID();
        org2.name = "Organization 2";
        org2.domain = "org2.com";

        OrgMember member1 = new OrgMember();
        member1.id = new OrgMemberId(org1.orgId, userId);
        member1.role = OrgRole.ADMIN;

        OrgMember member2 = new OrgMember();
        member2.id = new OrgMemberId(org2.orgId, userId);
        member2.role = OrgRole.MEMBER;

        List<OrgMember> memberships = Arrays.asList(member1, member2);

        when(orgMemberRepository.findByUserId(userId))
            .thenReturn(Uni.createFrom().item(memberships));
        when(organizationRepository.findById(org1.orgId))
            .thenReturn(Uni.createFrom().item(org1));
        when(organizationRepository.findById(org2.orgId))
            .thenReturn(Uni.createFrom().item(org2));

        // When
        List<Organization> result = organizationService.getUserOrganizations(userId)
            .collect().asList()
            .await().indefinitely();

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).extracting("name")
            .containsExactlyInAnyOrder("Organization 1", "Organization 2");
        assertThat(result).extracting("domain")
            .containsExactlyInAnyOrder("org1.com", "org2.com");

        verify(orgMemberRepository).findByUserId(userId);
        verify(organizationRepository).findById(org1.orgId);
        verify(organizationRepository).findById(org2.orgId);
    }

    @Test
    void testGetUserOrganizations_NoMemberships_ReturnsEmptyList() {
        // Given
        UUID userId = UUID.randomUUID();

        when(orgMemberRepository.findByUserId(userId))
            .thenReturn(Uni.createFrom().item(List.of()));

        // When
        List<Organization> result = organizationService.getUserOrganizations(userId)
            .collect().asList()
            .await().indefinitely();

        // Then
        assertThat(result).isEmpty();

        verify(orgMemberRepository).findByUserId(userId);
        verify(organizationRepository, never()).findById(any(UUID.class));
    }
}
