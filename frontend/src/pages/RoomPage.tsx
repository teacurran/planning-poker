import React from 'react';
import { useParams } from 'react-router-dom';

const RoomPage: React.FC = () => {
  const { roomId } = useParams<{ roomId: string }>();

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
      <div className="container mx-auto px-4 py-8">
        <div className="mb-8">
          <h1 className="text-3xl font-bold text-gray-900 dark:text-white mb-2">
            Estimation Room
          </h1>
          <p className="text-gray-600 dark:text-gray-300">
            Room ID: <span className="font-mono text-primary-600 dark:text-primary-400">{roomId}</span>
          </p>
        </div>

        {/* Test div with Tailwind colors */}
        <div className="bg-gradient-to-r from-primary-500 to-primary-700 text-white p-8 rounded-xl shadow-lg mb-8">
          <h2 className="text-2xl font-bold mb-4">Active Estimation Session</h2>
          <p className="text-lg">
            This is a placeholder for the real-time estimation interface
          </p>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <div className="bg-white dark:bg-gray-800 p-6 rounded-lg shadow-md">
            <h3 className="text-xl font-semibold text-gray-900 dark:text-white mb-4">
              Participants
            </h3>
            <p className="text-gray-600 dark:text-gray-300">
              Participant list will appear here
            </p>
          </div>

          <div className="bg-white dark:bg-gray-800 p-6 rounded-lg shadow-md">
            <h3 className="text-xl font-semibold text-gray-900 dark:text-white mb-4">
              Voting Cards
            </h3>
            <div className="grid grid-cols-5 gap-2">
              {['1', '2', '3', '5', '8', '13', '21', '34', '?'].map((value) => (
                <button
                  key={value}
                  className="bg-primary-100 hover:bg-primary-200 dark:bg-primary-900 dark:hover:bg-primary-800
                           text-primary-900 dark:text-primary-100 font-bold py-3 px-2 rounded-lg
                           transition-colors duration-200"
                >
                  {value}
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default RoomPage;
