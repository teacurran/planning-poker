/**
 * Member management page component.
 * Lists organization members with invite and remove functionality.
 */

import React, { useState, Fragment } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { Dialog, Transition } from '@headlessui/react';
import {
  BuildingOfficeIcon,
  UserPlusIcon,
  ExclamationTriangleIcon,
  XCircleIcon,
  ArrowLeftIcon,
} from '@heroicons/react/24/outline';
import { useOrganization, useInviteMember, useRemoveMember } from '@/services/organizationApi';
import { useAuthStore } from '@/stores/authStore';
import { MemberTable } from '@/components/org/MemberTable';
import type { OrgMemberDTO, OrgRole, InviteMemberRequest } from '@/types/organization';

/**
 * Invite member modal component.
 */
const InviteMemberModal: React.FC<{
  isOpen: boolean;
  onClose: () => void;
  onInvite: (request: InviteMemberRequest) => void;
  isLoading: boolean;
}> = ({ isOpen, onClose, onInvite, isLoading }) => {
  const [email, setEmail] = useState('');
  const [role, setRole] = useState<OrgRole>('MEMBER');
  const [emailError, setEmailError] = useState('');

  const validateEmail = (email: string): boolean => {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailRegex.test(email);
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();

    if (!validateEmail(email)) {
      setEmailError('Please enter a valid email address');
      return;
    }

    onInvite({ email, role });
    setEmail('');
    setRole('MEMBER');
    setEmailError('');
  };

  const handleClose = () => {
    setEmail('');
    setRole('MEMBER');
    setEmailError('');
    onClose();
  };

  return (
    <Transition appear show={isOpen} as={Fragment}>
      <Dialog as="div" className="relative z-50" onClose={handleClose}>
        <Transition.Child
          as={Fragment}
          enter="ease-out duration-300"
          enterFrom="opacity-0"
          enterTo="opacity-100"
          leave="ease-in duration-200"
          leaveFrom="opacity-100"
          leaveTo="opacity-0"
        >
          <div className="fixed inset-0 bg-black bg-opacity-25 dark:bg-opacity-50" />
        </Transition.Child>

        <div className="fixed inset-0 overflow-y-auto">
          <div className="flex min-h-full items-center justify-center p-4 text-center">
            <Transition.Child
              as={Fragment}
              enter="ease-out duration-300"
              enterFrom="opacity-0 scale-95"
              enterTo="opacity-100 scale-100"
              leave="ease-in duration-200"
              leaveFrom="opacity-100 scale-100"
              leaveTo="opacity-0 scale-95"
            >
              <Dialog.Panel className="w-full max-w-md transform overflow-hidden rounded-2xl bg-white p-6 text-left align-middle shadow-xl transition-all dark:bg-gray-800">
                <Dialog.Title
                  as="h3"
                  className="text-lg font-semibold leading-6 text-gray-900 dark:text-white mb-4"
                >
                  Invite Member
                </Dialog.Title>

                <form onSubmit={handleSubmit}>
                  <div className="space-y-4">
                    {/* Email Input */}
                    <div>
                      <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                        Email Address
                      </label>
                      <input
                        type="email"
                        value={email}
                        onChange={(e) => {
                          setEmail(e.target.value);
                          setEmailError('');
                        }}
                        className={`w-full px-3 py-2 border rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-700 dark:text-white ${
                          emailError
                            ? 'border-red-500'
                            : 'border-gray-300 dark:border-gray-600'
                        }`}
                        placeholder="member@example.com"
                        required
                      />
                      {emailError && (
                        <p className="mt-1 text-sm text-red-600 dark:text-red-400">{emailError}</p>
                      )}
                    </div>

                    {/* Role Selection */}
                    <div>
                      <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                        Role
                      </label>
                      <select
                        value={role}
                        onChange={(e) => setRole(e.target.value as OrgRole)}
                        className="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-700 dark:border-gray-600 dark:text-white"
                      >
                        <option value="MEMBER">Member</option>
                        <option value="ADMIN">Admin</option>
                      </select>
                      <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
                        Admins can manage members, SSO, and view audit logs
                      </p>
                    </div>
                  </div>

                  <div className="mt-6 flex gap-3">
                    <button
                      type="button"
                      onClick={handleClose}
                      disabled={isLoading}
                      className="inline-flex flex-1 justify-center rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-300 dark:hover:bg-gray-600"
                    >
                      Cancel
                    </button>
                    <button
                      type="submit"
                      disabled={isLoading}
                      className="inline-flex flex-1 justify-center rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      {isLoading ? 'Inviting...' : 'Send Invitation'}
                    </button>
                  </div>
                </form>
              </Dialog.Panel>
            </Transition.Child>
          </div>
        </div>
      </Dialog>
    </Transition>
  );
};

/**
 * Remove member confirmation modal component.
 */
const RemoveMemberModal: React.FC<{
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => void;
  isLoading: boolean;
  memberName: string;
  memberRole: OrgRole;
}> = ({ isOpen, onClose, onConfirm, isLoading, memberName, memberRole }) => {
  return (
    <Transition appear show={isOpen} as={Fragment}>
      <Dialog as="div" className="relative z-50" onClose={onClose}>
        <Transition.Child
          as={Fragment}
          enter="ease-out duration-300"
          enterFrom="opacity-0"
          enterTo="opacity-100"
          leave="ease-in duration-200"
          leaveFrom="opacity-100"
          leaveTo="opacity-0"
        >
          <div className="fixed inset-0 bg-black bg-opacity-25 dark:bg-opacity-50" />
        </Transition.Child>

        <div className="fixed inset-0 overflow-y-auto">
          <div className="flex min-h-full items-center justify-center p-4 text-center">
            <Transition.Child
              as={Fragment}
              enter="ease-out duration-300"
              enterFrom="opacity-0 scale-95"
              enterTo="opacity-100 scale-100"
              leave="ease-in duration-200"
              leaveFrom="opacity-100 scale-100"
              leaveTo="opacity-0 scale-95"
            >
              <Dialog.Panel className="w-full max-w-md transform overflow-hidden rounded-2xl bg-white p-6 text-left align-middle shadow-xl transition-all dark:bg-gray-800">
                <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-red-100 dark:bg-red-900">
                  <ExclamationTriangleIcon className="h-6 w-6 text-red-600 dark:text-red-400" />
                </div>

                <Dialog.Title
                  as="h3"
                  className="mt-4 text-center text-lg font-semibold leading-6 text-gray-900 dark:text-white"
                >
                  Remove Member
                </Dialog.Title>

                <div className="mt-4">
                  <p className="text-center text-sm text-gray-600 dark:text-gray-400">
                    Are you sure you want to remove{' '}
                    <span className="font-semibold">{memberName}</span>
                    {memberRole === 'ADMIN' && (
                      <span className="text-red-600 dark:text-red-400"> (Admin)</span>
                    )}{' '}
                    from the organization? This action cannot be undone.
                  </p>
                </div>

                <div className="mt-6 flex gap-3">
                  <button
                    type="button"
                    onClick={onClose}
                    disabled={isLoading}
                    className="inline-flex flex-1 justify-center rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-300 dark:hover:bg-gray-600"
                  >
                    Cancel
                  </button>
                  <button
                    type="button"
                    onClick={onConfirm}
                    disabled={isLoading}
                    className="inline-flex flex-1 justify-center rounded-md bg-red-600 px-4 py-2 text-sm font-medium text-white hover:bg-red-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-red-500 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 dark:bg-red-500 dark:hover:bg-red-600"
                  >
                    {isLoading ? 'Removing...' : 'Yes, Remove'}
                  </button>
                </div>
              </Dialog.Panel>
            </Transition.Child>
          </div>
        </div>
      </Dialog>
    </Transition>
  );
};

const MemberManagementPage: React.FC = () => {
  const { orgId } = useParams<{ orgId: string }>();
  const navigate = useNavigate();
  const { user } = useAuthStore();

  // Fetch organization data
  const { data: organization, isLoading, error } = useOrganization(orgId || '');

  // Mutations
  const inviteMember = useInviteMember(orgId || '');
  const removeMember = useRemoveMember(orgId || '');

  // Local state for members (since we don't have a GET /members endpoint)
  const [members, setMembers] = useState<OrgMemberDTO[]>([]);

  // Modal state
  const [isInviteModalOpen, setIsInviteModalOpen] = useState(false);
  const [isRemoveModalOpen, setIsRemoveModalOpen] = useState(false);
  const [memberToRemove, setMemberToRemove] = useState<{
    userId: string;
    displayName: string;
    role: OrgRole;
  } | null>(null);

  // Error and success messages
  const [errorMessage, setErrorMessage] = useState('');
  const [successMessage, setSuccessMessage] = useState('');

  // Handle invite member
  const handleInviteMember = (request: InviteMemberRequest) => {
    setErrorMessage('');
    setSuccessMessage('');

    inviteMember.mutate(request, {
      onSuccess: (newMember) => {
        // Add new member to local state
        setMembers([...members, newMember]);
        setSuccessMessage(`Invitation sent to ${request.email}`);
        setIsInviteModalOpen(false);
        setTimeout(() => setSuccessMessage(''), 5000);
      },
      onError: (err: any) => {
        if (err.response?.status === 403) {
          setErrorMessage('Only organization administrators can invite members');
        } else if (err.response?.data?.message?.includes('already exists')) {
          setErrorMessage('This user is already a member of the organization');
        } else {
          setErrorMessage(err.message || 'Failed to invite member');
        }
        setIsInviteModalOpen(false);
      },
    });
  };

  // Handle remove member
  const handleRemoveMember = (userId: string, displayName: string, role: OrgRole) => {
    setMemberToRemove({ userId, displayName, role });
    setIsRemoveModalOpen(true);
  };

  const confirmRemoveMember = () => {
    if (!memberToRemove) return;

    setErrorMessage('');
    setSuccessMessage('');

    removeMember.mutate(memberToRemove.userId, {
      onSuccess: () => {
        // Remove member from local state
        setMembers(members.filter((m) => m.userId !== memberToRemove.userId));
        setSuccessMessage(`${memberToRemove.displayName} has been removed from the organization`);
        setIsRemoveModalOpen(false);
        setMemberToRemove(null);
        setTimeout(() => setSuccessMessage(''), 5000);
      },
      onError: (err: any) => {
        if (err.response?.status === 403) {
          if (err.response?.data?.message?.includes('last admin')) {
            setErrorMessage('Cannot remove the last administrator');
          } else {
            setErrorMessage('Only organization administrators can remove members');
          }
        } else {
          setErrorMessage(err.message || 'Failed to remove member');
        }
        setIsRemoveModalOpen(false);
        setMemberToRemove(null);
      },
    });
  };

  // Check if user has Enterprise tier
  const hasEnterpriseTier = user?.subscriptionTier === 'ENTERPRISE';

  // Loading state
  if (isLoading) {
    return (
      <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
        <div className="container mx-auto px-4 py-8">
          <div className="animate-pulse bg-gray-300 dark:bg-gray-700 h-9 w-64 rounded mb-8"></div>
          <div className="bg-white dark:bg-gray-800 rounded-lg shadow-md p-6">
            <div className="animate-pulse bg-gray-300 dark:bg-gray-700 h-6 w-3/4 rounded mb-4"></div>
          </div>
        </div>
      </div>
    );
  }

  // Error state
  if (error) {
    return (
      <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
        <div className="container mx-auto px-4 py-8">
          <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg p-6 max-w-2xl mx-auto">
            <XCircleIcon className="w-6 h-6 text-red-600 dark:text-red-400 mb-2" />
            <h3 className="text-lg font-semibold text-red-800 dark:text-red-200">
              Failed to load organization
            </h3>
            <p className="text-sm text-red-700 dark:text-red-300">{error.message}</p>
          </div>
        </div>
      </div>
    );
  }

  // Check if user lacks Enterprise tier
  if (!hasEnterpriseTier) {
    return (
      <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
        <div className="container mx-auto px-4 py-8">
          <div className="bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-lg p-6 max-w-2xl mx-auto">
            <h3 className="text-lg font-semibold text-yellow-800 dark:text-yellow-200">
              Enterprise Tier Required
            </h3>
            <p className="text-sm text-yellow-700 dark:text-yellow-300">
              Member management requires an Enterprise subscription.
            </p>
          </div>
        </div>
      </div>
    );
  }

  if (!organization) {
    return null;
  }

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
      <div className="container mx-auto px-4 py-8">
        {/* Header */}
        <div className="mb-8">
          <button
            onClick={() => navigate(`/org/${orgId}/settings`)}
            className="flex items-center text-sm text-gray-600 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-300 mb-4"
          >
            <ArrowLeftIcon className="h-4 w-4 mr-1" />
            Back to Settings
          </button>

          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center">
              <BuildingOfficeIcon className="h-8 w-8 text-blue-600 dark:text-blue-400 mr-3" />
              <div>
                <h1 className="text-3xl font-bold text-gray-900 dark:text-white">
                  Member Management
                </h1>
                <p className="text-gray-600 dark:text-gray-300">
                  Manage members for {organization.name}
                </p>
              </div>
            </div>
            <button
              onClick={() => setIsInviteModalOpen(true)}
              className="inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
            >
              <UserPlusIcon className="h-5 w-5 mr-2" />
              Invite Member
            </button>
          </div>

          {/* Navigation tabs */}
          <nav className="flex space-x-4 border-b border-gray-200 dark:border-gray-700">
            <Link
              to={`/org/${orgId}/settings`}
              className="border-b-2 border-transparent pb-2 text-sm font-medium text-gray-600 hover:border-gray-300 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-300"
            >
              Settings
            </Link>
            <Link
              to={`/org/${orgId}/members`}
              className="border-b-2 border-blue-600 pb-2 text-sm font-medium text-blue-600 dark:text-blue-400"
            >
              Members
            </Link>
            <Link
              to={`/org/${orgId}/sso`}
              className="border-b-2 border-transparent pb-2 text-sm font-medium text-gray-600 hover:border-gray-300 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-300"
            >
              SSO Configuration
            </Link>
            <Link
              to={`/org/${orgId}/audit-logs`}
              className="border-b-2 border-transparent pb-2 text-sm font-medium text-gray-600 hover:border-gray-300 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-300"
            >
              Audit Logs
            </Link>
          </nav>
        </div>

        {/* Success message */}
        {successMessage && (
          <div className="mb-6 bg-green-50 dark:bg-green-900/20 border border-green-200 dark:border-green-800 rounded-lg p-4">
            <p className="text-sm text-green-800 dark:text-green-200">{successMessage}</p>
          </div>
        )}

        {/* Error message */}
        {errorMessage && (
          <div className="mb-6 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg p-4">
            <p className="text-sm text-red-800 dark:text-red-200">{errorMessage}</p>
          </div>
        )}

        {/* Member Table */}
        <div className="bg-white dark:bg-gray-800 rounded-lg shadow-md overflow-hidden">
          <MemberTable
            members={members}
            onRemoveMember={handleRemoveMember}
            currentUserId={user?.userId}
          />
        </div>

        {/* Invite Member Modal */}
        <InviteMemberModal
          isOpen={isInviteModalOpen}
          onClose={() => setIsInviteModalOpen(false)}
          onInvite={handleInviteMember}
          isLoading={inviteMember.isPending}
        />

        {/* Remove Member Modal */}
        {memberToRemove && (
          <RemoveMemberModal
            isOpen={isRemoveModalOpen}
            onClose={() => {
              setIsRemoveModalOpen(false);
              setMemberToRemove(null);
            }}
            onConfirm={confirmRemoveMember}
            isLoading={removeMember.isPending}
            memberName={memberToRemove.displayName}
            memberRole={memberToRemove.role}
          />
        )}
      </div>
    </div>
  );
};

export default MemberManagementPage;
