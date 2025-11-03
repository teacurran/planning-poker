/**
 * Upgrade modal context provider.
 * Manages global upgrade modal state and provides trigger function for 403 errors.
 */

import React, { createContext, useContext, useState, useCallback, useEffect } from 'react';
import type { SubscriptionTier } from '@/types/auth';
import { UpgradeModal } from '@/components/subscription/UpgradeModal';
import { useAuthStore } from '@/stores/authStore';
import { registerFeatureNotAvailableHandler } from '@/services/api';

interface UpgradeModalState {
  isOpen: boolean;
  requiredTier: SubscriptionTier;
  feature: string;
}

interface UpgradeModalContextValue {
  showUpgradeModal: (requiredTier: SubscriptionTier, feature: string) => void;
  hideUpgradeModal: () => void;
}

const UpgradeModalContext = createContext<UpgradeModalContextValue | undefined>(undefined);

export const UpgradeModalProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { user } = useAuthStore();
  const [modalState, setModalState] = useState<UpgradeModalState>({
    isOpen: false,
    requiredTier: 'PRO',
    feature: '',
  });

  const showUpgradeModal = useCallback((requiredTier: SubscriptionTier, feature: string) => {
    setModalState({
      isOpen: true,
      requiredTier,
      feature,
    });
  }, []);

  const hideUpgradeModal = useCallback(() => {
    setModalState((prev) => ({ ...prev, isOpen: false }));
  }, []);

  // Register the 403 error handler on mount
  useEffect(() => {
    registerFeatureNotAvailableHandler((requiredTier, feature) => {
      showUpgradeModal(requiredTier as SubscriptionTier, feature);
    });
  }, [showUpgradeModal]);

  return (
    <UpgradeModalContext.Provider value={{ showUpgradeModal, hideUpgradeModal }}>
      {children}
      {user && (
        <UpgradeModal
          isOpen={modalState.isOpen}
          onClose={hideUpgradeModal}
          requiredTier={modalState.requiredTier}
          currentTier={user.subscriptionTier}
          feature={modalState.feature}
        />
      )}
    </UpgradeModalContext.Provider>
  );
};

/**
 * Hook to access upgrade modal context.
 */
export const useUpgradeModal = (): UpgradeModalContextValue => {
  const context = useContext(UpgradeModalContext);
  if (context === undefined) {
    throw new Error('useUpgradeModal must be used within UpgradeModalProvider');
  }
  return context;
};
