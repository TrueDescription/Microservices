from flask import Flask, request, Response, jsonify
import aiohttp
import logging
import orjson
import asyncio

app = Flask(__name__)
log = logging.getLogger('werkzeug')
log.setLevel(logging.ERROR)

# Service Registry and Round Robin Counters
services = {"user": [], "product": []}
rr_counters = {"user": 0, "product": 0}
cache = {"user": {}, "product": {}}

@app.route('/register', methods=['POST'])
async def register_service():
    data = request.get_json()
    service_type = data.get("type")
    address = data.get("address")
    if service_type not in services:
        return jsonify({"error": "Invalid service type"}), 400
    services[service_type].append(address)
    print(f"Registering {service_type} at {address}")
    return jsonify({"message": "Service registered successfully"}), 200

def get_service_url(service_type):
    if services[service_type]:
        url = services[service_type][rr_counters[service_type] % len(services[service_type])]
        rr_counters[service_type] = (rr_counters[service_type] + 1) % len(services[service_type])
        return url
    return None

async def handle_get_or_post(service_type, id=None, method='get', payload=None):
    cache_key = service_type if id is None else f"{service_type}/{id}"
    if id is not None and id in cache[service_type]:
        return Response(cache[service_type][id], status=200, mimetype='application/json')
    
    service_url = get_service_url(service_type)
    if not service_url:
        return jsonify({"error": f"No {service_type} service available"}), 503
    
    async with aiohttp.ClientSession() as session:
        url = f"{service_url}" if id is None else f"{service_url}/{id}"
        response, status = await fetch(session, method, url, json=payload)
        if status == 200 and id is not None:
            cache[service_type][id] = response
        return Response(response, status=status, mimetype='application/json')

@app.route('/<service_type>/<int:id>', methods=['GET', 'POST'])
async def handle_request(service_type, id):
    if service_type not in services:
        return jsonify({"error": "Invalid service type"}), 400
    if request.method == 'POST':
        json_payload = request.get_json()
        return await handle_get_or_post(service_type, method='post', payload=json_payload)
    return await handle_get_or_post(service_type, id=id)

@app.route('/', methods=['POST'])
async def handle_kill_post():
    kill_data = request.get_json()
    async with aiohttp.ClientSession() as session:
        for service_type, urls in services.items():
            for url in urls:
                base_url = url.rsplit('/', 1)[0]
                await fetch(session, 'post', f"{base_url}", json=kill_data)
    return jsonify({"message": "Services killed"}), 200

async def fetch(session, method, url, **kwargs):
    if 'json' in kwargs:
        kwargs['data'] = orjson.dumps(kwargs.pop('json'))
        kwargs.setdefault('headers', {})['Content-Type'] = 'application/json'
    
    async with session.request(method, url, **kwargs) as response:
        return await response.read(), response.status
if __name__ == "__main__":
    app.run(debug=True, port=14002)
