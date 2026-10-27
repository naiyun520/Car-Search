<template>
  <view class="plate-input-wrap">
    <!-- 车牌类型选择 -->
    <view class="plate-type-tabs">
      <view class="tab" :class="{ active: plateType === 'oil' }" @tap="switchType('oil')">普通车牌</view>
      <view class="tab" :class="{ active: plateType === 'ev' }" @tap="switchType('ev')">新能源车牌</view>
    </view>

    <!-- 格子输入框 -->
    <view class="plate-cells" @tap="onTapCells" @longpress="showPasteBtn = true">
      <view class="cell" v-for="(char, i) in cells" :key="i"
        :class="{ active: activeIndex === i, 'is-dot': i === 1 }">
        <text>{{ char }}</text>
      </view>
    </view>

    <!-- 粘贴按钮 -->
    <view class="paste-btn" v-if="showPasteBtn" @tap="onPaste">
      <text>粘贴</text>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  maxLength: { type: Number, default: 8 }
})

const emit = defineEmits(['update:modelValue', 'focus'])

// 车牌类型：oil=油车7位, ev=新能源8位
const plateType = ref('oil')
const cellCount = computed(() => plateType.value === 'ev' ? 8 : 7)
const activeIndex = ref(0)
const showPasteBtn = ref(false)

// 格子内容
const cells = computed(() => {
  const val = props.modelValue || ''
  const arr = []
  for (let i = 0; i < cellCount.value; i++) {
    arr.push(val[i] || '')
  }
  return arr
})

// 切换车牌类型
function switchType(type) {
  if (plateType.value === type) return
  plateType.value = type
  const val = props.modelValue || ''
  if (val.length > cellCount.value) {
    emit('update:modelValue', val.slice(0, cellCount.value))
  }
  activeIndex.value = Math.min(activeIndex.value, cellCount.value - 1)
}

// 点击格子区域，触发键盘
function onTapCells() {
  showPasteBtn.value = false
  const val = props.modelValue || ''
  activeIndex.value = Math.min(val.length, cellCount.value - 1)
  emit('focus')
}

// 粘贴功能
async function onPaste() {
  showPasteBtn.value = false
  try {
    const res = await new Promise((resolve, reject) => {
      uni.getClipboardData({ success: resolve, fail: reject })
    })
    if (res.data) {
      // 清理粘贴内容：去除空格、特殊字符，转大写
      const cleaned = String(res.data).replace(/[\s\-\_\.]/g, '').toUpperCase().slice(0, cellCount.value)
      emit('update:modelValue', cleaned)
      activeIndex.value = Math.min(cleaned.length, cellCount.value - 1)
      uni.showToast({ title: '已粘贴', icon: 'success', duration: 800 })
    }
  } catch (e) {
    // 忽略粘贴失败
  }
}

// 更新输入值（由父组件调用）
function addChar(char) {
  const val = props.modelValue || ''
  if (val.length >= cellCount.value) return
  const newVal = val + char.toUpperCase()
  emit('update:modelValue', newVal)
  activeIndex.value = Math.min(newVal.length, cellCount.value - 1)
}

function deleteChar() {
  const val = props.modelValue || ''
  if (val.length === 0) return
  const newVal = val.slice(0, -1)
  emit('update:modelValue', newVal)
  activeIndex.value = Math.min(newVal.length, cellCount.value - 1)
}

defineExpose({ addChar, deleteChar, cellCount })
</script>

<style lang="scss" scoped>
.plate-input-wrap {
  padding: 20rpx 0;
  position: relative;
}

.plate-type-tabs {
  display: flex;
  gap: 16rpx;
  margin-bottom: 24rpx;

  .tab {
    flex: 1;
    height: 72rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 24rpx;
    font-size: 24rpx;
    font-weight: 500;
    background: #F0F2F5;
    color: #6B7280;
    transition: all 0.2s;

    &.active {
      background: #E0EEFF;
      color: #2E7DFF;
      font-weight: 600;
    }
  }
}

.plate-cells {
  display: flex;
  justify-content: center;
  gap: 12rpx;

  .cell {
    width: 80rpx;
    height: 96rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 16rpx;
    border: 2rpx solid #E5E7EB;
    background: #fff;
    font-size: 36rpx;
    font-weight: 700;
    color: #1F2937;
    transition: all 0.2s;
    position: relative;

    &.active {
      border-color: #2E7DFF;
      box-shadow: 0 0 0 4rpx rgba(46, 125, 255, 0.15);
    }

    &.is-dot::after {
      content: '·';
      position: absolute;
      right: -20rpx;
      top: 50%;
      transform: translateY(-50%);
      font-size: 32rpx;
      color: #9CA3AF;
    }
  }
}

.paste-btn {
  position: absolute;
  right: 0;
  top: 100rpx;
  padding: 16rpx 32rpx;
  background: #2E7DFF;
  color: #fff;
  border-radius: 24rpx;
  font-size: 26rpx;
  font-weight: 500;
  box-shadow: 0 8rpx 20rpx rgba(46, 125, 255, 0.28);
  z-index: 10;
}
</style>
