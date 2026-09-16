'use strict';
const crypto = require('crypto');

function hashPassword(password, salt = crypto.randomBytes(16).toString('hex')) {
  const hash = crypto.scryptSync(password, salt, 64).toString('hex');
  return { salt, hash };
}

function verifyPassword(password, salt, hash) {
  if (!salt || !hash) return false;
  const candidate = crypto.scryptSync(password, salt, 64);
  const stored = Buffer.from(hash, 'hex');
  return stored.length === candidate.length && crypto.timingSafeEqual(stored, candidate);
}

function newToken() {
  return crypto.randomBytes(24).toString('hex');
}

/** Names: 2-50 characters. Decorative unicode (like the site's 𓆩𝐒𓆪 names) is allowed. */
function validName(name) {
  if (typeof name !== 'string') return false;
  const trimmed = name.trim();
  const length = [...trimmed].length;
  if (length < 2 || length > 50) return false;
  for (const ch of trimmed) {
    const code = ch.codePointAt(0);
    if (code < 32 || ch === '<' || ch === '>') return false;
  }
  return true;
}

module.exports = { hashPassword, verifyPassword, newToken, validName };
