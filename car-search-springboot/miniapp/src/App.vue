<script>
export default {
}
</script>

<style lang="scss">
/* ============ 全局设计规范：品牌蓝 · 简洁专业 ============
   主色   --brand:    #175cd3  品牌蓝
   强调   --accent:   #126bee  行动蓝
   文字   --ink:      #18243a  主文字
         --sub:      #6b7a90  次级文字
   线条   --line:     #e8edf5
   底色   --bg:       #f4f7fb
   成功   --ok:       #06a567  警示 --warn: #9b6300  失败 --bad: #d84843
*/
page {
  background: #f4f7fb;
  color: #18243a;
  font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", "Helvetica Neue", sans-serif;
  font-size: 28rpx;
  line-height: 1.6;
}
view, text, input, textarea, button, image { box-sizing: border-box; }
button::after { border: 0; }
.page { min-height: 100vh; }

/* 卡片基座 */
.card {
  background: #ffffff;
  border: 1rpx solid #e8edf5;
  border-radius: 24rpx;
  box-shadow: 0 10rpx 30rpx rgba(25, 74, 139, 0.06);
}

/* 主行动按钮 */
.primary-btn {
  height: 92rpx;
  line-height: 92rpx;
  border-radius: 16rpx;
  color: #ffffff;
  background: linear-gradient(135deg, #175cd3, #2f80e3);
  font-size: 30rpx;
  font-weight: 600;
  letter-spacing: 1rpx;
}
.primary-btn[disabled] { opacity: 0.55; }

/* 次级按钮 */
.ghost-btn {
  height: 92rpx;
  line-height: 92rpx;
  border-radius: 16rpx;
  color: #175cd3;
  background: #ffffff;
  border: 1rpx solid #b9cdf0;
  font-size: 30rpx;
  font-weight: 600;
}

/* 空状态 */
.empty { padding: 120rpx 30rpx; text-align: center; color: #8995a8; }

/* 页面公共头部（非自定义导航页面） */
.page-head-bar {
  padding: 30rpx 28rpx 18rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.page-head-bar .bar-title { font-size: 38rpx; font-weight: 800; color: #18243a; }
.page-head-bar .bar-sub { display: block; margin-top: 6rpx; font-size: 22rpx; color: #8995a8; font-weight: 400; }
</style>
