/**
 * Room page component - main orchestrator for the voting UI.
 * Manages WebSocket connection, handles voting flow, and composes all room components.
 */

import React, { useState, useCallback, useMemo, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { useWebSocket } from '@/hooks/useWebSocket';
import { useRoomStore } from '@/stores/roomStore';
import { useAuthStore } from '@/stores/authStore';
import { ParticipantList } from '@/components/room/ParticipantList';
import { DeckSelector } from '@/components/room/DeckSelector';
import { RevealView } from '@/components/room/RevealView';
import { HostControls } from '@/components/room/HostControls';
import { MessageType } from '@/types/websocket';

const RoomPage: React.FC = () => {
  const { roomId } = useParams<{ roomId: string }>();

  // WebSocket connection
  const { connectionStatus, isConnected, send, error } = useWebSocket(roomId || null);

  // Room state from store
  const title = useRoomStore((state) => state.title);
  const config = useRoomStore((state) => state.config);
  const currentRound = useRoomStore((state) => state.currentRound);
  const revealedVotes = useRoomStore((state) => state.revealedVotes);
  const participants = useRoomStore((state) => state.getParticipantsArray());

  // Auth state
  const user = useAuthStore((state) => state.user);

  // Local optimistic state
  const [selectedCard, setSelectedCard] = useState<string | null>(null);
  const [hasVotedOptimistic, setHasVotedOptimistic] = useState(false);

  // Get current participant to determine role
  const currentParticipant = useMemo(() => {
    if (!user) return null;
    return participants.find((p) => p.participantId === user.userId);
  }, [participants, user]);

  const isHost = currentParticipant?.role === 'HOST';
  const isObserver = currentParticipant?.role === 'OBSERVER';

  // Check if current user has voted (from server state)
  const hasVotedServer = currentParticipant?.hasVoted || false;

  // Combined vote status (optimistic + server)
  const hasVoted = hasVotedOptimistic || hasVotedServer;

  // Reset optimistic state when round changes or is revealed
  useEffect(() => {
    if (!currentRound || currentRound.revealed) {
      setHasVotedOptimistic(false);
      setSelectedCard(null);
    }
  }, [currentRound?.roundId, currentRound?.revealed]);

  // ========================================
  // Event Handlers
  // ========================================

  /**
   * Handle card selection and vote casting.
   */
  const handleCardSelect = useCallback(
    (cardValue: string) => {
      if (!isConnected || hasVoted || isObserver) {
        return;
      }

      // Optimistic update
      setSelectedCard(cardValue);
      setHasVotedOptimistic(true);

      // Send vote to server
      console.log('[RoomPage] Casting vote:', cardValue);
      send(MessageType.VOTE_CAST, { cardValue });
    },
    [isConnected, hasVoted, isObserver, send]
  );

  /**
   * Handle starting a new round (host only).
   */
  const handleStartRound = useCallback(() => {
    if (!isConnected || !isHost) {
      return;
    }

    console.log('[RoomPage] Starting new round');
    send(MessageType.ROUND_START, {
      storyTitle: 'Story estimation', // Could be made configurable
      timerDurationSeconds: config?.timerEnabled ? config.timerDurationSeconds : undefined,
    });
  }, [isConnected, isHost, config, send]);

  /**
   * Handle revealing votes (host only).
   */
  const handleReveal = useCallback(() => {
    if (!isConnected || !isHost) {
      return;
    }

    console.log('[RoomPage] Revealing votes');
    send(MessageType.ROUND_REVEAL, {});
  }, [isConnected, isHost, send]);

  /**
   * Handle resetting the round (host only).
   */
  const handleReset = useCallback(() => {
    if (!isConnected || !isHost) {
      return;
    }

    console.log('[RoomPage] Resetting round');
    send(MessageType.ROUND_RESET, { clearVotes: true });

    // Clear optimistic state
    setHasVotedOptimistic(false);
    setSelectedCard(null);
  }, [isConnected, isHost, send]);

  // ========================================
  // Derived State
  // ========================================

  const hasVotes = participants.some((p) => p.hasVoted);

  // ========================================
  // Render Helpers
  // ========================================

  const renderConnectionStatus = () => {
    if (connectionStatus === 'connecting') {
      return (
        <div className="bg-blue-100 dark:bg-blue-900/30 text-blue-800 dark:text-blue-200 px-4 py-2 rounded-lg flex items-center space-x-2">
          <div className="animate-spin rounded-full h-4 w-4 border-2 border-blue-800 dark:border-blue-200 border-t-transparent"></div>
          <span className="text-sm font-medium">Connecting to room...</span>
        </div>
      );
    }

    if (connectionStatus === 'disconnected') {
      return (
        <div className="bg-red-100 dark:bg-red-900/30 text-red-800 dark:text-red-200 px-4 py-2 rounded-lg flex items-center space-x-2">
          <svg className="h-5 w-5" fill="currentColor" viewBox="0 0 20 20">
            <path
              fillRule="evenodd"
              d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z"
              clipRule="evenodd"
            />
          </svg>
          <span className="text-sm font-medium">Disconnected - Reconnecting...</span>
        </div>
      );
    }

    return (
      <div className="bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-200 px-4 py-2 rounded-lg flex items-center space-x-2">
        <div className="h-2 w-2 bg-green-600 dark:bg-green-400 rounded-full"></div>
        <span className="text-sm font-medium">Connected</span>
      </div>
    );
  };

  const renderError = () => {
    if (!error) return null;

    return (
      <div className="bg-red-100 dark:bg-red-900/30 text-red-800 dark:text-red-200 px-4 py-3 rounded-lg mb-4">
        <div className="flex items-start space-x-2">
          <svg className="h-5 w-5 mt-0.5 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
            <path
              fillRule="evenodd"
              d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z"
              clipRule="evenodd"
            />
          </svg>
          <div className="flex-1">
            <p className="font-semibold">{error.error}</p>
            <p className="text-sm mt-1">{error.message}</p>
          </div>
        </div>
      </div>
    );
  };

  const renderVotingArea = () => {
    // Show revealed view if round is revealed
    if (currentRound?.revealed && revealedVotes) {
      return <RevealView />;
    }

    // Show voting cards if round is in progress and user is not observer
    if (currentRound && !isObserver) {
      return (
        <div className="bg-white dark:bg-gray-800 rounded-xl p-6 shadow-lg">
          <div className="mb-4">
            <h3 className="text-xl font-semibold text-gray-900 dark:text-white mb-2">
              Cast Your Vote
            </h3>
            {currentRound.storyTitle && (
              <p className="text-gray-600 dark:text-gray-300">
                Story: <span className="font-medium">{currentRound.storyTitle}</span>
              </p>
            )}
          </div>

          {hasVoted ? (
            <div className="text-center py-12">
              <div className="inline-block bg-gradient-to-br from-green-500 to-green-600 text-white rounded-full p-4 mb-4">
                <svg className="h-12 w-12" fill="currentColor" viewBox="0 0 20 20">
                  <path
                    fillRule="evenodd"
                    d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z"
                    clipRule="evenodd"
                  />
                </svg>
              </div>
              <p className="text-lg font-semibold text-gray-900 dark:text-white mb-2">
                Vote Cast!
              </p>
              <p className="text-gray-600 dark:text-gray-400">
                {selectedCard && `You voted: ${selectedCard}`}
              </p>
              <p className="text-sm text-gray-500 dark:text-gray-500 mt-2">
                Waiting for host to reveal votes...
              </p>
            </div>
          ) : (
            <DeckSelector
              onCardSelect={handleCardSelect}
              disabled={!isConnected || hasVoted}
              selectedValue={selectedCard}
              deckType={config?.deckType}
              customDeck={config?.customDeck}
            />
          )}
        </div>
      );
    }

    // Waiting for round to start
    return (
      <div className="bg-white dark:bg-gray-800 rounded-xl p-12 shadow-lg text-center">
        <div className="text-gray-400 dark:text-gray-500 mb-4">
          <svg className="h-16 w-16 mx-auto" fill="currentColor" viewBox="0 0 20 20">
            <path
              fillRule="evenodd"
              d="M10 18a8 8 0 100-16 8 8 0 000 16zm1-12a1 1 0 10-2 0v4a1 1 0 00.293.707l2.828 2.829a1 1 0 101.415-1.415L11 9.586V6z"
              clipRule="evenodd"
            />
          </svg>
        </div>
        <h3 className="text-xl font-semibold text-gray-900 dark:text-white mb-2">
          Waiting for Round to Start
        </h3>
        <p className="text-gray-600 dark:text-gray-400">
          {isHost
            ? 'Click "Start New Round" to begin voting'
            : 'The host will start the round soon'}
        </p>
      </div>
    );
  };

  // ========================================
  // Main Render
  // ========================================

  if (!roomId) {
    return (
      <div className="min-h-screen bg-gray-50 dark:bg-gray-900 flex items-center justify-center">
        <div className="text-center">
          <p className="text-xl text-gray-600 dark:text-gray-400">Invalid room ID</p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-gray-50 to-gray-100 dark:from-gray-900 dark:to-gray-800">
      <div className="container mx-auto px-4 py-8 max-w-7xl">
        {/* Header */}
        <div className="mb-6">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h1 className="text-3xl font-bold text-gray-900 dark:text-white mb-2">
                {title || 'Planning Poker Room'}
              </h1>
              <p className="text-gray-600 dark:text-gray-300">
                Room ID:{' '}
                <span className="font-mono text-primary-600 dark:text-primary-400">{roomId}</span>
              </p>
            </div>
            {renderConnectionStatus()}
          </div>

          {/* Round Info */}
          {currentRound && (
            <div className="bg-primary-100 dark:bg-primary-900/30 text-primary-900 dark:text-primary-100 px-4 py-2 rounded-lg">
              <span className="font-semibold">Round {currentRound.roundNumber}</span>
              {currentRound.revealed && (
                <span className="ml-3 text-sm font-medium">• Revealed</span>
              )}
            </div>
          )}
        </div>

        {/* Error Display */}
        {renderError()}

        {/* Main Content Grid */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Left Column - Participants & Host Controls */}
          <div className="lg:col-span-1 space-y-6">
            <ParticipantList />

            {isHost && (
              <HostControls
                onStartRound={handleStartRound}
                onReveal={handleReveal}
                onReset={handleReset}
                roundInProgress={!!currentRound}
                roundRevealed={currentRound?.revealed || false}
                hasVotes={hasVotes}
                disabled={!isConnected}
              />
            )}
          </div>

          {/* Right Column - Voting Area */}
          <div className="lg:col-span-2">{renderVotingArea()}</div>
        </div>
      </div>
    </div>
  );
};

export default RoomPage;
