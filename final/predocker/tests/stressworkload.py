import concurrent.futures
import time
import requests

base_url = 'http://127.0.0.1:14002' 

def create_user_json(id):
    return {
        "command": "create",
        "id": id,
        "username": f"tester{id}",
        "email": f"test{id}@test.com",
        "password": f"password{id}"
    }

def update_user_json(id):
    return {
        "command": "update",
        "id": id,
        "username": f"tester{id}-update",
        "email": f"testupdate{id}@test.com",
        "password": f"password{id}-update"
    }

def delete_user_json(id):
    return {
        "command": "delete",
        "id": id,
        "username": f"tester{id}",
        "email": f"test{id}@test.com",
        "password": f"password{id}"
    }

def get_user_url(id):
    return f"{base_url}/user/{id}"

def create_product_json(id):
    return {
        "command": "create",
        "id": id,
        "name": f"product{id}",
        "description": f"This is product {id}",
        "price": 162.58,  
        "quantity": 90  
    }

def update_product_json(id):
    return {
        "command": "update",
        "id": id,
        "name": f"product{id}-update",
        "description": f"This is product {id} version 2",
        "price": 199.99,  
        "quantity": 100 
    }

def delete_product_json(id):
    return {
        "command": "delete",
        "id": id,
        "name": f"product{id}",
        "description": f"This is product {id}",
        "price": 79.18, 
        "quantity": 89  
    }

def get_product_url(id):
    return f"{base_url}/product/{id}"

def send_post_request(json_data):
    response = requests.post(f"{base_url}/endpoint", json=json_data)
    return response.json()

def send_get_request(url):
    response = requests.get(url)
    return response.json()
import concurrent.futures
import time
import requests

base_url = 'http://127.0.0.1:14002'

def send_request(endpoint, json_data=None, method='post'):
    url = f"{base_url}/{endpoint}"
    if method == 'post':
        response = requests.post(url, json=json_data)
    elif method == 'get':
        response = requests.get(url)
    else:
        raise ValueError("Unsupported method")
    return response.json()

def send_requests_in_parallel(requests_data, rate_limit=10):
    start_time = time.time()
    with concurrent.futures.ThreadPoolExecutor(max_workers=rate_limit) as executor:
        futures = []
        for req in requests_data:
            endpoint = f"{req['request_type']}/{req['id']}" if req['method'] == 'get' else req['request_type']
            method = req['method']
            data = req.get('data', None)
            future = executor.submit(send_request, endpoint, data, method)
            futures.append(future)

        responses = 0
        for future in concurrent.futures.as_completed(futures):
            try:
                future.result()
                responses += 1
            except Exception as exc:
                print(f"Request generated an exception: {exc}")

    end_time = time.time()
    execution_time = end_time - start_time
    requests_per_second = responses / execution_time if execution_time > 0 else 0
    print(f"Total execution time: {execution_time:.2f} seconds")
    print(f"Total achieved requests per second: {requests_per_second:.2f}")

n = 150
requests_data = []
for i in range(1000, 1000 + n):
    requests_data.append({"request_type": "user", "method": "post", "id": i, "data": create_user_json(i)})
    requests_data.append({"request_type": "product", "method": "post", "id": 2000 + i - 1000, "data": create_product_json(2000 + i - 1000)})

# Add update, delete, and get requests
for i in range(1001, 1001 + n):
    # Get and Update User
    requests_data.append({"request_type": "user", "method": "get", "id": i})
    requests_data.append({"request_type": "user", "method": "post", "id": i, "data": update_user_json(i)})
    # Get and Update Product
    requests_data.append({"request_type": "product", "method": "get", "id": 2001 + i - 1001})
    requests_data.append({"request_type": "product", "method": "post", "id": 2001 + i - 1001, "data": update_product_json(2001 + i - 1001)})

for i in range(1005, 1005 + n):
    # Delete User and Get
    requests_data.append({"request_type": "user", "method": "post", "id": i, "data": delete_user_json(i)})
    requests_data.append({"request_type": "user", "method": "get", "id": i})
    # Delete Product and Get
    requests_data.append({"request_type": "product", "method": "post", "id": 2007 + i - 1005, "data": delete_product_json(2007 + i - 1005)})
    requests_data.append({"request_type": "product", "method": "get", "id": 2007 + i - 1005})

send_requests_in_parallel(requests_data, rate_limit=150)
