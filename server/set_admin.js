#!/usr/bin/env node
'use strict';
/**
 * CLI utility to create or reset an OWNER / ADMIN user.
 * Usage:
 *   node set_admin.js <username> <password> [rank]
 * Example:
 *   node set_admin.js علي 123456 OWNER
 */
const fs = require('fs');
const path = require('path');
const auth = require('./lib/auth');

const DB_FILE = process.env.DB_FILE || path.join(__dirname, 'data/db.json');
const username = process.argv[2];
const password = process.argv[3];
const rank = (process.argv[4] || 'OWNER').toUpperCase();

if (!username || !password) {
  console.log('Usage: node set_admin.js <username> <password> [rank]');
  process.exit(1);
}

if (!['OWNER', 'ADMIN', 'MODERATOR'].includes(rank)) {
  console.error('Invalid rank. Must be OWNER, ADMIN, or MODERATOR.');
  process.exit(1);
}

if (!fs.existsSync(DB_FILE)) {
  console.error('Database file not found:', DB_FILE);
  process.exit(1);
}

const state = JSON.parse(fs.readFileSync(DB_FILE, 'utf8'));
state.users = state.users || {};
state.names = state.names || {};

const key = username.trim().toLowerCase();
let user = null;

if (state.names[key]) {
  user = state.users[state.names[key]];
  console.log(`Updating existing user: ${user.name} (${user.id})`);
} else {
  const id = `u_${Date.now().toString(36)}`;
  user = {
    id,
    name: username.trim(),
    displayName: username.trim(),
    country: 'العراق',
    status: 'online',
    isMuted: false,
    isGhost: false,
    createdAt: Date.now(),
    devices: [],
    oldNames: []
  };
  state.users[id] = user;
  state.names[key] = id;
  console.log(`Created new user: ${user.name} (${user.id})`);
}

const { salt, hash } = auth.hashPassword(password);
user.salt = salt;
user.hash = hash;
user.rank = rank;

fs.writeFileSync(DB_FILE, JSON.stringify(state, null, 2), 'utf8');
console.log(`✅ Success: User "${user.name}" is now ${rank}.`);
