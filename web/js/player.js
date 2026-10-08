import { resolveStream } from './providers.js';

const media = () => document.getElementById('media');

export const player = {
  queue: [],
  index: -1,
  shuffle: false,
  repeat: 'off',
  playing: false,
  loading: false,
  objectUrls: [],
  _listeners: {},
  _sessionBound: false,
  _consecErrors: 0,

  on(evt, fn) { (this._listeners[evt] = this._listeners[evt] || []).push(fn); },
  emit(evt, data) { for (const fn of (this._listeners[evt] || [])) fn(data); },

  current() { return this.index >= 0 && this.index < this.queue.length ? this.queue[this.index] : null; },

  async playTracks(tracks, startIndex = 0) {
    if (!tracks || !tracks.length) return;
    this.queue = tracks.slice();
    this.index = Math.max(0, Math.min(startIndex, this.queue.length - 1));
    await this._loadCurrent(true);
  },

  async enqueue(tracks) {
    if (!tracks || !tracks.length) return;
    const wasEmpty = this.queue.length === 0;
    this.queue = this.queue.concat(tracks);
    this.emit('queue');
    if (wasEmpty) { this.index = 0; await this._loadCurrent(true); }
  },

  playNextInQueue() {
    if (this.index < this.queue.length - 1) { this.index++; this._loadCurrent(true); }
  },

  async _loadCurrent(userPlay) {
    const track = this.current();
    if (!track) return;
    this.loading = true;
    this.emit('state');
    this.emit('track');
    const m = media();
    try {
      for (const u of this.objectUrls) URL.revokeObjectURL(u);
      this.objectUrls = [];
      const src = await resolveStream(track);
      if (this.current() !== track) return;
      m.src = src;
      if (src.startsWith('blob:')) this.objectUrls.push(src);
      m.load();
      await m.play();
      this.playing = true;
      this._consecErrors = 0;
      this._touchHistory(track);
      this._bindSession(track);
    } catch (e) {
      this.playing = false;
      if (e && e.name === 'AbortError') { this.loading = false; this.emit('state'); return; }
      this.emit('error', { track, error: e });
      if (this.queue.length > 1 && this._consecErrors < 3) {
        this._consecErrors++;
        setTimeout(() => this._advance(true), 700);
      }
    }
    this.loading = false;
    this.emit('state');
  },

  async toggle() {
    const m = media();
    if (!this.current()) return;
    if (m.paused) {
      try { await m.play(); this.playing = true; } catch (e) { this.emit('error', { error: e }); }
    } else {
      m.pause();
      this.playing = false;
    }
    this.emit('state');
  },

  async next(auto = false) {
    if (!this.queue.length) return;
    if (this.repeat === 'one' && auto) {
      const m = media();
      m.currentTime = 0;
      try { await m.play(); } catch { /* ignore */ }
      return;
    }
    if (this.shuffle && this.queue.length > 1) {
      let n;
      do { n = Math.floor(Math.random() * this.queue.length); } while (n === this.index && this.queue.length > 1);
      this.index = n;
    } else if (this.index < this.queue.length - 1) {
      this.index++;
    } else if (this.repeat === 'all') {
      this.index = 0;
    } else {
      this.playing = false;
      this.emit('state');
      return;
    }
    await this._loadCurrent(true);
  },

  async prev() {
    const m = media();
    if (m.currentTime > 3) { m.currentTime = 0; return; }
    if (this.index > 0) this.index--;
    await this._loadCurrent(true);
  },

  async _advance(auto) {
    if (this.shuffle && this.queue.length > 1) {
      let n;
      do { n = Math.floor(Math.random() * this.queue.length); } while (n === this.index && this.queue.length > 1);
      this.index = n;
    } else if (this.index < this.queue.length - 1) this.index++;
    else if (this.repeat === 'all') this.index = 0;
    else { this.playing = false; this.emit('state'); return; }
    await this._loadCurrent(true);
  },

  seek(sec) {
    const m = media();
    if (isFinite(sec)) m.currentTime = Math.max(0, Math.min(sec, m.duration || sec));
  },

  toggleShuffle() {
    this.shuffle = !this.shuffle;
    this.emit('state');
    return this.shuffle;
  },

  cycleRepeat() {
    this.repeat = this.repeat === 'off' ? 'all' : this.repeat === 'all' ? 'one' : 'off';
    this.emit('state');
    return this.repeat;
  },

  _touchHistory(track) {
    track.lastPlayedAt = Date.now();
    track.playCount = (track.playCount || 0) + 1;
    this.emit('history', track);
  },

  artworkFor(track) {
    if (!track) return null;
    if (track.artBlobUrl) return track.artBlobUrl;
    if (track.artwork) return track.artwork;
    return null;
  },

  _bindSession(track) {
    if (!('mediaSession' in navigator) || !track) return;
    try {
      navigator.mediaSession.metadata = new MediaMetadata({
        title: track.title,
        artist: track.artist || 'CS music',
        album: track.album || '',
        artwork: this.artworkFor(track) ? [
          { src: this.artworkFor(track), sizes: '512x512', type: 'image/jpeg' },
          { src: this.artworkFor(track), sizes: '256x256', type: 'image/jpeg' }
        ] : []
      });
      if (!this._sessionBound) {
        this._sessionBound = true;
        const set = (action, fn) => {
          try { navigator.mediaSession.setActionHandler(action, fn); } catch { /* unsupported */ }
        };
        set('play', () => this.toggle());
        set('pause', () => this.toggle());
        set('previoustrack', () => this.prev());
        set('nexttrack', () => this.next());
        set('seekbackward', d => this.seek(media().currentTime - ((d && d.seekOffset) || 10)));
        set('seekforward', d => this.seek(media().currentTime + ((d && d.seekOffset) || 10)));
        set('seekto', d => { if (d && d.seekTime != null) this.seek(d.seekTime); });
      }
      navigator.mediaSession.playbackState = 'playing';
    } catch { /* ignore */ }
  },

  bindMediaEvents() {
    const m = media();
    m.addEventListener('play', () => { this.playing = true; this.emit('state'); if ('mediaSession' in navigator) navigator.mediaSession.playbackState = 'playing'; });
    m.addEventListener('pause', () => { this.playing = false; this.emit('state'); if ('mediaSession' in navigator) navigator.mediaSession.playbackState = 'paused'; });
    m.addEventListener('timeupdate', () => this.emit('time'));
    m.addEventListener('durationchange', () => this.emit('time'));
    m.addEventListener('ended', () => this.next(true));
    m.addEventListener('error', () => {
      if (!m.src) return;
      this.emit('mediaerror');
      if (this.queue.length > 1 && this._consecErrors < 3) {
        this._consecErrors++;
        setTimeout(() => this._advance(true), 700);
      } else {
        this.playing = false;
        this.emit('state');
      }
    });
    m.addEventListener('waiting', () => { this.loading = true; this.emit('state'); });
    m.addEventListener('canplay', () => { this.loading = false; this.emit('state'); });
  }
};
