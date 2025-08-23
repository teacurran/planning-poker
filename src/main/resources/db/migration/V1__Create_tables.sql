-- Create rooms table
CREATE TABLE rooms (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    is_voting_active BOOLEAN DEFAULT true,
    are_cards_revealed BOOLEAN DEFAULT false
);

-- Create players table
CREATE TABLE players (
    id VARCHAR(36) PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    session_id VARCHAR(50),
    room_id VARCHAR(36) REFERENCES rooms(id) ON DELETE CASCADE,
    is_observer BOOLEAN DEFAULT false,
    joined_at TIMESTAMP NOT NULL,
    is_connected BOOLEAN DEFAULT true
);

-- Create votes table
CREATE TABLE votes (
    id VARCHAR(36) PRIMARY KEY,
    room_id VARCHAR(36) REFERENCES rooms(id) ON DELETE CASCADE,
    player_id VARCHAR(36) REFERENCES players(id) ON DELETE CASCADE,
    value VARCHAR(10) NOT NULL,
    voting_round INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL
);

-- Create indexes
CREATE INDEX idx_players_room ON players(room_id);
CREATE INDEX idx_votes_room ON votes(room_id);
CREATE INDEX idx_votes_player ON votes(player_id);
CREATE INDEX idx_votes_round ON votes(room_id, voting_round);