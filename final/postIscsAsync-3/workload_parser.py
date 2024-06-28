import sys
import json
from typing import List
import requests

CONFIG_PATH = "config.json"
# APIs = ['USER', 'PRODUCT', 'ORDER']

#USER API CALLS
    #POST REQUESTS
def create_user(command: List[str], url: str):
    # print(f"API ENDPOINT: {command[0]} |||| API_FUNCTION: {command[1]} |||| LINE: {command}")
    url = url + "/user"
    data = {"command" : "create", "id" : int(command[2]), "username" : command[3], "email" : command[4] ,"password" : command[5]}
    try:
        headers = {'Content-Type' : 'application/json', 'Authorization' : 'Bearer your_token'}
        response = requests.post(url, json=data, headers=headers)
        # print(response)
        return response
    except Exception as e:
        print(e)

def update_user(command: List[str], url: str):
    # print(f"API ENDPOINT: {command[0]} |||| API_FUNCTION: {command[1]} |||| LINE: {command}")
    url = url + "/user"
    data = {"command" : "update", "id" : int(command[2]), "username" : command[3].split(':')[1], "email" : command[4].split(':')[1] ,"password" : command[5].split(':')[1]}
    try:
        headers = {'Content-Type' : 'application/json', 'Authorization' : 'Bearer your_token'}
        response = requests.post(url, json=data, headers=headers)
        # print(response)
        return response
    except Exception as e:
        print(e)

def delete_user(command: List[str], url: str):
    # print(f"API ENDPOINT: {command[0]} |||| API_FUNCTION: {command[1]} |||| LINE: {command}")
    url = url + "/user"
    data = {"command" : "delete", "id" : int(command[2]), "username" : command[3], "email" : command[4] ,"password" : command[5]}
    try:
        headers = {'Content-Type' : 'application/json', 'Authorization' : 'Bearer your_token'}
        response = requests.post(url, json=data, headers=headers)
        return response
        # print(response)
    except Exception as e:
        print(e)

    #GET REQUESTS
def get_user(command: List[str], url: str):
    # print(f"API ENDPOINT: {command[0]} |||| API_FUNCTION: {command[1]} |||| LINE: {command}")
    url = url + f"/user/{command[2]}"
    try:
        response = requests.get(url=url, params=command[2])
        return response
        # print(response)
    except Exception as e:
        print(e)

#PRODUCT API CALLS
    # POST REQUESTS
def create_product(command: List[str], url: str):
    # print(f"API ENDPOINT: {command[0]} |||| API_FUNCTION: {command[1]} |||| LINE: {command}")
    url = url + "/product"
    data = {"command" : "create", "id" : int(command[2]), "name" : command[3], "description" : command[4] ,"price" : float(command[5]), "quantity" : int(command[6])}
    try:
        headers = {'Content-Type' : 'application/json', 'Authorization' : 'Bearer your_token'}
        response = requests.post(url, json=data, headers=headers)
        return response
        # print(response)
        # print(response.json())
    except Exception as e:
        print(e)

def update_product(command: List[str], url):
    # print(f"API ENDPOINT: {command[0]} |||| API_FUNCTION: {command[1]} |||| LINE: {command}")
    url = url + "/product"
    data = {"command" : "update", "id" : int(command[2]), "name" : command[3].split(':')[1], "description" : command[4].split(':')[1] ,"price" : float(command[5].split(':')[1]), "quantity" : int(command[6].split(':')[1])}
    try:
        headers = {'Content-Type' : 'application/json', 'Authorization' : 'Bearer your_token'}
        response = requests.post(url, json=data, headers=headers)
        return response
        # print(response)
        # print(response.json())
    except Exception as e:
        print(e)

def delete_product(command: List[str], url: str):
    # print(f"API ENDPOINT: {command[0]} |||| API_FUNCTION: {command[1]} |||| LINE: {command}")
    url = url + "/product"
    data = {"command" : "delete", "id" : int(command[2]), "name" : command[3], "price" : float(command[4]), "quantity" : int(command[5])}
    try:
        headers = {'Content-Type' : 'application/json', 'Authorization' : 'Bearer your_token'}
        response = requests.post(url, json=data, headers=headers)
        return response
        # print(response)
        # print(response.json())
    except Exception as e:
        print(e)

    #GET REQUESTS 
def info_product(command: List[str], url: str):
    # print(f"API ENDPOINT: {command[0]} |||| API_FUNCTION: {command[1]} |||| LINE: {command}")
    url = url + f"/product/{command[2]}"
    arg = ''
    try:
        response = requests.get(url=url)
        return response
        # print("INFOO!!!!")
        # print(response)
        # print(response.json())
    except Exception as e:
        print(e)

#ORDER API CALLS
def place_order(command: List[str], url: str):
    """
    {
        "command" : "place order",
        "product_id" : <product_id>,
        "user_id" : <user_id>,
        "quantity" : <quantity>
    }
    Edge cases:
        1. All fields are required for a valid order.
        2. Quantity cannot go over the available limit.
    """
    # print(f"API ENDPOINT: {command[0]} |||| API_FUNCTION: {command[1]} |||| LINE: {command}")
    url = url + "/order"
    data = {"command" : "place order", "product_id" : int(command[2]), "user_id" : int(command[3]), "quantity" : int(command[4])}
    try:
        headers = {'Content-Type' : 'application/json', 'Authorization' : 'Bearer your_token'}
        response = requests.post(url, json=data, headers=headers)
        # print(response.json())
        return response
        # print(response.content)
    except Exception as e:
        print(e)
    

def shutdown(url):
    data = {"command" : "shutdown"}
    try:
        headers = {'Content-Type' : 'application/json', 'Authorization' : 'Bearer your_token'}
        response = requests.post(url, json=data, headers=headers)
        # print(response.json())
        return response
        # print(response.content)
    except Exception as e:
        print(e)

def wipe(url):
    data = {"command" : "restart"}
    try:
        headers = {'Content-Type' : 'application/json', 'Authorization' : 'Bearer your_token'}
        response = requests.post(url, json=data, headers=headers)
        # print(response.json())
        return response
        # print(response.content)
    except Exception as e:
        print(e)


API_COMMANDS = {'USER' : {'create' : create_user , 'update' : update_user, 'delete': delete_user, 'get': get_user},
                'PRODUCT' : {'create' : create_product , 'update' : update_product, 'delete' : delete_product, 'info' : info_product},
                'ORDER' : {'place' : place_order}
                }



def main(argv: list):
    # Initialize an empty list
    if len(argv) != 2: 
        print("Usage: python3 process_file.py <InputFileName>")
        sys.exit(0)
    
    file_name = argv[1]
    api_req = []

    # Parse workload file
    try:
        # Open the file and read each line
        with open(file_name, 'r') as file:
            for line in file:
                # Append each line to the list (removing newline characters)
                api_req.append(line.strip())

        # Print the list
        # print("Lines inserted into list:", api_req)

    except FileNotFoundError:
        print(f"Error: File '{file_name}' not found.")
        return -1

    # read config.json
    config_data = None
    try:
        with open(CONFIG_PATH, 'r',  encoding='utf-8') as config:
            config_data = json.load(config)
    except FileNotFoundError:
        print(f"Error: File '{CONFIG_PATH}' not found.")
        return -1
    except json.JSONDecodeError:
        print(f"Error: JSON failed to parse {CONFIG_PATH}")
        return -1
    

    # order_service_ip = config_data["OrderService"].get("ip")
    order_service_ip = "127.0.0.1"
    # ^ above is order
    order_service_port = config_data["OrderService"].get("port")
    # print(f"IP: {order_service_ip} PORT: {order_service_port}")
    #print(api_req)

    # Formulate commands and send requqests
    c =  0
    for command in api_req:
        url = f"http://{order_service_ip}:{order_service_port}"
        if c == 0:
            c = 1
            if command != 'restart':
                wipe(url)
            elif command == 'restart':
                continue
        if command == 'shutdown':
            shutdown(url)
            return

        split_command = command.split()
        #API_COMMANDS[split_command[0]][split_command[1]](split_command)
        
        test = API_COMMANDS[split_command[0]][split_command[1]](split_command, url)
        # print(test)
        #print(f"API ENDPOINT: {split_command[0]} |||| API_FUNCTION: {split_command[1]} |||| LINE: {command}")


    


if __name__ == "__main__":
    main(sys.argv)
