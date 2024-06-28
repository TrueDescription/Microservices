import unittest
import requests

class MyAPITestCase(unittest.TestCase):

    def test_product_create(self):
        """
        Create products, send a get request and check response
        """
        # Replace these values with your actual endpoint and request data
        endpoint = "http://example.com/api/endpoint"
        data = {"command" : "create", "id" : 1, "name" : "p1", "description" : "testD" ,"price" : 7.0, "quantity" : 1100}

        # Send the POST request
        response = requests.post(endpoint, json=data)

        # Check if the request was successful (status code 2xx)
        self.assertTrue(response.ok, f"Request failed with status code {response.status_code}")

        # Parse the response JSON
        response_json = response.json()

        # Now, you can write assertions to check the values in the response JSON
        self.assertEqual(response_json["status"], "success", "Expected 'status' to be 'success'")
        self.assertEqual(response_json["data"]["key1"], "expected_value1", "Unexpected value for 'key1'")
        # Add more assertions as needed

if __name__ == '__main__':
    unittest.main()
