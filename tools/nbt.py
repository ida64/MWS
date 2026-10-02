"""Minimal NBT reader/writer (gzip, big-endian) for structure files."""
import gzip, struct, io

class Tag:
    def __init__(self, t, v): self.t, self.v = t, v
    def __repr__(self): return f"<{self.t}:{self.v!r}>"

def _r(f, fmt): return struct.unpack('>' + fmt, f.read(struct.calcsize('>' + fmt)))[0]
def _rs(f): n = _r(f, 'H'); return f.read(n).decode('utf-8')

def _read_payload(f, t):
    if t == 1: return _r(f, 'b')
    if t == 2: return _r(f, 'h')
    if t == 3: return _r(f, 'i')
    if t == 4: return _r(f, 'q')
    if t == 5: return _r(f, 'f')
    if t == 6: return _r(f, 'd')
    if t == 7: n = _r(f, 'i'); return f.read(n)
    if t == 8: return _rs(f)
    if t == 9:
        et = _r(f, 'b'); n = _r(f, 'i')
        return (et, [_read_payload(f, et) for _ in range(n)])
    if t == 10:
        d = {}
        while True:
            ct = _r(f, 'b')
            if ct == 0: return d
            name = _rs(f); d[name] = Tag(ct, _read_payload(f, ct))
    if t == 11: n = _r(f, 'i'); return [_r(f, 'i') for _ in range(n)]
    if t == 12: n = _r(f, 'i'); return [_r(f, 'q') for _ in range(n)]
    raise ValueError(t)

def read(path):
    with gzip.open(path, 'rb') as g: f = io.BytesIO(g.read())
    t = _r(f, 'b'); _rs(f); return _read_payload(f, t)

def plain(v):
    """Strip tags for printing."""
    if isinstance(v, Tag): return plain(v.v)
    if isinstance(v, dict): return {k: plain(x) for k, x in v.items()}
    if isinstance(v, tuple): return [plain(x) for x in v[1]]
    return v

# ---- writer: python values -> tags ----
def _w(f, fmt, v): f.write(struct.pack('>' + fmt, v))
def _ws(f, s): b = s.encode('utf-8'); _w(f, 'H', len(b)); f.write(b)

def _write_payload(f, t, v):
    if t == 1: _w(f, 'b', v)
    elif t == 3: _w(f, 'i', v)
    elif t == 6: _w(f, 'd', v)
    elif t == 8: _ws(f, v)
    elif t == 9:
        et, items = v; _w(f, 'b', et if items else 0); _w(f, 'i', len(items))
        for x in items: _write_payload(f, et, x)
    elif t == 10:
        for k, (ct, cv) in v.items():
            _w(f, 'b', ct); _ws(f, k); _write_payload(f, ct, cv)
        _w(f, 'b', 0)
    else: raise ValueError(t)

def write(path, root):
    f = io.BytesIO(); _w(f, 'b', 10); _ws(f, ''); _write_payload(f, 10, root)
    with gzip.open(path, 'wb') as g: g.write(f.getvalue())
