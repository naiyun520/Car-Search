import assert from 'node:assert/strict'
import { readFileSync, readdirSync } from 'node:fs'

const read = path => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8')
const javaRoot = 'car-search-springboot/backend/src/main/java/cn/naiblog/carsearch'
const controllers = readdirSync(new URL(`../${javaRoot}/controller`, import.meta.url))
  .filter(name => name.endsWith('.java'))
  .map(name => read(`${javaRoot}/controller/${name}`))
  .join('\n')

const requiredRoutes = [
  '/bootstrap', '/service-icon/{file}', '/auth/login', '/me', '/me/profile',
  '/api/v1/checkout', '/create', '/status', '/payment', '/confirm',
  '/query', '/recoverable', '/feedback', '/feedback-detail/{id}',
  '/dashboard', '/dashboard-finance', '/users', '/orders', '/wechat-messages',
  '/order-detail/{orderNo}', '/services', '/service-test/{id}', '/service-icon-upload/{id}',
  '/announcements', '/settings', '/payment-settings', '/email-settings', '/email-test',
  '/password', '/wechat/message'
]
for (const route of requiredRoutes) assert.ok(controllers.includes(`"${route}"`), `Spring route missing: ${route}`)

const apiResponse = read(`${javaRoot}/api/ApiResponse.java`)
for (const field of ['code', 'message', 'data']) assert.ok(apiResponse.includes(field), `response field missing: ${field}`)

const webConfig = read(`${javaRoot}/config/WebConfig.java`)
assert.ok(webConfig.includes('"/api/v1/**"') && webConfig.includes('"/admin-api/**"'), 'user/admin authentication boundaries missing')
assert.ok(webConfig.includes('"/api/v1/auth/login"'), 'public login exclusion missing')

const checkout = read(`${javaRoot}/service/CheckoutService.java`)
assert.ok(checkout.includes('request_key') && checkout.includes('user_id'), 'checkout idempotency lookup missing')
assert.ok(checkout.includes('service_snapshot_cipher'), 'service snapshot freeze missing')
const queryRunner = read(`${javaRoot}/service/QueryRunnerService.java`)
assert.ok(queryRunner.includes("status='querying'") && queryRunner.includes("status='paid'"), 'atomic paid-to-querying claim missing')

const reconciliation = read(`${javaRoot}/service/PaymentReconciliationService.java`)
assert.ok(reconciliation.includes('wechat.queryOrder'), 'server-side payment verification missing')
assert.ok(reconciliation.includes('payment_review'), 'payment mismatch review state missing')
assert.ok(reconciliation.includes('paid_fee') && reconciliation.includes('env_type') && reconciliation.includes('order_type'), 'payment amount/environment/type checks missing')

const refundEvents = read(`${javaRoot}/wechat/WechatRefundEventService.java`)
for (const event of ['xpay_subscribe_ios_refund_query_notify', 'xpay_refund_notify', 'xpay_goods_deliver_notify']) {
  assert.ok(refundEvents.includes(event), `WeChat event missing: ${event}`)
}
assert.ok(refundEvents.includes('refund_status=\'apple_review\''), 'Apple refund review transition missing')
assert.ok(refundEvents.includes("status='refunded'"), 'final refund transition missing')

const scheduler = read(`${javaRoot}/service/MaintenanceScheduler.java`)
assert.ok(scheduler.includes('@Scheduled') && scheduler.includes('reconcilePending'), 'payment reconciliation schedule missing')
assert.ok(scheduler.includes('privacy_retention_days'), 'privacy retention cleanup missing')
assert.ok(scheduler.includes('email_notify_daily'), 'daily email schedule missing')

const databaseVerifier = read(`${javaRoot}/config/DatabaseCompatibilityVerifier.java`)
for (const table of ['ci_admin', 'ci_user', 'ci_service', 'ci_order', 'ci_payment', 'ci_feedback', 'ci_wechat_event']) {
  assert.ok(databaseVerifier.includes(`"${table}"`), `database startup check missing: ${table}`)
}

const oldAdmin = new URL('../car-admin/public/admin/', import.meta.url)
const newAdmin = new URL('../car-search-springboot/backend/src/main/resources/static/admin/', import.meta.url)
for (const name of readdirSync(oldAdmin).filter(name => !name.startsWith('.'))) {
  const oldContent = readFileSync(new URL(name, oldAdmin), 'utf8').trimEnd()
  const newContent = readFileSync(new URL(name, newAdmin), 'utf8').trimEnd()
  assert.equal(newContent, oldContent, `admin static asset differs after migration: ${name}`)
}

const oldMiniappFiles = [
  'src/App.vue', 'src/main.js', 'src/manifest.json', 'src/pages.json',
  'src/utils/auth.js', 'src/utils/loading.js', 'src/utils/payment.js', 'src/utils/protocol.js', 'src/utils/request.js',
  'src/components/plate-input/plate-input.vue', 'src/components/plate-keyboard/plate-keyboard.vue',
  ...['agreement', 'feedback', 'index', 'my', 'orders', 'query', 'result'].map(page => `src/pages/${page}/${page}.vue`)
]
for (const name of oldMiniappFiles) {
  assert.equal(read(`car-search-springboot/miniapp/${name}`).trimEnd(), read(`car-miniapp/${name}`).trimEnd(), `miniapp behavior differs after migration: ${name}`)
}

console.log('Spring Boot migration contract OK')
