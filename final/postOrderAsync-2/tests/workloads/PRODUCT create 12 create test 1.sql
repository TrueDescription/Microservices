PRODUCT create 12 create test 1.1 2
PRODUCT create 123 create test 1.1 2
PRODUCT create 1234 create test 1.1 2
PRODUCT info 12
PRODUCT update 12 name:update description:<description> price:7 quantity:7
PRODUCT update 123 name:update description:<description> price:8 quantity:8
PRODUCT update 1234 name:update description:<description> price:9 quantity:9
PRODUCT delete 1234 delete 8.0 8
PRODUCT delete 123 delete 8.0 8
USER create 12 <username> <email> <password>
USER create 123 <username> <email> <password>
USER create 1234 <username> <email> <password>
USER get 1234
USER update 12 username:hello email:bye password:batman123
USER update 123 username:hello email:bye password:batman123
USER update 1234 username:hello email:bye password:batman123
USER delete 12 <username> <email> <password>
USER delete 1234 <username> <email> <password>
ORDER place 12 123 1