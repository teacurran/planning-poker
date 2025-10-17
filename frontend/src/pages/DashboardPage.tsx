import React from 'react';

const DashboardPage: React.FC = () => {
  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
      <div className="container mx-auto px-4 py-8">
        <div className="mb-8">
          <h1 className="text-3xl font-bold text-gray-900 dark:text-white mb-2">
            User Dashboard
          </h1>
          <p className="text-gray-600 dark:text-gray-300">
            View your estimation history and analytics
          </p>
        </div>

        {/* Test div with Tailwind colors */}
        <div className="bg-primary-600 text-white p-6 rounded-lg shadow-lg mb-8">
          <h2 className="text-xl font-bold mb-2">Quick Stats</h2>
          <p className="text-lg">Your estimation sessions and metrics will appear here</p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          <div className="bg-white dark:bg-gray-800 p-6 rounded-lg shadow-md border-l-4 border-primary-500">
            <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-2">
              Total Sessions
            </h3>
            <p className="text-3xl font-bold text-primary-600 dark:text-primary-400">0</p>
            <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
              All time
            </p>
          </div>

          <div className="bg-white dark:bg-gray-800 p-6 rounded-lg shadow-md border-l-4 border-green-500">
            <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-2">
              Active Rooms
            </h3>
            <p className="text-3xl font-bold text-green-600 dark:text-green-400">0</p>
            <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
              Currently active
            </p>
          </div>

          <div className="bg-white dark:bg-gray-800 p-6 rounded-lg shadow-md border-l-4 border-blue-500">
            <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-2">
              Team Members
            </h3>
            <p className="text-3xl font-bold text-blue-600 dark:text-blue-400">0</p>
            <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
              Collaborators
            </p>
          </div>
        </div>

        <div className="mt-8 bg-white dark:bg-gray-800 rounded-lg shadow-md p-6">
          <h2 className="text-2xl font-bold text-gray-900 dark:text-white mb-4">
            Recent Sessions
          </h2>
          <p className="text-gray-600 dark:text-gray-300">
            Your recent estimation sessions will be listed here
          </p>
        </div>
      </div>
    </div>
  );
};

export default DashboardPage;
