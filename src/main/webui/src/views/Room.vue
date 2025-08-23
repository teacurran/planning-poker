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
    
    <div class="main-content">
      <div class="players-section">
        <Card>
          <template #title>Players</template>
          <template #content>
            <div class="players-list">
              <div v-for="player in roomState?.players" :key="player.id" class="player-item">
                <div class="player-info">
                  <i :class="getPlayerIcon(player)" class="player-icon"></i>
                  <span class="player-name">{{ player.username }}</span>
                  <Tag v-if="player.isObserver" value="Observer" severity="info" />
                </div>
                <div class="player-vote">
                  <PokerCard 
                    :revealed="roomState?.areCardsRevealed" 
                    :value="player.vote"
                    :hasVoted="player.hasVoted"
                    :small="true"
                  />
                </div>
              </div>
            </div>
          </template>
        </Card>
        
        <Card class="mt-3">
          <template #title>Actions</template>
          <template #content>
            <div class="actions">
              <Button 
                label="Toggle Observer" 
                @click="toggleObserver"
                severity="secondary"
                class="w-full mb-2"
              />
              <Button 
                label="Reveal Cards" 
                @click="revealCards"
                :disabled="!roomState?.isVotingActive || roomState?.areCardsRevealed"
                class="w-full mb-2"
              />
              <Button 
                label="New Round" 
                @click="resetVotes"
                severity="success"
                class="w-full"
              />
            </div>
          </template>
        </Card>
      </div>
      
      <div class="voting-section">
        <Card>
          <template #title>
            <span v-if="!roomState?.areCardsRevealed">Select Your Card</span>
            <span v-else>Results</span>
          </template>
          <template #content>
            <div v-if="roomState?.areCardsRevealed && roomState?.votingStats" class="results">
              <div class="stats-grid">
                <div class="stat-item">
                  <div class="stat-label">Average</div>
                  <div class="stat-value">{{ roomState.votingStats.average || '-' }}</div>
                </div>
                <div class="stat-item">
                  <div class="stat-label">Consensus</div>
                  <div class="stat-value">{{ roomState.votingStats.consensus || 'None' }}</div>
                </div>
                <div class="stat-item">
                  <div class="stat-label">Voted</div>
                  <div class="stat-value">{{ roomState.votingStats.votedPlayers }}/{{ roomState.votingStats.totalPlayers }}</div>
                </div>
              </div>
              
              <div class="vote-distribution">
                <h4>Vote Distribution</h4>
                <div class="vote-counts">
                  <div v-for="vc in roomState.votingStats.voteCounts" :key="vc.value" class="vote-count">
                    <span class="vote-value">{{ vc.value }}</span>
                    <span class="vote-count-number">{{ vc.count }}</span>
                  </div>
                </div>
              </div>
            </div>
            
            <div v-else class="card-selection">
              <div class="fibonacci-cards">
                <div v-for="value in fibonacciValues" :key="value" class="card-wrapper">
                  <PokerCard 
                    :value="value"
                    :selected="selectedCard === value"
                    :clickable="!isObserver"
                    @click="selectCard(value)"
                  />
                </div>
              </div>
            </div>
          </template>
        </Card>
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
import PokerCard from '../components/PokerCard.vue'
import { WebSocketService } from '../services/websocket'

export default {
  name: 'Room',
  components: {
    Card,
    Button,
    Dialog,
    InputText,
    Tag,
    PokerCard
  },
  setup() {
    const route = useRoute()
    const roomId = route.params.roomId
    const username = ref('')
    const showUsernameDialog = ref(true)
    const roomState = ref(null)
    const selectedCard = ref(null)
    const wsService = ref(null)
    
    const fibonacciValues = ['0', '1', '2', '3', '5', '8', '13', '21', '34', '55', '89', '?', '☕']
    
    const currentPlayer = computed(() => {
      if (!roomState.value || !wsService.value) return null
      return roomState.value.players.find(p => p.sessionId === wsService.value.sessionId)
    })
    
    const isObserver = computed(() => {
      return currentPlayer.value?.isObserver || false
    })
    
    const joinRoom = () => {
      if (!username.value) return
      
      showUsernameDialog.value = false
      wsService.value = new WebSocketService(roomId)
      
      wsService.value.onMessage((message) => {
        if (message.type === 'ROOM_STATE') {
          roomState.value = message.roomState
          
          const myVote = roomState.value.players.find(p => p.username === username.value)?.vote
          if (myVote) {
            selectedCard.value = myVote
          }
        } else if (message.type === 'ERROR') {
          console.error('WebSocket error:', message.message)
        }
      })
      
      wsService.value.connect().then(() => {
        wsService.value.send({
          type: 'JOIN_ROOM',
          username: username.value
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
    
    const revealCards = () => {
      wsService.value.send({ type: 'REVEAL_CARDS' })
    }
    
    const resetVotes = () => {
      selectedCard.value = null
      wsService.value.send({ type: 'RESET_VOTES' })
    }
    
    const toggleObserver = () => {
      wsService.value.send({ type: 'TOGGLE_OBSERVER' })
    }
    
    const getPlayerIcon = (player) => {
      if (!player.isConnected) return 'pi pi-user-minus'
      if (player.isObserver) return 'pi pi-eye'
      return 'pi pi-user'
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
      fibonacciValues,
      isObserver,
      joinRoom,
      selectCard,
      revealCards,
      resetVotes,
      toggleObserver,
      getPlayerIcon
    }
  }
}
</script>

<style scoped>
.room-container {
  min-height: 100vh;
  padding: 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.room-header {
  text-align: center;
  color: white;
  margin-bottom: 30px;
}

.room-header h2 {
  margin: 0 0 10px;
  font-size: 2.5rem;
}

.room-id {
  font-size: 1rem;
  opacity: 0.9;
}

.main-content {
  display: grid;
  grid-template-columns: 350px 1fr;
  gap: 20px;
  max-width: 1400px;
  margin: 0 auto;
}

.players-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.player-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px;
  background: #f8f9fa;
  border-radius: 8px;
}

.player-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.player-icon {
  font-size: 1.2rem;
  color: #6c757d;
}

.player-name {
  font-weight: 500;
}

.fibonacci-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(100px, 1fr));
  gap: 15px;
  padding: 20px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  margin-bottom: 30px;
}

.stat-item {
  text-align: center;
  padding: 15px;
  background: #f8f9fa;
  border-radius: 8px;
}

.stat-label {
  font-size: 0.9rem;
  color: #6c757d;
  margin-bottom: 5px;
}

.stat-value {
  font-size: 1.8rem;
  font-weight: bold;
  color: #495057;
}

.vote-distribution h4 {
  margin-bottom: 15px;
  color: #495057;
}

.vote-counts {
  display: flex;
  gap: 15px;
  flex-wrap: wrap;
}

.vote-count {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 10px 15px;
  background: #e9ecef;
  border-radius: 8px;
}

.vote-value {
  font-weight: bold;
  font-size: 1.2rem;
  margin-bottom: 5px;
}

.vote-count-number {
  color: #6c757d;
}

.w-full {
  width: 100%;
}

.mb-2 {
  margin-bottom: 0.5rem;
}

.mt-3 {
  margin-top: 1rem;
}

@media (max-width: 768px) {
  .main-content {
    grid-template-columns: 1fr;
  }
  
  .fibonacci-cards {
    grid-template-columns: repeat(auto-fill, minmax(80px, 1fr));
  }
}
</style>