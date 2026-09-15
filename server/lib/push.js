'use strict';
/**
 * Firebase Cloud Messaging sender (Requirement 5: push notifications).
 *
 * Dormant until FCM_SERVICE_ACCOUNT points at a service-account JSON file
 * (downloaded from the Firebase console). Without it, send() is a no-op so the
 * server runs fine in development and on a VPS that has not set up Firebase yet.
 */
let messaging = null;

try {
  const saPath = process.env.FCM_SERVICE_ACCOUNT;
  if (saPath) {
    const admin = require('firebase-admin');
    const serviceAccount = require(require('path').resolve(saPath));
    admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
    messaging = admin.messaging();
    console.log('[push] FCM enabled');
  } else {
    console.log('[push] FCM disabled (set FCM_SERVICE_ACCOUNT to enable)');
  }
} catch (e) {
  console.error('[push] FCM init failed, notifications disabled:', e.message);
}

function enabled() { return !!messaging; }

/**
 * Send a notification to device tokens. Returns invalid tokens so the caller
 * can prune them from the store.
 */
async function send(tokens, title, body, data = {}) {
  if (!messaging || !tokens || tokens.length === 0) return { invalid: [] };
  const invalid = [];
  try {
    const res = await messaging.sendEachForMulticast({
      tokens,
      notification: { title, body },
      data: Object.fromEntries(Object.entries(data).map(([k, v]) => [k, String(v)])),
      android: { priority: 'high', notification: { channelId: 'chat', sound: 'default' } }
    });
    res.responses.forEach((r, i) => {
      if (!r.success) {
        const code = r.error && r.error.code;
        if (code === 'messaging/registration-token-not-registered' || code === 'messaging/invalid-argument') {
          invalid.push(tokens[i]);
        }
      }
    });
  } catch (e) {
    console.error('[push] send failed:', e.message);
  }
  return { invalid };
}

module.exports = { enabled, send };
