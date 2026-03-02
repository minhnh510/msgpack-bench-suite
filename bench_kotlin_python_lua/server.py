import json
import msgpack
import time
from aiohttp import web
from lupa import LuaRuntime

N_ROWS = 2500
DATA = [
    {
        "id": i,
        "symbol": "XAUUSD" if i % 2 else "BTCUSDT",
        "price": 2400.0 + (i % 300) * 0.15,
        "qty": (i % 5) + 1,
        "vol": (i % 100) * 0.37,
        "ts": 1700000000 + i,
    }
    for i in range(N_ROWS)
]

lua = LuaRuntime(unpack_returned_tuples=True)
lua.execute((__import__('pathlib').Path(__file__).with_name('transform.lua')).read_text(encoding='utf-8'))
transform_row = lua.globals().transform_row


def build_payload_with_lua():
    rows = []
    for r in DATA:
        score, flag = transform_row(int(r['id']), float(r['price']), float(r['qty']), float(r['vol']))
        rows.append({
            'id': r['id'],
            'symbol': r['symbol'],
            'price': r['price'],
            'qty': r['qty'],
            'vol': r['vol'],
            'score': float(score),
            'flag': bool(flag),
            'ts': r['ts'],
        })
    return {
        'meta': {'rows': len(rows), 'mode': 'lua', 'generated_at': time.time()},
        'rows': rows,
    }

STATIC_PAYLOAD = build_payload_with_lua()
STATIC_JSON = json.dumps(STATIC_PAYLOAD, ensure_ascii=False, separators=(',', ':')).encode('utf-8')
STATIC_MSGPACK = msgpack.packb(STATIC_PAYLOAD, use_bin_type=True)


async def h_health(_):
    return web.json_response({'ok': True})


async def h_json_static(_):
    return web.Response(body=STATIC_JSON, content_type='application/json')


async def h_msgpack_static(_):
    return web.Response(body=STATIC_MSGPACK, content_type='application/x-msgpack')


async def h_json_lua(_):
    payload = build_payload_with_lua()
    b = json.dumps(payload, ensure_ascii=False, separators=(',', ':')).encode('utf-8')
    return web.Response(body=b, content_type='application/json')


async def h_msgpack_lua(_):
    payload = build_payload_with_lua()
    b = msgpack.packb(payload, use_bin_type=True)
    return web.Response(body=b, content_type='application/x-msgpack')


def create_app():
    app = web.Application()
    app.router.add_get('/health', h_health)
    app.router.add_get('/json_static', h_json_static)
    app.router.add_get('/msgpack_static', h_msgpack_static)
    app.router.add_get('/json_lua', h_json_lua)
    app.router.add_get('/msgpack_lua', h_msgpack_lua)
    return app


if __name__ == '__main__':
    web.run_app(create_app(), host='127.0.0.1', port=18990)
