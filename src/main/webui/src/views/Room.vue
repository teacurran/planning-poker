<template>
  <div class="room-container">
    <Dialog v-model:visible="showUsernameDialog" :modal="true" :closable="false" :draggable="false">
      <template #header>
        <h3>Join Room</h3>
      </template>
      <div class="p-field">
        <label for="username">Enter your name</label>
        <InputText 
          id="username" 
          v-model="username" 
          placeholder="Your name"
          @keyup.enter="joinRoom"
          class="w-full"
        />
      </div>
      <template #footer>
        <Button label="Join" @click="joinRoom" :disabled="!username" />
      </template>
    </Dialog>
    
    <div class="room-header">
      <h2>{{ roomState?.roomName || 'Planning Poker Room' }}</h2>
      <div class="room-id">Room ID: {{ roomId }}</div>
    </div>
    
    <div class="poker-table-container">
      <div class="poker-table">
        <div class="table-center">
          <div v-if="roomState?.areCardsRevealed" class="results-display">
            <h3>Results</h3>
            <div class="consensus-display" v-if="roomState?.votingStats">
              <div v-if="roomState.votingStats.consensus">
                <div class="consensus-label">Consensus</div>
                <div class="consensus-value">{{ roomState.votingStats.consensus }}</div>
              </div>
              <div v-else>
                <div class="consensus-label">Average</div>
                <div class="consensus-value">{{ roomState.votingStats.average || '-' }}</div>
              </div>
            </div>
          </div>
          <div v-else class="voting-status">
            <h3>{{ votingStatusText }}</h3>
          </div>
        </div>
        
        <div class="players-around-table">
          <div 
            v-for="(player, index) in roomState?.players" 
            :key="player.id"
            :class="getPlayerPositionClass(index, roomState?.players.length)"
            class="player-seat"
          >
            <div class="player-card-slot">
              <div v-if="player.hasVoted || player.vote" class="card-in-slot">
                <div v-if="roomState?.areCardsRevealed" class="card-face-up">
                  {{ player.vote }}
                </div>
                <div v-else class="card-face-down">
                  <div class="card-back"></div>
                </div>
              </div>
              <div v-else class="empty-slot">
                <div class="slot-placeholder"></div>
              </div>
            </div>
            <div class="player-info">
              <span class="player-name">{{ player.username }}</span>
              <div class="player-badges">
                <Tag v-if="player.isObserver" value="Observer" severity="info" size="small" />
              </div>
            </div>
          </div>
        </div>
      </div>
      
      <div class="controls-section">
        <!-- Debug info -->
        <div v-if="true" style="background: #f0f0f0; padding: 10px; margin-bottom: 10px; font-size: 12px;">
          <div>Username: {{ username }}</div>
          <div>My Player ID: {{ myPlayerId }}</div>
          <div>Current Player: {{ currentPlayer?.username }}</div>
          <div>Is Observer: {{ isObserver }}</div>
          <div>Room has {{ roomState?.players?.length || 0 }} players</div>
        </div>
        
        <div class="card-selection" v-if="!isObserver">
          <h3>Select Your Card</h3>
          <div class="card-options">
            <div 
              v-for="value in cardValues" 
              :key="value"
              :class="['selectable-card', { 'selected': selectedCard === value }]"
              @click="selectCard(value)"
            >
              {{ value }}
            </div>
          </div>
        </div>
        
        <div class="room-controls" v-if="!isObserver">
          <h3>Room Controls</h3>
          <div class="control-buttons">
            <Button 
              :label="roomState?.areCardsRevealed ? 'Hide Cards' : 'Reveal Cards'"
              :icon="roomState?.areCardsRevealed ? 'pi pi-eye-slash' : 'pi pi-eye'"
              @click="toggleRevealCards"
              class="w-full mb-2"
              :severity="roomState?.areCardsRevealed ? 'secondary' : 'primary'"
            />
            <Button 
              label="New Round" 
              icon="pi pi-refresh"
              @click="resetVotes"
              severity="success"
              class="w-full"
            />
          </div>
        </div>
        
        <div class="player-controls">
          <Button 
            :label="isObserver ? 'Join as Player' : 'Become Observer'"
            :icon="isObserver ? 'pi pi-user' : 'pi pi-eye'"
            @click="toggleObserver"
            severity="secondary"
            class="w-full"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import Card from 'primevue/card'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Tag from 'primevue/tag'
import { WebSocketService } from '../services/websocket'

export default {
  name: 'Room',
  components: {
    Card,
    Button,
    Dialog,
    InputText,
    Tag
  },
  setup() {
    const route = useRoute()
    const roomId = route.params.roomId
    const username = ref('')
    const showUsernameDialog = ref(true)
    const roomState = ref(null)
    const selectedCard = ref(null)
    const wsService = ref(null)
    const myPlayerId = ref(null)
    
    const cardValues = ['1', '3', '5', '8']
    
    const currentPlayer = computed(() => {
      if (!roomState.value || !myPlayerId.value) return null
      return roomState.value.players.find(p => p.id === myPlayerId.value)
    })
    
    const isObserver = computed(() => {
      return currentPlayer.value?.isObserver || false
    })
    
    
    const votingStatusText = computed(() => {
      if (!roomState.value) return 'Waiting...'
      const voted = roomState.value.players.filter(p => !p.isObserver && p.hasVoted).length
      const total = roomState.value.players.filter(p => !p.isObserver).length
      if (voted === 0) return 'Waiting for votes...'
      if (voted === total) return 'All players have voted!'
      return `${voted} of ${total} players voted`
    })
    
    const getPlayerPositionClass = (index, total) => {
      const positions = [
        'position-top',
        'position-top-right',
        'position-right',
        'position-bottom-right',
        'position-bottom',
        'position-bottom-left',
        'position-left',
        'position-top-left'
      ]
      
      if (total <= 8) {
        const step = Math.floor(8 / total)
        return positions[index * step]
      }
      
      const angle = (index / total) * 2 * Math.PI - Math.PI / 2
      const x = 50 + 40 * Math.cos(angle)
      const y = 50 + 40 * Math.sin(angle)
      
      return {
        left: `${x}%`,
        top: `${y}%`,
        position: 'absolute',
        transform: 'translate(-50%, -50%)'
      }
    }
    
    const joinRoom = () => {
      if (!username.value) return
      
      showUsernameDialog.value = false
      wsService.value = new WebSocketService(roomId)
      
      wsService.value.onMessage((message) => {
        console.log('Received message:', message)
        if (message.type === 'ROOM_STATE') {
          roomState.value = message.roomState
          console.log('Room state updated:', roomState.value)
          console.log('Players:', roomState.value.players)
          console.log('Looking for username:', username.value)
          
          const myPlayer = roomState.value.players.find(p => p.username === username.value)
          if (myPlayer) {
            myPlayerId.value = myPlayer.id
            console.log('Found my player:', myPlayer)
            if (myPlayer.vote && roomState.value.areCardsRevealed) {
              selectedCard.value = myPlayer.vote
            }
          } else {
            console.warn('Could not find player with username:', username.value)
          }
        } else if (message.type === 'ERROR') {
          console.error('WebSocket error:', message.message)
        }
      })
      
      wsService.value.connect().then(() => {
        wsService.value.send({
          type: 'room.join.v1',
          requestId: Date.now().toString(),
          payload: {
            displayName: username.value
          }
        })
      })
    }
    
    const selectCard = (value) => {
      if (isObserver.value) return
      
      selectedCard.value = value
      wsService.value.send({
        type: 'VOTE',
        value: value
      })
    }
    
    const toggleRevealCards = () => {
      if (roomState.value?.areCardsRevealed) {
        wsService.value.send({ type: 'HIDE_CARDS' })
      } else {
        wsService.value.send({ type: 'REVEAL_CARDS' })
      }
    }
    
    const resetVotes = () => {
      selectedCard.value = null
      wsService.value.send({ type: 'RESET_VOTES' })
    }
    
    const toggleObserver = () => {
      wsService.value.send({ type: 'TOGGLE_OBSERVER' })
      selectedCard.value = null
    }
    
    onUnmounted(() => {
      if (wsService.value) {
        wsService.value.disconnect()
      }
    })
    
    return {
      roomId,
      username,
      showUsernameDialog,
      roomState,
      selectedCard,
      cardValues,
      isObserver,
      votingStatusText,
      myPlayerId,
      joinRoom,
      selectCard,
      toggleRevealCards,
      resetVotes,
      toggleObserver,
      getPlayerPositionClass
    }
  }
}
</script>

<style scoped>
.room-container {
  min-height: 100vh;
  padding: 20px;
  background: #2c3e50;
}

.room-header {
  text-align: center;
  color: white;
  margin-bottom: 30px;
}

.room-header h2 {
  margin: 0 0 10px;
  font-size: 2rem;
}

.room-id {
  font-size: 0.9rem;
  opacity: 0.8;
}

.poker-table-container {
  max-width: 1200px;
  margin: 0 auto;
}

.poker-table {
  position: relative;
  width: 100%;
  height: 500px;
  background: radial-gradient(ellipse at center, #27ae60, #229954);
  border-radius: 250px / 150px;
  border: 15px solid #8b6914;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.5);
  margin-bottom: 40px;
}

.table-center {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  text-align: center;
  color: white;
}

.results-display h3,
.voting-status h3 {
  margin: 0 0 15px;
  font-size: 1.5rem;
  text-shadow: 2px 2px 4px rgba(0, 0, 0, 0.3);
}

.consensus-display {
  background: rgba(255, 255, 255, 0.9);
  padding: 20px 40px;
  border-radius: 15px;
  color: #2c3e50;
}

.consensus-label {
  font-size: 0.9rem;
  color: #7f8c8d;
  margin-bottom: 5px;
}

.consensus-value {
  font-size: 3rem;
  font-weight: bold;
  color: #27ae60;
}

.players-around-table {
  position: relative;
  width: 100%;
  height: 100%;
}

.player-seat {
  position: absolute;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.position-top {
  top: -60px;
  left: 50%;
  transform: translateX(-50%);
}

.position-top-right {
  top: 10%;
  right: 15%;
}

.position-right {
  top: 50%;
  right: -60px;
  transform: translateY(-50%);
}

.position-bottom-right {
  bottom: 10%;
  right: 15%;
}

.position-bottom {
  bottom: -60px;
  left: 50%;
  transform: translateX(-50%);
}

.position-bottom-left {
  bottom: 10%;
  left: 15%;
}

.position-left {
  top: 50%;
  left: -60px;
  transform: translateY(-50%);
}

.position-top-left {
  top: 10%;
  left: 15%;
}

.player-card-slot {
  width: 70px;
  height: 100px;
}

.card-in-slot {
  width: 100%;
  height: 100%;
}

.card-face-up {
  width: 100%;
  height: 100%;
  background: white;
  border: 2px solid #34495e;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 2rem;
  font-weight: bold;
  color: #2c3e50;
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.2);
}

.card-face-down {
  width: 100%;
  height: 100%;
}

.card-back {
  width: 100%;
  height: 100%;
  background: linear-gradient(45deg, #3498db, #2980b9);
  border: 2px solid #34495e;
  border-radius: 8px;
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.2);
  position: relative;
  overflow: hidden;
}

.card-back::after {
  content: '';
  position: absolute;
  top: 10%;
  left: 10%;
  width: 80%;
  height: 80%;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-radius: 4px;
}

.empty-slot {
  width: 100%;
  height: 100%;
}

.slot-placeholder {
  width: 100%;
  height: 100%;
  border: 2px dashed rgba(255, 255, 255, 0.3);
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.1);
}

.player-info {
  text-align: center;
  background: rgba(255, 255, 255, 0.95);
  padding: 5px 10px;
  border-radius: 5px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
}

.player-name {
  font-weight: 500;
  color: #2c3e50;
  font-size: 0.9rem;
}

.player-badges {
  display: flex;
  gap: 5px;
  justify-content: center;
  margin-top: 3px;
}

.controls-section {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 20px;
}

.card-selection,
.room-controls,
.player-controls {
  background: white;
  padding: 20px;
  border-radius: 10px;
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
}

.card-selection h3,
.room-controls h3 {
  margin: 0 0 15px;
  color: #2c3e50;
}

.card-options {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}

.selectable-card {
  aspect-ratio: 2/3;
  background: white;
  border: 3px solid #3498db;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.5rem;
  font-weight: bold;
  color: #2c3e50;
  cursor: pointer;
  transition: all 0.2s;
}

.selectable-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 5px 15px rgba(52, 152, 219, 0.3);
}

.selectable-card.selected {
  background: #3498db;
  color: white;
  transform: translateY(-5px);
  box-shadow: 0 5px 15px rgba(52, 152, 219, 0.5);
}

.control-buttons {
  display: flex;
  flex-direction: column;
}

.w-full {
  width: 100%;
}

.mb-2 {
  margin-bottom: 0.5rem;
}

@media (max-width: 768px) {
  .poker-table {
    height: 400px;
    border-radius: 200px / 120px;
  }
  
  .controls-section {
    grid-template-columns: 1fr;
  }
}
</style>