const TEXT_FRAMES_V23 = { TIT2: 'title', TPE1: 'artist', TALB: 'album', TYER: 'year', TDRC: 'year', TLEN: 'length' };
const TEXT_FRAMES_V22 = { TT2: 'title', TP1: 'artist', TAL: 'album', TYE: 'year' };

function readSynchsafe(b, o) {
  return ((b[o] & 0x7f) << 21) | ((b[o + 1] & 0x7f) << 14) | ((b[o + 2] & 0x7f) << 7) | (b[o + 3] & 0x7f);
}

function decodeText(bytes, enc) {
  if (!bytes.length) return '';
  try {
    if (enc === 0) return new TextDecoder('windows-1252').decode(bytes).replace(/\0+$/, '');
    if (enc === 1) {
      if (bytes[0] === 0xff && bytes[1] === 0xfe) return new TextDecoder('utf-16le').decode(bytes.subarray(2)).replace(/\0+$/, '');
      if (bytes[0] === 0xfe && bytes[1] === 0xff) return new TextDecoder('utf-16be').decode(bytes.subarray(2)).replace(/\0+$/, '');
      return new TextDecoder('utf-16le').decode(bytes).replace(/\0+$/, '');
    }
    if (enc === 2) return new TextDecoder('utf-16be').decode(bytes).replace(/\0+$/, '');
    return new TextDecoder('utf-8').decode(bytes).replace(/\0+$/, '');
  } catch {
    return '';
  }
}

function cstring(bytes, start, enc) {
  const two = enc === 1 || enc === 2;
  for (let i = start; i < bytes.length - (two ? 1 : 0); i += two ? 2 : 1) {
    if (bytes[i] === 0 && (!two || bytes[i + 1] === 0)) return decodeText(bytes.subarray(start, i), enc);
  }
  return decodeText(bytes.subarray(start), enc);
}

function parseId3(b) {
  const out = {};
  if (b.length < 10 || b[0] !== 0x49 || b[1] !== 0x44 || b[2] !== 0x33) return out;
  const major = b[3];
  const flags = b[5];
  const tagSize = readSynchsafe(b, 6);
  let pos = 10;
  if (flags & 0x40) {
    if (major === 4) pos += readSynchsafe(b, 10);
    else pos += ((b[10] << 24) | (b[11] << 16) | (b[12] << 8) | b[13]) + 4;
  }
  const end = Math.min(10 + tagSize, b.length);
  const map = major === 2 ? TEXT_FRAMES_V22 : TEXT_FRAMES_V23;
  const idLen = major === 2 ? 3 : 4;
  while (pos + idLen <= end) {
    if (b[pos] === 0) break;
    let id, size, headerLen;
    if (major === 2) {
      id = String.fromCharCode(b[pos], b[pos + 1], b[pos + 2]);
      size = (b[pos + 3] << 16) | (b[pos + 4] << 8) | b[pos + 5];
      headerLen = 6;
    } else {
      id = String.fromCharCode(b[pos], b[pos + 1], b[pos + 2], b[pos + 3]);
      if (major === 4) size = readSynchsafe(b, pos + 4);
      else size = (b[pos + 4] << 24) | (b[pos + 5] << 16) | (b[pos + 6] << 8) | b[pos + 7];
      headerLen = 10;
    }
    if (size <= 0 || pos + headerLen + size > end) break;
    const data = b.subarray(pos + headerLen, pos + headerLen + size);
    if (major === 2 ? TEXT_FRAMES_V22[id] : (id === 'APIC' || TEXT_FRAMES_V23[id])) {
      if (id === 'APIC' || id === 'PIC') {
        if (data.length > 5) {
          let mime, enc = data[0], p = 1;
          if (id === 'PIC') {
            const f = String.fromCharCode(data[1], data[2], data[3]).toLowerCase();
            mime = f === 'png' ? 'image/png' : 'image/jpeg';
            p = 4;
          } else {
            let e = p;
            while (e < data.length && data[e] !== 0) e++;
            mime = new TextDecoder('latin1').decode(data.subarray(p, e)) || 'image/jpeg';
            p = e + 1;
          }
          p += 1;
          const descEnd = enc === 1 || enc === 2
            ? (() => { for (let i = p; i < data.length - 1; i += 2) if (data[i] === 0 && data[i + 1] === 0) return i + 2; return data.length; })()
            : (() => { for (let i = p; i < data.length; i++) if (data[i] === 0) return i + 1; return data.length; })();
          const img = data.subarray(descEnd);
          if (img.length > 100) out.art = new Blob([img], { type: mime });
        }
      } else {
        const key = map[id] || (major === 2 ? TEXT_FRAMES_V22[id] : TEXT_FRAMES_V23[id]);
        if (key) {
          const v = decodeText(data.subarray(1), data[0]).trim();
          if (v && !out[key]) out[key] = v;
        }
      }
    }
    pos += headerLen + size;
  }
  if (out.length) {
    const ms = parseInt(out.length, 10);
    if (!isNaN(ms) && ms > 0) out.duration = Math.round(ms / 1000);
    delete out.length;
  }
  return out;
}

function findAtom(b, start, end, name) {
  let p = start;
  while (p + 8 <= end) {
    let size = (b[p] << 24) | (b[p + 1] << 16) | (b[p + 2] << 8) | b[p + 3];
    const type = String.fromCharCode(b[p + 4], b[p + 5], b[p + 6], b[p + 7]);
    let hdr = 8;
    if (size === 1) {
      if (p + 16 > end) return null;
      const hi = (b[p + 8] << 24) | (b[p + 9] << 16) | (b[p + 10] << 8) | b[p + 11];
      const lo = (b[p + 12] << 24) | (b[p + 13] << 16) | (b[p + 14] << 8) | b[p + 15];
      size = hi * 4294967296 + lo;
      hdr = 16;
    } else if (size === 0) {
      size = end - p;
    }
    if (size < hdr || p + size > end + 8) return null;
    if (type === name) return { start: p + hdr, end: Math.min(p + size, end) };
    p += size;
  }
  return null;
}

function ilstValue(b, start, end) {
  const dataAtom = findAtom(b, start, end, 'data');
  if (!dataAtom) return null;
  const type = (b[dataAtom.start] << 24) | (b[dataAtom.start + 1] << 16) | (b[dataAtom.start + 2] << 8) | b[dataAtom.start + 3];
  const payload = b.subarray(dataAtom.start + 8, dataAtom.end);
  if (type === 1) return new TextDecoder('utf-8').decode(payload).trim();
  if (type === 13 || type === 21) {
    let n = 0;
    for (let i = 0; i < payload.length; i++) n = n * 256 + payload[i];
    return n;
  }
  if (type === 16 || type === 17 || type === 18 || type === 24) return new Blob([payload], { type: 'image/jpeg' });
  return null;
}

function parseMp4(b) {
  const out = {};
  const moov = findAtom(b, 0, b.length, 'moov');
  if (!moov) return out;
  const udta = findAtom(b, moov.start, moov.end, 'udta');
  if (udta) {
    const meta = findAtom(b, udta.start, udta.end, 'meta');
    if (meta) {
      const ilst = findAtom(b, meta.start + 4, meta.end, 'ilst');
      if (ilst) {
        const map = { '©nam': 'title', '©ART': 'artist', '©alb': 'album', '©day': 'year', 'covr': 'art' };
        let p = ilst.start;
        while (p + 8 <= ilst.end) {
          let size = (b[p] << 24) | (b[p + 1] << 16) | (b[p + 2] << 8) | b[p + 3];
          const type = String.fromCharCode(b[p + 4], b[p + 5], b[p + 6], b[p + 7]);
          if (size < 8 || p + size > ilst.end) break;
          const key = map[type];
          if (key) {
            const v = ilstValue(b, p + 8, p + size);
            if (v !== null && v !== '' && !out[key]) out[key] = v;
          }
          p += size;
        }
      }
    }
  }
  const mvhd = (() => {
    let found = null;
    const walk = (s, e, depth) => {
      if (found || depth > 3) return;
      let p = s;
      while (p + 8 <= e) {
        let size = (b[p] << 24) | (b[p + 1] << 16) | (b[p + 2] << 8) | b[p + 3];
        const type = String.fromCharCode(b[p + 4], b[p + 5], b[p + 6], b[p + 7]);
        if (size < 8 || p + size > e + 8) break;
        if (type === 'mvhd') { found = { start: p + 8, end: p + size }; return; }
        if (['moov', 'trak', 'udta'].includes(type)) walk(p + 8, Math.min(p + size, e), depth + 1);
        p += size;
      }
    };
    walk(moov.start, moov.end, 0);
    return found;
  })();
  if (mvhd) {
    const ver = b[mvhd.start];
    try {
      if (ver === 1) {
        const ts = (b[mvhd.start + 20] << 24 | b[mvhd.start + 21] << 16 | b[mvhd.start + 22] << 8 | b[mvhd.start + 23]) >>> 0;
        const du = Number(((b[mvhd.start + 24] << 24 | b[mvhd.start + 25] << 16 | b[mvhd.start + 26] << 8 | b[mvhd.start + 27]) >>> 0)) * 4294967296 +
          (((b[mvhd.start + 28] << 24 | b[mvhd.start + 29] << 16 | b[mvhd.start + 30] << 8 | b[mvhd.start + 31]) >>> 0));
        if (ts) out.duration = Math.round(du / ts);
      } else {
        const ts = (b[mvhd.start + 12] << 24 | b[mvhd.start + 13] << 16 | b[mvhd.start + 14] << 8 | b[mvhd.start + 15]) >>> 0;
        const du = (b[mvhd.start + 16] << 24 | b[mvhd.start + 17] << 16 | b[mvhd.start + 18] << 8 | b[mvhd.start + 19]) >>> 0;
        if (ts) out.duration = Math.round(du / ts);
      }
    } catch { /* ignore */ }
  }
  return out;
}

function le32(b, o) {
  return (b[o] | (b[o + 1] << 8) | (b[o + 2] << 16) | (b[o + 3] << 24)) >>> 0;
}

function parseFlac(b) {
  const out = {};
  if (b[0] !== 0x66 || b[1] !== 0x4c || b[2] !== 0x61 || b[3] !== 0x43) return out;
  let p = 4;
  while (p + 4 <= b.length) {
    const last = !!(b[p] & 0x80);
    const type = b[p] & 0x7f;
    const len = (b[p + 1] << 16) | (b[p + 2] << 8) | b[p + 3];
    const start = p + 4;
    const end = Math.min(start + len, b.length);
    if (type === 4) {
      let q = start;
      if (q + 4 > end) return out;
      q += 4 + le32(b, q);
      if (q + 4 > end) return out;
      const count = le32(b, q);
      q += 4;
      for (let i = 0; i < count && q + 4 <= end; i++) {
        const klen = le32(b, q); q += 4;
        if (q + klen + 4 > end) break;
        const key = new TextDecoder('latin1').decode(b.subarray(q, q + klen)); q += klen;
        const vlen = le32(b, q); q += 4;
        if (q + vlen > end) break;
        const val = new TextDecoder('utf-8').decode(b.subarray(q, q + vlen)); q += vlen;
        if (key === 'TITLE') out.title = val.trim();
        if (key === 'ARTIST') out.artist = val.trim();
        if (key === 'ALBUM') out.album = val.trim();
      }
      return out;
    }
    if (last) break;
    p = start + len;
  }
  return out;
}

export async function readTags(file) {
  const head = new Uint8Array(await file.slice(0, Math.min(file.size, 512 * 1024)).arrayBuffer());
  let meta = {};
  const ext = (file.name.split('.').pop() || '').toLowerCase();
  if (head[0] === 0x49 && head[1] === 0x44 && head[2] === 0x33) meta = parseId3(head);
  else if ((head[4] === 0x66 && head[5] === 0x74 && head[6] === 0x79 && head[7] === 0x70) || ext === 'm4a' || ext === 'mp4' || ext === 'aac') meta = parseMp4(head);
  else if (head[0] === 0x66 && head[1] === 0x4c && head[2] === 0x61 && head[3] === 0x43) meta = parseFlac(head);
  if (!meta.title) {
    const base = file.name.replace(/\.[^.]+$/, '').replace(/_/g, ' ').trim();
    const m = base.match(/^(.+?)\s*[-–]\s*(.+)$/);
    if (m) { meta.artist = meta.artist || m[1].trim(); meta.title = m[2].trim(); }
    else meta.title = base || 'Unknown';
  }
  return meta;
}
