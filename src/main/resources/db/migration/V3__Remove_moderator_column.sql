-- Remove is_moderator column as any player can control the room
ALTER TABLE players DROP COLUMN IF EXISTS is_moderator;