import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

// Load this standalone module without changing the UniApp package module mode.
const source = await readFile(new URL('../src/utils/update-manager.js', import.meta.url), 'utf8')
const { createUpdatePrompt } = await import('data:text/javascript;base64,' + Buffer.from(source).toString('base64'))

function fixture() {
  const callbacks = {}, modals = [], toasts = []
  let applies = 0, registrations = 0
  const manager = {
    onUpdateReady(fn) { callbacks.ready = fn; registrations++ },
    onUpdateFailed(fn) { callbacks.failed = fn },
    applyUpdate() { applies++ }
  }
  const api = { getUpdateManager: () => manager, showModal: options => modals.push(options), showToast: options => toasts.push(options) }
  const controller = createUpdatePrompt(api)
  return { callbacks, modals, toasts, manager, api, controller, get applies() { return applies }, get registrations() { return registrations } }
}

test('registers once and never restarts before package is ready', () => {
  const f = fixture()
  f.controller.start(); f.controller.start(); f.controller.onShow()
  assert.equal(f.registrations, 1)
  assert.equal(f.modals.length, 0)
  assert.equal(f.applies, 0)
})

test('ready package presents a single honest restart button', () => {
  const f = fixture(); f.controller.start(); f.controller.onShow(); f.callbacks.ready()
  assert.equal(f.modals.length, 1)
  assert.equal(f.modals[0].showCancel, false)
  assert.equal(f.modals[0].confirmText, '重启更新')
  assert.equal(f.applies, 0)
  f.modals[0].success({ confirm: true }); f.modals[0].complete()
  f.callbacks.ready(); f.controller.onShow()
  assert.equal(f.applies, 1)
  assert.equal(f.modals.length, 1)
})

test('background download waits until foreground to show modal', () => {
  const f = fixture(); f.controller.start(); f.controller.onShow(); f.controller.onHide(); f.callbacks.ready()
  assert.equal(f.modals.length, 0)
  f.controller.onShow(); assert.equal(f.modals.length, 1)
})

test('repeated ready and foreground events do not stack dialogs', () => {
  const f = fixture(); f.controller.start(); f.callbacks.ready(); f.controller.onShow(); f.controller.onShow(); f.callbacks.ready()
  assert.equal(f.modals.length, 1)
})

test('download failure never forces a restart or clears application storage', () => {
  const f = fixture(); f.controller.start(); f.callbacks.failed(); f.controller.onShow()
  assert.equal(f.modals.length, 0); assert.equal(f.applies, 0)
})

test('unsupported API and initialization exceptions leave startup usable', () => {
  assert.equal(createUpdatePrompt({}).start(), false)
  assert.equal(createUpdatePrompt({ getUpdateManager() { throw Error('unsupported') }, showModal() {} }).start(), false)
})

test('unexpected dismissal does not pretend cancel means restart', () => {
  const f = fixture(); f.controller.start(); f.controller.onShow(); f.callbacks.ready()
  f.modals[0].success({ confirm: false, cancel: true }); f.modals[0].complete()
  assert.equal(f.applies, 0)
  f.controller.onHide(); f.controller.onShow()
  assert.equal(f.modals.length, 2)
})

test('modal invocation failure can retry on later foreground without a loop', () => {
  const f = fixture(); f.api.showModal = () => { throw Error('not available') }
  f.controller.start(); f.controller.onShow(); f.callbacks.ready()
  assert.equal(f.applies, 0)
  f.api.showModal = options => f.modals.push(options)
  f.controller.onShow(); assert.equal(f.modals.length, 1)
})

test('apply exception is contained and a subsequent foreground can retry', () => {
  const f = fixture(); f.manager.applyUpdate = () => { throw Error('apply failed') }
  f.controller.start(); f.controller.onShow(); f.callbacks.ready()
  f.modals[0].success({ confirm: true }); f.modals[0].complete()
  assert.equal(f.toasts.length, 1)
  f.controller.onShow(); assert.equal(f.modals.length, 2)
})
