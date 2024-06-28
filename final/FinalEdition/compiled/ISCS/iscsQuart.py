import sys
import orjson
import asyncio
from quart import Quart, request, Response
import aiohttp
import uvicorn

CONFIG_PATH = "config.json"

USURL = "http://127.0.0.1:14001/user"
PSURL = "http://127.0.0.1:15000/product"
USIP = ""
USP = ""
PSIP = ""
PSP = ""
http_client = None

app = Quart(__name__)


async def fetch(session, method, url, **kwargs):
    try:
        if 'json' in kwargs:
            kwargs['data'] = orjson.dumps(kwargs.pop('json'))
            kwargs.setdefault('headers', {})['Content-Type'] = 'application/json'

        async with session.request(method, url, **kwargs) as response:
            return await response.read(), response.status
    except Exception as e:
        print(f"Error in fetch: {e}")
        return None, 500 
    
@app.before_serving
async def startup():
    global http_client
    connector = aiohttp.TCPConnector(limit_per_host=10)
    http_client = aiohttp.ClientSession(connector=connector)

@app.after_serving
async def cleanup():
    await http_client.close()

@app.route('/user/<string:id>', methods=['GET'])
async def handle_user_get(id):
    url = f"{USURL}/{id}"
    print(f"Requesting URL: {url}")
    response, status = await fetch(http_client, 'get', url)
    return Response(response, status=status, mimetype='application/json')

@app.route('/user', methods=['POST'])
async def handle_user_post():
    url = USURL
    print(f"Requesting URL: {url}")
    json_data = await request.get_json()
    print(2)
    response, status = await fetch(http_client, 'post', url, json=json_data)
    print(3)
    return Response(response, status=status, mimetype='application/json')

@app.route('/product/<string:id>', methods=['GET'])
async def handle_product_get(id):
    url = f"{PSURL}/{id}"
    print(f"Requesting URL: {url}")
    response, status = await fetch(http_client, 'get', url)
    return Response(response, status=status, mimetype='application/json')

@app.route('/product', methods=['POST'])
async def handle_product_post():
    url = PSURL
    print(f"Requesting URL: {url}")
    response, status = await fetch(http_client, 'post', url, json=await request.get_json())
    return Response(response, status=status, mimetype='application/json')

@app.route('/', methods=['POST'])
async def handle_kill_post():
    json_data = await request.get_json()
    if json_data.get('command') in ['shutdown', 'restart']:
        url = f"http://{USIP}:{USP}"
        url2 = f"http://{PSIP}:{PSP}"
        task1 = fetch(http_client, 'post', url, json=json_data)
        task2 = fetch(http_client, 'post', url2, json=json_data)
        responses = await asyncio.gather(task1, task2)
        return Response(responses[0][0], status=responses[0][1], mimetype='application/json')

def main(argv: list):
    try:
        with open(CONFIG_PATH, 'rb') as config: 
            config_data = orjson.loads(config.read())
    except FileNotFoundError:
        print(f"Error: File '{CONFIG_PATH}' not found.")
        return -1
    except orjson.JSONDecodeError:
        print(f"Error: JSON failed to parse {CONFIG_PATH}")
        return -1

    global USIP, USP, PSIP, PSP, USURL, PSURL
    USIP = config_data["UserService"].get("ip")
    USP = config_data["UserService"].get("port")
    PSIP = config_data["ProductService"].get("ip")
    PSP = config_data["ProductService"].get("port")

    USURL = f"http://{USIP}:{USP}/user"
    PSURL = f"http://{PSIP}:{PSP}/product"
    print(f"UserService IP: {USIP}, Port: {USP}")
    print(f"ProductService IP: {PSIP}, Port: {PSP}")
    print(f"UserService URL: {USURL}")
    print(f"ProductService URL: {PSURL}")
    iscs_service_port = config_data["InterServiceCommunication"].get("port")

    # app.run(debug=True, port=iscs_service_port)
    import uvicorn
    import_name = __name__ + ":app"
    uvicorn.run(import_name, host="0.0.0.0", port=14002, workers=4)
    # uvicorn.run("iscs:app", host="127.0.0.1", port=iscs_service_port, workers=4, reload=True)

    # uvicorn.run(app, host="0.0.0.0", port=int(iscs_service_port), workers=4)

if __name__ == "__main__":
    main(sys.argv)
