/**
 * Modal to display the authenticated user's saved room preferences.
 */

import React from 'react';
import type {
  UserPreferenceDTO,
  DeckPreference,
  ThemePreference,
  RevealBehaviorPreference,
} from '@/types/preferences';

interface UserPreferencesModalProps {
  isOpen: boolean;
  onClose: () => void;
  preferences?: UserPreferenceDTO;
  isLoading: boolean;
  error?: Error | null;
  onRetry: () => void;
}

function formatLabel(value?: string): string {
  if (!value) return 'Not set';
  return value
    .toLowerCase()
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
}

function formatDeckType(value?: DeckPreference): string {
  return formatLabel(value);
}

function formatTheme(value?: ThemePreference): string {
  return formatLabel(value);
}

function formatRevealBehavior(value?: RevealBehaviorPreference): string {
  return formatLabel(value);
}

export const UserPreferencesModal: React.FC<UserPreferencesModalProps> = ({
  isOpen,
  onClose,
  preferences,
  isLoading,
  error,
  onRetry,
}) => {
  if (!isOpen) {
    return null;
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center px-4">
      <div
        className="absolute inset-0 bg-black/40 dark:bg-black/60"
        onClick={onClose}
        aria-hidden="true"
      />
      <div
        role="dialog"
        aria-modal="true"
        className="relative z-10 w-full max-w-lg rounded-xl bg-white p-6 shadow-2xl transition-all dark:bg-gray-900"
      >
        <div className="flex items-start justify-between mb-4">
          <div>
            <h3 className="text-xl font-semibold text-gray-900 dark:text-white">User Preferences</h3>
            <p className="text-sm text-gray-500 dark:text-gray-400">
              Default settings applied to new rooms
            </p>
          </div>
          <button
            onClick={onClose}
            className="text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200"
            aria-label="Close preferences"
          >
            <svg
              className="w-5 h-5"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
              xmlns="http://www.w3.org/2000/svg"
            >
              <path strokeLinecap="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {isLoading && (
          <div className="space-y-4">
            <div className="animate-pulse bg-gray-200 dark:bg-gray-700 h-5 w-40 rounded" />
            <div className="space-y-3">
              {[1, 2, 3].map((i) => (
                <div
                  key={i}
                  className="animate-pulse bg-gray-200 dark:bg-gray-700 h-12 rounded-md"
                ></div>
              ))}
            </div>
          </div>
        )}

        {!isLoading && error && (
          <div className="rounded-lg border border-red-200 bg-red-50 p-4 dark:border-red-800 dark:bg-red-900/30">
            <p className="text-sm text-red-700 dark:text-red-200 mb-3">
              {error.message || 'Failed to load your preferences.'}
            </p>
            <div className="flex gap-3">
              <button
                onClick={onRetry}
                className="inline-flex items-center rounded-md bg-red-600 px-4 py-2 text-sm font-medium text-white hover:bg-red-700"
              >
                Retry
              </button>
              <button
                onClick={onClose}
                className="inline-flex items-center rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 dark:border-gray-700 dark:text-gray-200 dark:hover:bg-gray-800"
              >
                Close
              </button>
            </div>
          </div>
        )}

        {!isLoading && !error && (
          <div className="space-y-6">
            {preferences ? (
              <>
                <div>
                  <h4 className="text-sm font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">
                    General
                  </h4>
                  <dl className="mt-2 space-y-3">
                    <div className="flex items-center justify-between rounded-lg border border-gray-100 bg-gray-50 px-4 py-3 dark:border-gray-800 dark:bg-gray-800/70">
                      <dt className="text-sm text-gray-600 dark:text-gray-300">Theme</dt>
                      <dd className="text-sm font-medium text-gray-900 dark:text-white">
                        {formatTheme(preferences.theme)}
                      </dd>
                    </div>
                    <div className="flex items-center justify-between rounded-lg border border-gray-100 bg-gray-50 px-4 py-3 dark:border-gray-800 dark:bg-gray-800/70">
                      <dt className="text-sm text-gray-600 dark:text-gray-300">Default deck</dt>
                      <dd className="text-sm font-medium text-gray-900 dark:text-white">
                        {formatDeckType(preferences.defaultDeckType)}
                      </dd>
                    </div>
                  </dl>
                </div>

                <div>
                  <h4 className="text-sm font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">
                    Default Room Configuration
                  </h4>
                  {preferences.defaultRoomConfig ? (
                    <dl className="mt-2 space-y-3">
                      <div className="rounded-lg border border-gray-100 bg-white px-4 py-3 dark:border-gray-800 dark:bg-gray-800/70">
                        <dt className="text-xs uppercase text-gray-500 dark:text-gray-400">
                          Reveal Behavior
                        </dt>
                        <dd className="text-sm font-medium text-gray-900 dark:text-white">
                          {formatRevealBehavior(preferences.defaultRoomConfig.revealBehavior)}
                        </dd>
                      </div>
                      <div className="rounded-lg border border-gray-100 bg-white px-4 py-3 dark:border-gray-800 dark:bg-gray-800/70">
                        <dt className="text-xs uppercase text-gray-500 dark:text-gray-400">
                          Timer
                        </dt>
                        <dd className="text-sm text-gray-900 dark:text-white">
                          {preferences.defaultRoomConfig.timerEnabled
                            ? `Enabled ${
                                preferences.defaultRoomConfig.timerDurationSeconds
                                  ? `(${preferences.defaultRoomConfig.timerDurationSeconds}s)`
                                  : ''
                              }`
                            : 'Disabled'}
                        </dd>
                      </div>
                      <div className="rounded-lg border border-gray-100 bg-white px-4 py-3 dark:border-gray-800 dark:bg-gray-800/70">
                        <dt className="text-xs uppercase text-gray-500 dark:text-gray-400">
                          Observers
                        </dt>
                        <dd className="text-sm text-gray-900 dark:text-white">
                          {preferences.defaultRoomConfig.allowObservers ? 'Allowed' : 'Blocked'}
                        </dd>
                      </div>
                      <div className="rounded-lg border border-gray-100 bg-white px-4 py-3 dark:border-gray-800 dark:bg-gray-800/70">
                        <dt className="text-xs uppercase text-gray-500 dark:text-gray-400">
                          Anonymous voters
                        </dt>
                        <dd className="text-sm text-gray-900 dark:text-white">
                          {preferences.defaultRoomConfig.allowAnonymousVoters
                            ? 'Allowed'
                            : 'Blocked'}
                        </dd>
                      </div>
                      {preferences.defaultRoomConfig.deckType === 'custom' &&
                        preferences.defaultRoomConfig.customDeck &&
                        preferences.defaultRoomConfig.customDeck.length > 0 && (
                          <div className="rounded-lg border border-gray-100 bg-white px-4 py-3 dark:border-gray-800 dark:bg-gray-800/70">
                            <dt className="text-xs uppercase text-gray-500 dark:text-gray-400">
                              Custom deck
                            </dt>
                            <dd className="text-sm text-gray-900 dark:text-white">
                              {preferences.defaultRoomConfig.customDeck.join(', ')}
                            </dd>
                          </div>
                        )}
                    </dl>
                  ) : (
                    <p className="mt-2 text-sm text-gray-500 dark:text-gray-400">
                      Default room configuration not set yet.
                    </p>
                  )}
                </div>

                <div>
                  <h4 className="text-sm font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">
                    Notifications
                  </h4>
                  <dl className="mt-2 space-y-3">
                    <div className="rounded-lg border border-gray-100 bg-white px-4 py-3 dark:border-gray-800 dark:bg-gray-800/70">
                      <dt className="text-xs uppercase text-gray-500 dark:text-gray-400">
                        Email notifications
                      </dt>
                      <dd className="text-sm text-gray-900 dark:text-white">
                        {preferences.notificationSettings?.emailNotifications ? 'Enabled' : 'Disabled'}
                      </dd>
                    </div>
                    <div className="rounded-lg border border-gray-100 bg-white px-4 py-3 dark:border-gray-800 dark:bg-gray-800/70">
                      <dt className="text-xs uppercase text-gray-500 dark:text-gray-400">
                        Session reminders
                      </dt>
                      <dd className="text-sm text-gray-900 dark:text-white">
                        {preferences.notificationSettings?.sessionReminders ? 'Enabled' : 'Disabled'}
                      </dd>
                    </div>
                  </dl>
                </div>
              </>
            ) : (
              <p className="text-sm text-gray-500 dark:text-gray-400">
                No preferences found. Configure your preferences from the profile settings page.
              </p>
            )}

            <div className="flex justify-end gap-3">
              <button
                onClick={onClose}
                className="inline-flex items-center rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 dark:border-gray-700 dark:text-gray-200 dark:hover:bg-gray-800"
              >
                Close
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
