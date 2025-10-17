/**
 * PrivateRoute component for protecting routes that require authentication.
 * Redirects unauthenticated users to the login page.
 */

import React from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '@/hooks/useAuth';

interface PrivateRouteProps {
  children: React.ReactNode;
}

/**
 * Wrapper component that protects routes requiring authentication.
 * If the user is not authenticated, redirects to /login.
 * If authenticated, renders the child components.
 *
 * @example
 * ```tsx
 * <Route path="/dashboard" element={
 *   <PrivateRoute>
 *     <DashboardPage />
 *   </PrivateRoute>
 * } />
 * ```
 */
const PrivateRoute: React.FC<PrivateRouteProps> = ({ children }) => {
  const { isAuthenticated } = useAuth();

  if (!isAuthenticated) {
    // Redirect to login page if not authenticated
    return <Navigate to="/login" replace />;
  }

  // Render protected content if authenticated
  return <>{children}</>;
};

export default PrivateRoute;
