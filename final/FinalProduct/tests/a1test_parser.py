import json
import time
import requests
import sys

CONFIG_PATH = "config.json"

# File paths
PATHS = {
    "user": ("user_testcases.json", "user_responses.json"),
    "product": ("product_testcases.json", "product_responses.json"),
    "order": ("order_testcases.json", "order_responses.json"),
    "purchased": ("purchased_testcases.json", "purchased_responses.json"),
}

def load_json(filepath):
    try:
        with open(filepath, 'r',  encoding='utf-8') as file:
            return json.load(file)
    except FileNotFoundError:
        print(f"Error: File '{filepath}' not found.")
        sys.exit(-1)
    except json.JSONDecodeError:
        print(f"Error: JSON failed to parse {filepath}")
        sys.exit(-1)

def test_endpoint(url, req_data, res_data, endpoint):
    for key in req_data.keys():
        if "wipe" in key or "kill" in key:
            endpoint = ''
        expected_status_code = get_expected_status_code(key)
        method = 'get' if 'get' in key else 'post'
        target_url = f"{url}/{endpoint}" + (f"/{req_data[key]['id']}" if method == 'get' else '')
        headers = {'Content-Type': 'application/json'} if method == 'post' else {}

        response = requests.request(method, target_url, json=req_data[key], headers=headers)
        try:
            res_content = response.json()
        except Exception as e:
            print(f"Error: {e}")
            print(f"Response: {response}")
            print(key)
            continue
        res_content = response.json()

        expected_res = json.dumps(res_data[key], sort_keys=True).lower()
        actual_res = json.dumps(res_content, sort_keys=True).lower()
        actual_status_code = response.status_code
        
        if actual_res != expected_res or actual_status_code not in expected_status_code:
            print(f"------------------------------------------------------------------------\n"
                  f"Test Name: {key}\nRequest Body: {req_data[key]}\n"
                  f"Expected: {expected_res}\nRESPONSE CONTENT: {actual_res}\n"
                  f"Actual Code: {actual_status_code}\n"
                  f"Expected Code: {expected_status_code}\n"
                  f"------------------------------------------------------------------------")
            
def get_expected_status_code(test_case_name: str) -> list[int]:
    try :
        return [int(test_case_name.split('_')[2])]
    except Exception as e:
        return [int(num) for num in test_case_name.split('_')[2].split(',')]

def main():
    config_data = load_json(CONFIG_PATH)
    order_service_ip = config_data["InterServiceCommunication"]["ip"]
    order_service_port = config_data["InterServiceCommunication"]["port"]
    url = f"http://{order_service_ip}:{order_service_port}"

    for endpoint, (req_path, res_path) in PATHS.items():
        req_data = load_json(req_path)
        res_data = load_json(res_path)
        if endpoint == "purchased": 
            endpoint = "user/purchased"
            test_endpoint(url, req_data, res_data, endpoint)
            continue
        test_endpoint(url, req_data, res_data, endpoint)


if __name__ == "__main__":
    start_time = time.time() 
    main()
    end_time = time.time() 
    elapsed_time = end_time - start_time 
    print(f"Execution time: {elapsed_time} seconds")

