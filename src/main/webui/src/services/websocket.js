export class WebSocketService {
  constructor(roomId) {
    this.roomId = roomId
    this.ws = null
    this.messageHandlers = []
    this.sessionId = null
  }
  
  connect() {
    return new Promise((resolve, reject) => {
      const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
      const host = window.location.host
      const wsUrl = `${protocol}//${host}/ws/room/${this.roomId}`
      
      this.ws = new WebSocket(wsUrl)
      
      this.ws.onopen = () => {
        console.log('WebSocket connected')
        this.sessionId = this.generateSessionId()
        resolve()
      }
      
      this.ws.onmessage = (event) => {
        try {
          const message = JSON.parse(event.data)
          this.messageHandlers.forEach(handler => handler(message))
        } catch (error) {
          console.error('Failed to parse WebSocket message:', error)
        }
      }
      
      this.ws.onerror = (error) => {
        console.error('WebSocket error:', error)
        reject(error)
      }
      
      this.ws.onclose = () => {
        console.log('WebSocket disconnected')
      }
    })
  }
  
  disconnect() {
    if (this.ws) {
      this.ws.close()
      this.ws = null
    }
  }
  
  send(message) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(message))
    } else {
      console.error('WebSocket is not connected')
    }
  }
  
  onMessage(handler) {
    this.messageHandlers.push(handler)
  }
  
  generateSessionId() {
    return Math.random().toString(36).substr(2, 9)
  }
}