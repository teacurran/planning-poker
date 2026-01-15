/**
 * User preference-related TypeScript types.
 * Mirrors the backend UserPreferenceDTO schema.
 */

export type DeckPreference = 'fibonacci' | 'tshirt' | 'powers_of_2' | 'custom';
export type ThemePreference = 'light' | 'dark' | 'system';
export type RevealBehaviorPreference = 'manual' | 'automatic' | 'timer';

export interface DefaultRoomConfigPreference {
  deckType?: DeckPreference;
  customDeck?: string[];
  timerEnabled?: boolean;
  timerDurationSeconds?: number;
  revealBehavior?: RevealBehaviorPreference;
  allowObservers?: boolean;
  allowAnonymousVoters?: boolean;
}

export interface NotificationSettingsPreference {
  emailNotifications?: boolean;
  sessionReminders?: boolean;
}

export interface UserPreferenceDTO {
  userId: string;
  defaultDeckType?: DeckPreference;
  defaultRoomConfig?: DefaultRoomConfigPreference;
  theme?: ThemePreference;
  notificationSettings?: NotificationSettingsPreference;
}
