/**
 * Create room button component for the dashboard.
 * Navigates to the room creation form when clicked.
 */

import React from 'react';
import { useNavigate } from 'react-router-dom';

export const CreateRoomButton: React.FC = () => {
  const navigate = useNavigate();

  const handleClick = () => {
    navigate('/rooms/new');
  };

  return (
    <button
      onClick={handleClick}
      className="w-full bg-primary-600 hover:bg-primary-700 text-white font-bold py-4 px-6 rounded-lg shadow-md hover:shadow-lg transition-all duration-200 flex items-center justify-center gap-2"
      aria-label="Create new room"
    >
      {/* Plus icon */}
      <svg
        className="w-6 h-6"
        fill="none"
        stroke="currentColor"
        viewBox="0 0 24 24"
        xmlns="http://www.w3.org/2000/svg"
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth={2}
          d="M12 4v16m8-8H4"
        />
      </svg>
      <span>Create New Room</span>
    </button>
  );
};
