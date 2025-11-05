import React from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import HomePage from '@/pages/HomePage';
import RoomPage from '@/pages/RoomPage';
import DashboardPage from '@/pages/DashboardPage';
import LoginPage from '@/pages/LoginPage';
import OAuthCallbackPage from '@/pages/OAuthCallbackPage';
import { PricingPage } from '@/pages/PricingPage';
import { SubscriptionSettingsPage } from '@/pages/SubscriptionSettingsPage';
import { BillingSuccessPage } from '@/pages/BillingSuccessPage';
import SessionHistoryPage from '@/pages/SessionHistoryPage';
import SessionDetailPage from '@/pages/SessionDetailPage';
import OrganizationSettingsPage from '@/pages/org/OrganizationSettingsPage';
import SsoConfigPage from '@/pages/org/SsoConfigPage';
import MemberManagementPage from '@/pages/org/MemberManagementPage';
import AuditLogPage from '@/pages/org/AuditLogPage';
import PrivateRoute from '@/components/auth/PrivateRoute';
import { UpgradeModalProvider } from '@/contexts/UpgradeModalContext';

// Create a QueryClient instance for React Query
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 60 * 1000, // 1 minute
      retry: 1, // Retry failed requests once
      refetchOnWindowFocus: false, // Don't refetch on window focus
    },
  },
});

const App: React.FC = () => {
  return (
    <QueryClientProvider client={queryClient}>
      <UpgradeModalProvider>
        <BrowserRouter>
          <Routes>
            <Route path="/" element={<HomePage />} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/auth/callback" element={<OAuthCallbackPage />} />
            <Route path="/pricing" element={<PricingPage />} />
            <Route path="/billing/success" element={<BillingSuccessPage />} />
            <Route path="/room/:roomId" element={<RoomPage />} />
            <Route
              path="/dashboard"
              element={
                <PrivateRoute>
                  <DashboardPage />
                </PrivateRoute>
              }
            />
            <Route
              path="/billing/settings"
              element={
                <PrivateRoute>
                  <SubscriptionSettingsPage />
                </PrivateRoute>
              }
            />
            <Route
              path="/reports/sessions"
              element={
                <PrivateRoute>
                  <SessionHistoryPage />
                </PrivateRoute>
              }
            />
            <Route
              path="/reports/sessions/:sessionId"
              element={
                <PrivateRoute>
                  <SessionDetailPage />
                </PrivateRoute>
              }
            />
            <Route
              path="/org/:orgId/settings"
              element={
                <PrivateRoute>
                  <OrganizationSettingsPage />
                </PrivateRoute>
              }
            />
            <Route
              path="/org/:orgId/members"
              element={
                <PrivateRoute>
                  <MemberManagementPage />
                </PrivateRoute>
              }
            />
            <Route
              path="/org/:orgId/sso"
              element={
                <PrivateRoute>
                  <SsoConfigPage />
                </PrivateRoute>
              }
            />
            <Route
              path="/org/:orgId/audit-logs"
              element={
                <PrivateRoute>
                  <AuditLogPage />
                </PrivateRoute>
              }
            />
          </Routes>
        </BrowserRouter>
      </UpgradeModalProvider>
    </QueryClientProvider>
  );
};

export default App;
