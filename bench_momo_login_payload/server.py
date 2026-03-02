import json
import msgpack
from aiohttp import web
from pathlib import Path

ROOT = Path(__file__).resolve().parent
PAYLOAD = json.loads((ROOT / 'payload.json').read_text(encoding='utf-8'))
JSON_BYTES = json.dumps(PAYLOAD, ensure_ascii=False, separators=(',', ':')).encode('utf-8')
MSGPACK_BYTES = msgpack.packb(PAYLOAD, use_bin_type=True)


async def h_health(_):
    return web.json_response({'ok': True})


async def h_json(_):
    return web.Response(body=JSON_BYTES, content_type='application/json')


async def h_msgpack(_):
    return web.Response(body=MSGPACK_BYTES, content_type='application/x-msgpack')


def create_app():
    app = web.Application()
    app.router.add_get('/health', h_health)
    app.router.add_get('/json', h_json)
    app.router.add_get('/msgpack', h_msgpack)
    return app


if __name__ == '__main__':
    web.run_app(create_app(), host='127.0.0.1', port=18991)
