-- Add is_moderator column to players table
ALTER TABLE players ADD COLUMN is_moderator BOOLEAN DEFAULT false;