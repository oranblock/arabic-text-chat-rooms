'use strict';
/**
 * Server-side content filter (Requirement 6): links and phone numbers are rejected,
 * swear words are masked. YouTube links stay allowed for the in-chat player.
 */
const LINK = /(https?:\/\/|www\.|\b[a-z0-9-]+\.(com|net|org|io|me|top|xyz|info|site|online|link)\b)/i;
const PHONE = /(\+?964|00964|\b07)[\s-]?\d[\d\s-]{7,}/;
const YOUTUBE = /(youtube\.com|youtu\.be)/i;

const BAD_WORDS = ['كلب', 'حمار', 'زباله', 'زبالة', 'قحب', 'منيوك', 'تفو', 'حيوان'];

function escapeRegex(s) {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}
const BAD = new RegExp(BAD_WORDS.map(escapeRegex).join('|'), 'g');

function sanitizeHtml(str) {
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
    .replace(/[\u200B-\u200D\uFEFF\u0000-\u0008\u000B\u000C\u000E-\u001F]/g, '');
}

function check(text) {
  if (typeof text !== 'string') return { ok: false, reason: 'رسالة غير صالحة' };
  if (text.length > 1000) return { ok: false, reason: 'الرسالة طويلة جداً (الحد الأقصى 500 حرف)' };
  const t = sanitizeHtml(text.trim()).slice(0, 500);
  if (!t) return { ok: false, reason: 'رسالة فارغة' };
  if (LINK.test(t) && !YOUTUBE.test(t)) return { ok: false, reason: 'الروابط ممنوعة في الشات' };
  if (PHONE.test(t)) return { ok: false, reason: 'أرقام الهواتف ممنوعة' };
  return { ok: true, text: t.replace(BAD, m => '*'.repeat([...m].length)) };
}

function youtubeId(text) {
  const m = /(?:youtu\.be\/|v=|shorts\/)([A-Za-z0-9_-]{11})/.exec(text || '');
  return m ? m[1] : null;
}

module.exports = { check, youtubeId };
