import React from 'react';
import Button from '@/components/common/Button';

const HomePage: React.FC = () => {
  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
      <div className="container mx-auto px-4 py-16">
        <div className="text-center">
          <h1 className="text-4xl font-bold text-gray-900 dark:text-white mb-4">
            Welcome to Scrum Poker Platform
          </h1>
          <p className="text-xl text-gray-600 dark:text-gray-300 mb-8">
            Real-time collaborative estimation for agile teams
          </p>

          {/* Test div with Tailwind colors and path alias import */}
          <div className="inline-block bg-primary-500 text-white px-8 py-4 rounded-lg shadow-lg mb-4">
            <p className="text-lg font-semibold">
              Get started with your first estimation session
            </p>
          </div>

          <div className="mt-4">
            <Button variant="primary" className="mx-2">
              Create Room
            </Button>
            <Button variant="secondary" className="mx-2">
              Join Room
            </Button>
          </div>

          <div className="mt-8 grid grid-cols-1 md:grid-cols-3 gap-6 max-w-4xl mx-auto">
            <div className="bg-white dark:bg-gray-800 p-6 rounded-lg shadow-md">
              <h3 className="text-lg font-semibold text-primary-600 dark:text-primary-400 mb-2">
                Create Room
              </h3>
              <p className="text-gray-600 dark:text-gray-300">
                Start a new estimation session
              </p>
            </div>

            <div className="bg-white dark:bg-gray-800 p-6 rounded-lg shadow-md">
              <h3 className="text-lg font-semibold text-primary-600 dark:text-primary-400 mb-2">
                Join Room
              </h3>
              <p className="text-gray-600 dark:text-gray-300">
                Enter an existing session
              </p>
            </div>

            <div className="bg-white dark:bg-gray-800 p-6 rounded-lg shadow-md">
              <h3 className="text-lg font-semibold text-primary-600 dark:text-primary-400 mb-2">
                Dashboard
              </h3>
              <p className="text-gray-600 dark:text-gray-300">
                View your session history
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default HomePage;
