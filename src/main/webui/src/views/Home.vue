<template>
  <div class="home-container">
    <Card class="welcome-card">
      <template #header>
        <div class="header-content">
          <i class="pi pi-chart-line header-icon"></i>
          <h1>Planning Poker</h1>
        </div>
      </template>
      <template #content>
        <div class="form-container">
          <div class="p-field">
            <label for="roomName">Room Name</label>
            <InputText 
              id="roomName" 
              v-model="roomName" 
              placeholder="Enter room name"
              class="w-full"
            />
          </div>
          <Button 
            label="Create Room" 
            icon="pi pi-plus" 
            @click="createRoom"
            :disabled="!roomName"
            class="w-full"
          />
          
          <Divider align="center">
            <span class="divider-text">OR</span>
          </Divider>
          
          <div class="p-field">
            <label for="roomId">Room ID</label>
            <InputText 
              id="roomId" 
              v-model="roomId" 
              placeholder="Enter room ID to join"
              class="w-full"
            />
          </div>
          <Button 
            label="Join Room" 
            icon="pi pi-sign-in" 
            @click="joinRoom"
            :disabled="!roomId"
            class="w-full"
            severity="secondary"
          />
        </div>
      </template>
    </Card>
  </div>
</template>

<script>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import Card from 'primevue/card'
import InputText from 'primevue/inputtext'
import Button from 'primevue/button'
import Divider from 'primevue/divider'

export default {
  name: 'Home',
  components: {
    Card,
    InputText,
    Button,
    Divider
  },
  setup() {
    const router = useRouter()
    const roomName = ref('')
    const roomId = ref('')
    
    const createRoom = async () => {
      try {
        const response = await fetch('/api/v1/rooms', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({ title: roomName.value })
        })
        
        if (response.ok) {
          const data = await response.json()
          router.push(`/room/${data.roomId}`)
        }
      } catch (error) {
        console.error('Failed to create room:', error)
      }
    }
    
    const joinRoom = () => {
      router.push(`/room/${roomId.value}`)
    }
    
    return {
      roomName,
      roomId,
      createRoom,
      joinRoom
    }
  }
}
</script>

<style scoped>
.home-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  padding: 20px;
}

.welcome-card {
  width: 100%;
  max-width: 500px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.2);
}

.header-content {
  text-align: center;
  padding: 2rem 1rem 1rem;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.header-icon {
  font-size: 3rem;
  margin-bottom: 1rem;
  display: block;
}

.header-content h1 {
  margin: 0;
  font-size: 2rem;
  font-weight: 600;
}

.form-container {
  padding: 1rem;
}

.p-field {
  margin-bottom: 1.5rem;
}

.p-field label {
  display: block;
  margin-bottom: 0.5rem;
  font-weight: 500;
  color: #495057;
}

.w-full {
  width: 100%;
}

.divider-text {
  background: white;
  padding: 0 1rem;
  color: #6c757d;
}
</style>