import json
import os
import signal
import sys
import requests
from flask import Flask, jsonify, request, make_response


CONFIG_PATH = "config.json"

USURL = ""
PSURL = ""
USIP = ""
USP = ""
PSIP = ""
PSP = ""

app = Flask(__name__)


def json_response(response, status_code):
    """
    Format the HTTP response into a JSON format.
    
    Parameters:
    - response: The HTTP response object.
    - status_code: The HTTP status code.
    
    Returns:
    A JSON response with status code, content, and headers.
    """
    # Extract relevant information from the Response object
    response_data = {
        'status_code': status_code,
        'content': response.json() if 'application/json' in response.headers.get('content-type', '') else response.text,
        'headers': dict(response.headers),
    }
    # print(response.json())
    return jsonify(response_data), status_code

@app.route('/user/<int:id>', methods=['GET'])
def handle_user_get(id):
    """
    Handle GET requests for fetching user details by ID.
    
    Parameters:
    - id: User ID.
    
    Returns:
    JSON response with user details.
    """
    try:
        if request.method == 'GET':
            url = f"{USURL}/{id}"
            response = requests.get(url)
            return json_response(response, response.status_code)
        else:
            return json_response({}, 400)
    except Exception as e:
        return json_response({}, 500)

@app.route('/user', methods=['POST'])
def handle_user_post():
    """
    Function: handle_user_post

    This function defines the route for handling POST requests to create a new user. It communicates with the User Service (US)
    by sending a POST request to the specified USURL with the user data.

    Endpoint:
    - POST /user

    Parameters:
    - None

    Returns:
    - JSON response containing the result of the POST request to the User Service.

    Exceptions:
    - Returns a JSON response with a status code of 400 for invalid requests.
    - Returns a JSON response with a status code of 500 for internal server errors.

    """
    try:
        if request.method == 'POST':
            url = USURL
            response = requests.post(url, data=request.data, headers=request.headers, json=request.json)
            return json_response(response, response.status_code)
        else:
            return json_response({}, 400)
    except Exception as e:
        return json_response({}, 500)


@app.route('/product/<int:id>', methods=['GET'])
def handle_product_get(id):
    """
    Function: handle_product_get

    This function defines the route for handling GET requests to retrieve product information by ID. It communicates with the Product Service (PS)
    by sending a GET request to the specified PSURL with the product ID.

    Endpoint:
    - GET /product/<int:id>

    Parameters:
    - id (int): The product ID to retrieve.

    Returns:
    - JSON response containing the result of the GET request to the Product Service.

    Exceptions:
    - Returns a JSON response with a status code of 400 for invalid requests.
    - Returns a JSON response with a status code of 500 for internal server errors.

    """

    try:
        if request.method == 'GET':
            url = f"{PSURL}/{id}"
            response = requests.get(url)
            return json_response(response, response.status_code)
        else:
            return json_response({}, 400)
    except Exception as e:
        return json_response({}, 500)

@app.route('/product', methods=['POST'])
def handle_product_post():
    """
    Function: handle_product_post

    This function defines the route for handling POST requests to create or update product information. 
    It communicates with the Product Service (PS) by sending a POST request to the specified PSURL.

    Endpoint:
    - POST /product

    Parameters:
    - None

    Returns:
    - JSON response containing the result of the POST request to the Product Service.

    Exceptions:
    - Returns a JSON response with a status code of 400 for invalid requests.
    - Returns a JSON response with a status code of 500 for internal server errors.

    """
    try:
        if request.method == 'POST':
            url = PSURL
            # print("PRODUCT POST REQUEST RECIEVED")
            response = requests.post(url, data=request.data, headers=request.headers, json=request.json)
            # print(response.status_code)
            return json_response(response, response.status_code)
        else:
            return json_response({}, 400)
    except Exception as e:
        print(e)
        return json_response({}, 500)

@app.route('/', methods=['POST'])
def handle_kill_post():
    """
    Function: handle_kill_post

    This function defines the route for handling POST requests to shut down or restart the services.
    It communicates with both the User Service (US) and Product Service (PS) by sending POST requests to their respective endpoints.

    Endpoint:
    - POST /

    Parameters:
    - None

    Returns:
    - JSON response containing the result of the POST requests to both the User Service and Product Service.

    Exceptions:
    - Returns a JSON response with a status code of 400 for invalid requests.
    - Returns a JSON response with a status code of 500 for internal server errors.

    """
    try:
        if request.method == 'POST':
            json = request.get_json()
            if json.get('command') == 'shutdown':
                
                url = f"http://{USIP}:{USP}"
                url2 = f"http://{PSIP}:{PSP}"
                # print("PRODUCT POST REQUEST RECIEVED")
                response = requests.post(url, data=request.data, headers=request.headers, json=request.json)
                response2 = requests.post(url2, data=request.data, headers=request.headers, json=request.json)

                # print(response.status_code)
                return json_response(response, response.status_code)
            elif json.get('command') == 'restart' :
                url = f"http://{USIP}:{USP}"
                url2 = f"http://{PSIP}:{PSP}"
                # print("PRODUCT POST REQUEST RECIEVED")
                response = requests.post(url, data=request.data, headers=request.headers, json=request.json)
                response2 = requests.post(url2, data=request.data, headers=request.headers, json=request.json)
                # print(response.status_code)
                

                return json_response(response, response.status_code)
                
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