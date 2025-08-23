<template>
  <div 
    class="poker-card"
    :class="cardClasses"
    @click="handleClick"
  >
    <div class="card-inner">
      <div class="card-front">
        <div class="card-pattern"></div>
      </div>
      <div class="card-back">
        <div class="card-value">{{ displayValue }}</div>
      </div>
    </div>
  </div>
</template>

<script>
import { computed } from 'vue'

export default {
  name: 'PokerCard',
  props: {
    value: String,
    revealed: {
      type: Boolean,
      default: false
    },
    selected: {
      type: Boolean,
      default: false
    },
    hasVoted: {
      type: Boolean,
      default: false
    },
    clickable: {
      type: Boolean,
      default: false
    },
    small: {
      type: Boolean,
      default: false
    }
  },
  emits: ['click'],
  setup(props, { emit }) {
    const cardClasses = computed(() => ({
      'card-revealed': props.revealed && props.value,
      'card-selected': props.selected,
      'card-voted': props.hasVoted && !props.revealed,
      'card-clickable': props.clickable,
      'card-small': props.small,
      'card-empty': !props.hasVoted && !props.value
    }))
    
    const displayValue = computed(() => {
      if (!props.hasVoted && !props.value) return ''
      if (!props.revealed) return '?'
      return props.value || '?'
    })
    
    const handleClick = () => {
      if (props.clickable) {
        emit('click', props.value)
      }
    }
    
    return {
      cardClasses,
      displayValue,
      handleClick
    }
  }
}
</script>

<style scoped>
.poker-card {
  width: 100px;
  height: 140px;
  perspective: 1000px;
  cursor: default;
  user-select: none;
}

.poker-card.card-small {
  width: 50px;
  height: 70px;
}

.poker-card.card-clickable {
  cursor: pointer;
}

.card-inner {
  width: 100%;
  height: 100%;
  position: relative;
  transform-style: preserve-3d;
  transition: transform 0.6s;
}

.poker-card.card-revealed .card-inner,
.poker-card.card-selected .card-inner {
  transform: rotateY(180deg);
}

.card-front,
.card-back {
  width: 100%;
  height: 100%;
  position: absolute;
  backface-visibility: hidden;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.2);
}

.card-front {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: 2px solid white;
}

.card-pattern {
  width: 80%;
  height: 80%;
  background-image: repeating-linear-gradient(
    45deg,
    rgba(255, 255, 255, 0.1),
    rgba(255, 255, 255, 0.1) 10px,
    transparent 10px,
    transparent 20px
  );
  border-radius: 8px;
}

.card-back {
  background: white;
  border: 2px solid #dee2e6;
  transform: rotateY(180deg);
}

.card-value {
  font-size: 2.5rem;
  font-weight: bold;
  color: #495057;
}

.poker-card.card-small .card-value {
  font-size: 1.5rem;
}

.poker-card.card-selected .card-back {
  background: #e7f3ff;
  border-color: #0084ff;
}

.poker-card.card-voted .card-front {
  background: linear-gradient(135deg, #28a745 0%, #20c997 100%);
}

.poker-card.card-empty {
  opacity: 0.3;
  pointer-events: none;
}

.poker-card.card-clickable:hover .card-inner {
  transform: translateY(-5px);
}

.poker-card.card-clickable.card-selected:hover .card-inner {
  transform: rotateY(180deg) translateY(-5px);
}
</style>