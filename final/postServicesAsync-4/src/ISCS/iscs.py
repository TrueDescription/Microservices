from flask import Flask, request, Response, jsonify
import asyncio
import aiohttp
import logging
import orjson
import sys

app = Flask(__name__)
log = logging.getLogger('werkzeug')
log.setLevel(logging.ERROR)

# Service Registry
services = {
    "user": [],
    "product": []
}

# Round Robin Counters
rr_counters = {
    "user": 0,
    "product": 0
}

cache = {
    "user": {},
    "product": {}
}

@app.route('/register', methods=['POST'])
async def register_service():
    data = request.get_json()
    service_type = data.get("type")
    address = data.get("address")
    print(f"Registering {service_type} at {address}")
    if service_type in services:
        services[service_type].append(address)
        return jsonify({"message": "Service registered successfully"}), 200
    return jsonify({"error": "Invalid service type"}), 400

def get_service_url(service_type):
    if services[service_type]:
        url = services[service_type][rr_counters[service_type] % len(services[service_type])]
        rr_counters[service_type] += 1
        return url
    return None

@app.route('/user/<int:id>', methods=['GET'])
async def handle_user_get(id):
    if id in cache['user']:
        return Response(cache['user'][id], status=200, mimetype='application/json')
    service_url = get_service_url("user")
    if not service_url:
        return jsonify({"error": "No user service available"}), 503
    async with aiohttp.ClientSession() as session:
        response, status = await fetch(session, 'get', f"{service_url}/{id}")
        if status == 200:
            cache['user'][id] = response
        return Response(response, status=status, mimetype='application/json')

@app.route('/product/<int:id>', methods=['GET'])
async def handle_product_get(id):
    if id in cache['product']:
        return Response(cache['product'][id], status=200, mimetype='application/json')
    service_url = get_service_url("product")
    if not service_url:
        return jsonify({"error": "No product service available"}), 503
    async with aiohttp.ClientSession() as session:
        response, status = await fetch(session, 'get', f"{service_url}/{id}")
        if status == 200:
            cache['product'][id] = response
        return Response(response, status=status, mimetype='application/json')

@app.route('/user', methods=['POST'])
async def handle_user_post():
    json=request.get_json()
    id = json.get("id")
    if id in cache['user']:
        cache['user'].pop(json.get("id"))
    service_url = get_service_url("user")
    if not service_url:
        return jsonify({"error": "No user service available"}), 503
    async with aiohttp.ClientSession() as session:
        response, status = await fetch(session, 'post', f"{service_url}", json=json)
        return Response(response, status=status, mimetype='application/json')

@app.route('/product', methods=['POST'])
async def handle_product_post():
    json=request.get_json()
    id = json.get("id")
    if id in cache['product']:
        cache['product'].pop(json.get("id"))
    service_url = get_service_url("product")
    if not service_url:
        return jsonify({"error": "No product service available"}), 503
    async with aiohttp.ClientSession() as session:
        response, status = await fetch(session, 'post', f"{service_url}", json=json)
        return Response(response, status=status, mimetype='application/json')

@app.route('/', methods=['POST'])
async def handle_kill_post():
    for service in services:
        for url in services[service]:
            base_url = url.rsplit('/', 1)[0]
            async with aiohttp.ClientSession() as session:
                await fetch(session, 'post', f"{base_url}", json=request.get_json())
    return jsonify({"message": "Services killed"}), 200
    
async def fetch(session, method, url, **kwargs):
    if 'json' in kwargs:
        kwargs['data'] = orjson.dumps(kwargs.pop('json'))
        kwargs.setdefault('headers', {})['Content-Type'] = 'application/json'
    
    async with session.request(method, url, **kwargs) as response:
        return await response.read(), response.status

def main(argv: list):
    CONFIG_PATH = "config.json"
    try:
        with open(CONFIG_PATH, 'rb') as config:
            config_data = orjson.loads(config.read())
    except FileNotFoundError:
        print(f"Error: File '{CONFIG_PATH}' not found.")
        return -1
    except orjson.JSONDecodeError:
        print(f"Error: JSON failed to parse {CONFIG_PATH}")
        return -1
    iscs_service_port = config_data["InterServiceCommunication"].get("port")

    app.run(debug=True, port=iscs_service_port, threaded=True, use_reloader=False)

if __name__ == "__main__":
    main(sys.argv)
