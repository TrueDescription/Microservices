import json
import sys
import requests
from flask import Flask, jsonify, request
import asyncio


CONFIG_PATH = "config.json"

USURL = ""
PSURL = ""
USIP = ""
USP = ""
PSIP = ""
PSP = ""

app = Flask(__name__)


def json_response(response, status_code):
    return jsonify(response), status_code

@app.route('/user/<string:id>', methods=['GET'])
async def handle_user_get(id):
    try:
        if request.method == 'GET':
            url = f"{USURL}/{id}"
            response = await asyncio.get_event_loop().run_in_executor(None, requests.get, url)
            return json_response(response.json(), response.status_code)
        else:
            return json_response({}, 400)
    except Exception as e:
        return json_response({}, 500)

@app.route('/user', methods=['POST'])
async def handle_user_post():
    try:
        if request.method == 'POST':
            url = USURL
            response = await asyncio.get_event_loop().run_in_executor(None, requests.post, url, data=request.get_data(), headers=request.headers, json=request.json)
            return json_response(response.json(), response.status_code)
        else:
            return json_response({}, 400)
    except Exception as e:
        return json_response({}, 500)


@app.route('/product/<string:id>', methods=['GET'])
async def handle_product_get(id):
    try:
        if request.method == 'GET':
            url = f"{PSURL}/{id}"
            response = await asyncio.get_event_loop().run_in_executor(None, requests.get, url)
            return json_response(response.json(), response.status_code)
        else:
            return json_response({}, 400)
    except Exception as e:
        return json_response({}, 500)

@app.route('/product', methods=['POST'])
async def handle_product_post():
    try:
        if request.method == 'POST':
            url = PSURL
            response = await asyncio.get_event_loop().run_in_executor(None, requests.post, url, data=request.get_data(), headers=request.headers, json=request.json)
            return json_response(response.json(), response.status_code)
        else:
            return json_response({}, 400)
    except Exception as e:
        print(e)
        return json_response({}, 500)

@app.route('/', methods=['POST'])
async def handle_kill_post():
    try:
        if request.method == 'POST':
            json_data = await request.get_json()
            if json_data.get('command') == 'shutdown':
                url = f"http://{USIP}:{USP}"
                url2 = f"http://{PSIP}:{PSP}"
                response = await asyncio.get_event_loop().run_in_executor(None, requests.post, url, data=request.data, headers=request.headers, json=request.json)
                response2 = await asyncio.get_event_loop().run_in_executor(None, requests.post, url2, data=request.data, headers=request.headers, json=request.json)
                return json_response(response.json(), response.status_code)
            elif json_data.get('command') == 'restart':
                url = f"http://{USIP}:{USP}"
                url2 = f"http://{PSIP}:{PSP}"
                response = await asyncio.get_event_loop().run_in_executor(None, requests.post, url, data=request.data, headers=request.headers, json=request.json)
                response2 = await asyncio.get_event_loop().run_in_executor(None, requests.post, url2, data=request.data, headers=request.headers, json=request.json)
                return json_response(response.json(), response.status_code)
        else:
            return json_response({}, 400)
    except Exception as e:
        print(e)
        return json_response({}, 500)
    


def main(argv: list):
    """
    Function: main

    This function serves as the entry point for the Inter-Service Communication (ISC) application.
    It reads the configuration file, extracts necessary information, and initializes the application by running the Flask app.

    Parameters:
    - argv: List containing command-line arguments. Unused in this implementation.

    Returns:
    - None

    Exceptions:
    - Prints an error message and returns -1 if the configuration file is not found or if there is a JSON decoding error.

    """
    try:
        with open(CONFIG_PATH, 'r',  encoding='utf-8') as config:
            config_data = json.load(config)
    except FileNotFoundError:
        print(f"Error: File '{CONFIG_PATH}' not found.")
        return -1
    except json.JSONDecodeError:
        print(f"Error: JSON failed to parse {CONFIG_PATH}")
        return -1
    

    global USIP
    global USP
    global PSIP
    global PSP
    USIP = config_data["UserService"].get("ip")
    USP = config_data["UserService"].get("port")
    PSIP = config_data["ProductService"].get("ip")
    PSP = config_data["ProductService"].get("port")

    global USURL
    global PSURL
    
    USURL = f"http://{USIP}:{USP}/user"
    PSURL = f"http://{PSIP}:{PSP}/product"
    iscs_service_ip = config_data["InterServiceCommunication"].get("ip")
    iscs_service_port = config_data["InterServiceCommunication"].get("port")

 
    app.run(debug=True, port=iscs_service_port)
    




if __name__ == "__main__":
    main(sys.argv)