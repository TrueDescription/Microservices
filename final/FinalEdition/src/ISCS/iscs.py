import requests
from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import Response, JSONResponse
# from pydantic import BaseModel
import requests
import uvicorn
import httpx
import json

CONFIG_PATH = "config.json"
app = FastAPI()

# class ProductData(BaseModel):
#     id: int
#     name: str
#     description: str
#     price: float
#     quantity: int

# class UserData(BaseModel):
#     id: int
#     username: str
#     email: str
#     password: str




# Load the configuration file data at the start
with open(CONFIG_PATH, 'r', encoding='utf-8') as config_file:
    global config_data
    config_data = json.load(config_file)
    global USIP
    global USP
    global PSIP
    global PSP
    USIP = config_data["UserService"]["ip"]
    USP = config_data["UserService"]["port"]
    PSIP = config_data["ProductService"]["ip"]
    PSP = config_data["ProductService"]["port"]

    global USURL
    global PSURL
    USURL = f"http://{USIP}:{USP}/user"
    PSURL = f"http://{PSIP}:{PSP}/product"
    # iscs_service_ip = config_data["InterServiceCommunication"].get("ip")
    # iscs_service_port = config_data["InterServiceCommunication"].get("port")


@app.get("/user/{id}")
async def handle_user_get(id: str):
    try:
        async with httpx.AsyncClient() as client:
            response = await client.get(f"{USURL}/{id}")
            print(response.json())
            return Response(content=response.text, status_code=response.status_code, media_type="application/json")
    except Exception as e:
        raise HTTPException(status_code=500, detail="Internal Server Error")

@app.post("/user")
async def handle_user_post(request: Request):
    try:
        request_data = await request.json()
        async with httpx.AsyncClient() as client:
            response = await client.post(USURL, json=request_data)
            print(response.json())
            return Response(content=response.text, status_code=response.status_code, media_type="application/json")
    except Exception as e:
        raise HTTPException(status_code=500, detail="Internal Server Error")

@app.get("/product/{id}")
async def handle_product_get(id: str):
    try:
        async with httpx.AsyncClient() as client:
            response = await client.get(f"{PSURL}/{id}")
            print(response.json())
            return Response(content=response.text, status_code=response.status_code, media_type="application/json")
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/product")
async def handle_product_post(request: Request):
    try:
        request_data = await request.json()
        async with httpx.AsyncClient() as client:
            response = await client.post(PSURL, json=request_data)
            print(response.json())
            return Response(content=response.text, status_code=response.status_code, media_type="application/json")
    except Exception as e:
        print(e)
        raise HTTPException(status_code=500, detail=str(e))
    
@app.post("/")
async def handle_kill_post(request: Request):
    try:
        request_data = await request.json()
        command = request_data.get('command')
        if command in ['shutdown', 'restart']:
            async with httpx.AsyncClient() as client:
                response_us = await client.post(f"http://{USIP}:{USP}/", json=request_data)
                response_ps = await client.post(f"http://{PSIP}:{PSP}/", json=request_data)
                
                # Check response status for both requests
                if response_us.status_code == 200 and response_ps.status_code == 200:
                    return JSONResponse({"command": command})  # Return the executed command
                else:
                    # Handle failed external requests appropriately
                    return JSONResponse({"error": "External service error"}, status_code=502)
        else:
            raise HTTPException(status_code=400, detail="Invalid command")
    except Exception as e:
        print(e)
        raise HTTPException(status_code=500, detail=str(e))

# @app.post("/")
# async def handle_kill_post(request: Request):
#     try:
#         request_data = await request.json()
#         command = (request_data).get('command')
#         if command in ['shutdown', 'restart']:
#             async with httpx.AsyncClient() as client:
#                 response_us = await client.post("http://{USIP}:{USP}/", json=request_data)
#                 response_ps = await client.post("http://{PSIP}:{PSP}/", json=request_data)
#                 return Response({"command": request_data})
#         else:
#             raise HTTPException(status_code=400, detail="Invalid command")
#     except Exception as e:
#         raise HTTPException(status_code=500, detail=str(e))

# @app.post("/")
# def handle_kill_post(request: Request):
#     try:
#         request_data = request.json()  # Synchronous call to get JSON data
#         command = request_data.get('command')
#         if command in ['shutdown', 'restart']:
#             # Use the requests library to make synchronous POST requests
#             response_us = requests.post(f"http://{USIP}:{USP}", json=request_data, headers=request.headers)
#             response_ps = requests.post(f"http://{PSIP}:{PSP}", json=request_data, headers=request.headers)
#             # Ensure to check response_us and response_ps for success or handle them as needed
            
#             # return Response(content={"command": request_data}, media_type="application/json")
#             return JSONResponse({"command": request_data})
#         else:
#             raise HTTPException(status_code=400, detail="Invalid command")
#     except Exception as e:
#         raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    uvicorn.run(app, host=config_data["InterServiceCommunication"].get("ip"), port=config_data["InterServiceCommunication"].get("port"))
