/**
 * Button that triggers the preferences quick view modal from the dashboard.
 */

import React from 'react';

interface ViewPreferencesButtonProps {
  onClick: () => void;
}

export const ViewPreferencesButton: React.FC<ViewPreferencesButtonProps> = ({ onClick }) => {
  return (
    <button
      onClick={onClick}
      className="w-full border-2 border-primary-600 text-primary-700 dark:text-primary-300 font-bold py-4 px-6 rounded-lg shadow-sm hover:shadow-lg transition-all duration-200 flex items-center justify-center gap-2 bg-white dark:bg-gray-800"
      aria-label="View preferences"
    >
      <svg
        className="w-6 h-6"
        fill="none"
        stroke="currentColor"
        viewBox="0 0 24 24"
        xmlns="http://www.w3.org/2000/svg"
        aria-hidden="true"
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth={1.8}
          d="M11.049 2.927c.3-.921 1.603-.921 1.902 0l.196.603a1 1 0 00.95.69h.634c.969 0 1.371 1.24.588 1.81l-.513.372a1 1 0 000 1.624l.513.372c.783.57.38 1.81-.588 1.81h-.634a1 1 0 00-.95.69l-.196.603c-.3.921-1.603.921-1.902 0l-.196-.603a1 1 0 00-.95-.69H9.269c-.969 0-1.371-1.24-.588-1.81l.513-.372a1 1 0 000-1.624l-.513-.372c-.783-.57-.38-1.81.588-1.81h.634a1 1 0 00.95-.69l.196-.603z"
        />
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth={1.8}
          d="M15 13a3 3 0 11-6 0 3 3 0 016 0z"
        />
      </svg>
      <span>View Preferences</span>
    </button>
  );
};
