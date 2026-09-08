<template>
  <view class="keyboard-mask" v-if="visible" @tap="close">
    <view class="keyboard-wrap" @tap.stop>
      <!-- 粘贴提示 -->
      <view class="paste-bar" v-if="showPaste">
        <text class="paste-tip">长按输入框可粘贴</text>
      </view>

      <!-- 车牌模式：省份简称 -->
      <template v-if="mode === 'plate' && !showLetters">
        <view class="key-row" v-for="(row, i) in provinceRows" :key="'province-'+i">
          <view class="key province" v-for="key in row" :key="key" @tap="onKey(key)">{{ key }}</view>
        </view>
        <!-- 底部操作行 -->
        <view class="key-row">
          <view class="key wide" @tap="showLetters = true">ABC</view>
          <view class="key" v-for="k in ['Z','X','C','V','B','N','M']" :key="k" @tap="onKey(k)">{{ k }}</view>
          <view class="key wide" @tap="emit('delete')">⌫</view>
        </view>
      </template>

      <!-- 车牌模式：字母数字 -->
      <template v-if="mode === 'plate' && showLetters">
        <view class="key-row" v-for="(row, i) in letterRows" :key="'letter-'+i">
          <view class="key" :class="{ wide: key === '完成' || key === '删除' || key === '省份' }"
            v-for="key in row" :key="key" @tap="onKey(key)">
            <text v-if="key === '删除'">⌫</text>
            <text v-else>{{ key }}</text>
          </view>
        </view>
      </template>

      <!-- 普通模式：QWERTY -->
      <template v-if="mode === 'normal'">
        <view class="key-row" v-for="(row, i) in normalRows" :key="'normal-'+i">
          <view class="key" :class="{ wide: key === '完成' || key === '删除' || key === '空格' }"
            v-for="key in row" :key="key" @tap="onKey(key)">
            <text v-if="key === '删除'">⌫</text>
            <text v-else-if="key === '空格'">空格</text>
            <text v-else>{{ key }}</text>
          </view>
        </view>
      </template>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  mode: { type: String, default: 'plate' }, // plate | normal
  showPaste: { type: Boolean, default: true }
})

const emit = defineEmits(['close', 'input', 'delete', 'paste'])

// 省份简称 - 每行6个
const provinceRows = [
  ['京', '津', '沪', '渝', '冀', '豫'],
  ['云', '辽', '黑', '湘', '皖', '鲁'],
  ['新', '苏', '浙', '赣', '鄂', '桂'],
  ['甘', '晋', '蒙', '陕', '吉', '闽'],
  ['贵', '粤', '川', '青', '藏', '琼'],
  ['宁', '使', '领', '警', '学', '港', '澳']
]

// 车牌模式字母行（显示字母数字时使用）
const showLetters = ref(false)
const letterRows = [
  ['1', '2', '3', '4', '5', '6', '7', '8', '9', '0'],
  ['Q', 'W', 'E', 'R', 'T', 'Y', 'U', 'I', 'O', 'P'],
  ['A', 'S', 'D', 'F', 'G', 'H', 'J', 'K', 'L'],
  ['省份', 'Z', 'X', 'C', 'V', 'B', 'N', 'M', '删除']
]

// 普通模式行
const normalRows = [
  ['1', '2', '3', '4', '5', '6', '7', '8', '9', '0'],
  ['Q', 'W', 'E', 'R', 'T', 'Y', 'U', 'I', 'O', 'P'],
  ['A', 'S', 'D', 'F', 'G', 'H', 'J', 'K', 'L'],
  ['完成', 'Z', 'X', 'C', 'V', 'B', 'N', 'M', '删除']
]

function onKey(key) {
  if (key === '删除') {
    emit('delete')
  } else if (key === '完成') {
    close()
  } else if (key === 'ABC') {
    showLetters.value = true
  } else if (key === '省份') {
    showLetters.value = false
  } else if (key === '空格') {
    emit('input', ' ')
  } else {
    emit('input', key)
  }
}

function close() {
  showLetters.value = false
  emit('close')
}
</script>

<style lang="scss" scoped>
.keyboard-mask {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  top: 0;
  z-index: 999;
}

.keyboard-wrap {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  background: #e8ecf0;
  padding: 12rpx 8rpx calc(12rpx + env(safe-area-inset-bottom));
  z-index: 1000;
  animation: slideUp 0.2s ease;
}

@keyframes slideUp {
  from { transform: translateY(100%); }
  to { transform: translateY(0); }
}

.paste-bar {
  display: flex;
  justify-content: flex-end;
  padding: 8rpx 16rpx;
}

.paste-tip {
  font-size: 22rpx;
  color: #8b96a7;
}

.key-row {
  display: flex;
  justify-content: center;
  gap: 8rpx;
  margin-bottom: 10rpx;
}

.key {
  height: 84rpx;
  min-width: 62rpx;
  padding: 0 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  border-radius: 12rpx;
  font-size: 28rpx;
  font-weight: 500;
  color: #223049;
  box-shadow: 0 2rpx 4rpx rgba(0, 0, 0, 0.06);

  &:active {
    background: #d1d5db;
  }

  &.wide {
    min-width: 100rpx;
    font-size: 24rpx;
    background: #adb4be;
    color: #fff;

    &:active {
      background: #9ca3af;
    }
  }
}

/* 省份简称键样式 */
.key.province {
  min-width: 80rpx;
  height: 80rpx;
  font-size: 26rpx;
}
</style>
