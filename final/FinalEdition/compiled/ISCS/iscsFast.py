import orjson
from fastapi import FastAPI, Request, Response
import aiohttp
from aiohttp import ClientSession
from pydantic import BaseModel
from contextlib import asynccontextmanager


CONFIG_PATH = "config.json"


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Load the ML model
    global http_client
    http_client = aiohttp.ClientSession(json_serialize=orjson.dumps)
    await load_config()    
    yield
    await http_client.close()

app = FastAPI(lifespan=lifespan)

# Configuration for services - replace these with your actual configurations
USURL = ""
PSURL = ""
USIP = ""
USP = ""
PSIP = ""
PSP = ""

# A single ClientSession for the lifetime of the application
http_client: ClientSession = None

async def load_config():
    global USURL, PSURL
    try:
        with open(CONFIG_PATH, 'rb') as config_file:
            config_data = orjson.loads(config_file.read())
            # Construct service URLs
            USIP = config_data["UserService"]["ip"]
            USP = config_data["UserService"]["port"]
            PSIP = config_data["ProductService"]["ip"]
            PSP = config_data["ProductService"]["port"]
            
            USURL = f"http://{USIP}:{USP}/user"
            PSURL = f"http://{PSIP}:{PSP}/product"
            
    except FileNotFoundError as e:
        print(f"Error: Configuration file '{CONFIG_PATH}' not found.")
        raise e
    except orjson.JSONDecodeError as e:
        print(f"Error: Failed to parse the configuration file '{CONFIG_PATH}'.")
        raise e




async def fetch(method: str, url: str, **kwargs):
    global http_client
    headers = kwargs.get('headers', {})
    headers['Content-Type'] = 'application/json'
    if 'json' in kwargs:
        kwargs['data'] = orjson.dumps(kwargs.pop('json'))
    kwargs['headers'] = headers
    
    async with http_client.request(method, url, **kwargs) as response:
        return await response.read(), response.status

@app.get('/user/{id}')
async def handle_user_get(id: str):
    url = f"{USURL}/{id}"
    response, status = await fetch('GET', url)
    return Response(content=response, status_code=status, media_type='application/json')

@app.post('/user')
async def handle_user_post(request: Request):
    json_data = await request.json()
    url = USURL
    response, status = await fetch('POST', url, json=json_data)
    return Response(content=response, status_code=status, media_type='application/json')

@app.get('/product/{id}')
async def handle_product_get(id: str):
    url = f"{PSURL}/{id}"
    response, status = await fetch('GET', url)
    return Response(content=response, status_code=status, media_type='application/json')

@app.post('/product')
async def handle_product_post(request: Request):
    json_data = await request.json()
    url = PSURL
    response, status = await fetch('POST', url, json=json_data)
    return Response(content=response, status_code=status, media_type='application/json')

# Example of handling a POST request that might trigger a shutdown or restart
class CommandModel(BaseModel):
    command: str

@app.post('/command')
async def handle_command_post(command: CommandModel):
    if command.command in ['shutdown', 'restart']:
        url_user_service = f"http://{USIP}:{USP}"
        url_product_service = f"http://{PSIP}:{PSP}"
        task1 = fetch('POST', url_user_service, json={"command": command.command})
        task2 = fetch('POST', url_product_service, json={"command": command.command})
        responses = await asyncio.gather(task1, task2)
        return Response(content=responses[0][0], status_code=responses[0][1], media_type='application/json')

if __name__ == "__main__":
    import uvicorn
    import_name = __name__ + ":app"
    uvicorn.run(import_name, host="0.0.0.0", port=14002, workers=4)
