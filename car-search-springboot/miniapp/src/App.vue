<script>
export default {
}
</script>

<style lang="scss">
/* ============ 车档查 · 全局设计规范 ============
   主色   --brand:    #2E7DFF  行动蓝
          --brand-2:  #E0EEFF  浅蓝底
   辅助   --ok: #10B981  --warn: #F59E0B  --bad: #EF4444
   文字   --ink: #1F2937  --gray: #6B7280  --muted: #9CA3AF
   线条   --line: #E5E7EB   底色 --bg: #F5F7FA
   圆角   12px = 24rpx   字体 PingFang SC
*/
page {
  background: #F5F7FA;
  color: #1F2937;
  font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", "Helvetica Neue", sans-serif;
  font-size: 28rpx;
  line-height: 1.6;
}
view, text, input, textarea, button, image { box-sizing: border-box; }
button::after { border: 0; }
.page { min-height: 100vh; }

/* 卡片基座：轻边框 + 柔和投影，圆角 12px */
.card {
  background: #ffffff;
  border-radius: 24rpx;
  box-shadow: 0 8rpx 28rpx rgba(31, 41, 55, 0.06);
  border: 1rpx solid #EEF1F5;
}

/* 主行动按钮 */
.primary-btn {
  height: 92rpx;
  line-height: 92rpx;
  border-radius: 24rpx;
  color: #ffffff;
  background: #2E7DFF;
  font-size: 30rpx;
  font-weight: 600;
  letter-spacing: 1rpx;
  box-shadow: 0 10rpx 24rpx rgba(46, 125, 255, 0.28);
}
.primary-btn[disabled] { opacity: 0.55; box-shadow: none; }

/* 次级按钮：白底蓝字 */
.ghost-btn {
  height: 92rpx;
  line-height: 92rpx;
  border-radius: 24rpx;
  color: #2E7DFF;
  background: #ffffff;
  border: 1rpx solid #BBD6FF;
  font-size: 30rpx;
  font-weight: 600;
}

/* 空状态 */
.empty { padding: 120rpx 30rpx; text-align: center; color: #9CA3AF; }

/* 页面公共头部（非自定义导航页面） */
.page-head-bar {
  padding: 32rpx 32rpx 20rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.page-head-bar .bar-title { font-size: 40rpx; font-weight: 700; color: #1F2937; letter-spacing: 1rpx; }
.page-head-bar .bar-sub { display: block; margin-top: 8rpx; font-size: 24rpx; color: #9CA3AF; font-weight: 400; }
</style>
