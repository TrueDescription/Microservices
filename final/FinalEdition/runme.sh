# need to install flask python
# need to pull config file into each folder
# neeed to compile each java file and set class path

# Function to compile code
compile_code() {
    javac -cp .:./compiled/ProductService/* ./src/ProductService/ProductService.java -d ./compiled/.
    javac -cp .:./compiled/UserService/* ./src/UserService/UserService.java -d ./compiled/.
    javac -cp .:./compiled/OrderService/* ./src/OrderService/OrderService.java -d ./compiled/.
    # javac -cp .:./compiled/InterServiceCommunicator/* ./src/InterServiceCommunicator/InterServiceCommunicator.java -d ./compiled/.
    # javac -cp .:./compiled/InterServiceCommunicator/jackson-databind-2.16.1.jar:./compiled/InterServiceCommunicator/jackson-core-2.16.1.jar:./compiled/InterServiceCommunicator/jackson-annotations-2.16.1.jar ./src/InterServiceCommunicator/InterServiceCommunicator.java -d ./compiled/.
    # javac -cp .:./compiled/InterServiceCommunicator/* ./src/InterServiceCommunicator/InterServiceCommunicator.java -d ./compiled/.
    # javac -cp .:./compiled/InterServiceCommunicator/jackson-databind-2.16.1.jar:./compiled/InterServiceCommunicator/jetty-ee10-servlet-12.0.7.jar:./compiled/InterServiceCommunicator/jetty-server-12.0.7.jar:./compiled/InterServiceCommunicator/jetty-servlet-api-4.0.6.jar:./compiled/InterServiceCommunicator/jackson-core-2.16.1.jar:./compiled/InterServiceCommunicator/jackson-annotations-2.16.1.jar ./src/InterServiceCommunicator/InterServiceCommunicator.java -d ./compiled/.
    # :./compiled/InterServiceCommunicator/jetty-servlet-api-4.0.6.jar
    # :./compiled/InterServiceCommunicator/jetty-server-12.0.7.jar
    # :./compiled/InterServiceCommunicator/jetty-ee10-servlet-12.0.7.jar
}

# Function to start User service
start_user_service() {
    cp ./config.json ./compiled/.
    cd ./compiled/
    java -cp .:./UserService/*:./UserService/postgresql-42.7.2.jar UserService.UserService $1
    # java -cp .:./UserService/*:./UserService/postgresql-42.7.2.jar UserService.UserService 15000
}

# Function to start Product service
start_product_service() {
    cp ./config.json ./compiled/.
    cd ./compiled/
    java -cp .:./ProductService/*  ProductService.ProductService
}

# Function to start Order service
start_order_service() {
    cp ./config.json ./compiled/.
    cd ./compiled/
    java -cp .:./OrderService/*  OrderService.OrderService
}

# Function to start ISCS
start_iscs() {
    cp ./config.json ./compiled/.
    # cd ./compiled/
    # java -cp .:./InterServiceCommunicator/*.jar InterServiceCommunicator.InterServiceCommunicator
    # java -cp .:./InterServiceCommunicator/jackson-databind-2.16.1.jar:./InterServiceCommunicator/jackson-core-2.16.1.jar:./InterServiceCommunicator/jackson-annotations-2.16.1.jar InterServiceCommunicator/InterServiceCommunicator

    if python3 -c "import fastapi" &> /dev/null; then
        :
    else
        pip install fastapi
    fi
    if python3 -c "import uvicorn" &> /dev/null; then
        :
    else
        pip install uvicorn
    fi
    if python3 -c "import httpx" &> /dev/null; then
        :
    else
        pip install httpx
    fi
    python3 ./compiled/ISCS/iscs.py
}

# Function to start workload parser
start_workload_parser() {
    if [ -z "$1" ]; then
        echo "Error: Workload file not provided."
        exit 1
    fi
    python3 workload_parser.py $1
}

start_user_service_profile() {
    cp ./config.json ./compiled/.
    cd ./compiled/
    java \
    -XX:StartFlightRecording=filename=userServiceRecording.jfr,dumponexit=true,maxage=24h,maxsize=500M,settings=profile \
    -Dcom.sun.management.jmxremote \
    -Dcom.sun.management.jmxremote.port=10001 \
    -Dcom.sun.management.jmxremote.rmi.port=10001 \
    -Dcom.sun.management.jmxremote.authenticate=false \
    -Dcom.sun.management.jmxremote.ssl=false \
    -Djava.rmi.server.hostname=172.17.206.210 \
    -cp .:./UserService/*:./UserService/postgresql-42.7.2.jar \
    UserService.UserService
}

# Function to start Product service with JFR and JMX enabled for remote monitoring
start_product_service_profile() {
    cp ./config.json ./compiled/.
    cd ./compiled/
    java \
    -XX:StartFlightRecording=filename=productServiceRecording.jfr,dumponexit=true,maxage=24h,maxsize=500M,settings=profile \
    -Dcom.sun.management.jmxremote \
    -Dcom.sun.management.jmxremote.port=10002 \
    -Dcom.sun.management.jmxremote.rmi.port=10002 \
    -Dcom.sun.management.jmxremote.authenticate=false \
    -Dcom.sun.management.jmxremote.ssl=false \
    -Djava.rmi.server.hostname=172.17.206.210 \
    -cp .:./ProductService/* \
    ProductService.ProductService
}

# Function to start Order service with JFR and JMX enabled for remote monitoring
start_order_service_profile() {
    cp ./config.json ./compiled/.
    cd ./compiled/
    java \
    -XX:StartFlightRecording=filename=orderServiceRecording.jfr,dumponexit=true,maxage=24h,maxsize=500M,settings=profile \
    -Dcom.sun.management.jmxremote \
    -Dcom.sun.management.jmxremote.port=10003 \
    -Dcom.sun.management.jmxremote.rmi.port=10003 \
    -Dcom.sun.management.jmxremote.authenticate=false \
    -Dcom.sun.management.jmxremote.ssl=false \
    -Djava.rmi.server.hostname=172.17.206.210 \
    -cp .:./OrderService/* \
    OrderService.OrderService
}


# start_user_service_profile() {
#     cp ./config.json ./compiled/.
#     cd ./compiled/
#     nohup java -cp .:./UserService/*:./UserService/postgresql-42.7.2.jar UserService.UserService &
#     user_pid=$!
#     sleep 3
#     if ps -p $user_pid > /dev/null; then
#         jcmd $user_pid VM.info
#         jcmd $user_pid GC.class_histogram
#         jcmd $user_pid GC.heap_info
#         jcmd $user_pid Thread.print
#         jcmd $user_pid VM.flags
#         jcmd $user_pid VM.system_properties
#         jcmd $user_pid VM.version
#         jcmd $user_pid VM.command_line
#         echo "Ready"
#     else
#         echo "No process found listening on port $port for UserService."
#     fi
# }

# start_product_service_profile() {
#     cp ./config.json ./compiled/.
#     cd ./compiled/
#     nohup java -cp .:./ProductService/*  ProductService.ProductService &
#     product_pid=$!
#     sleep 3
#     if ps -p $product_pid > /dev/null; then
#         jcmd $product_pid VM.info
#         jcmd $product_pid GC.class_histogram
#         jcmd $product_pid GC.heap_info
#         jcmd $product_pid Thread.print
#         jcmd $product_pid VM.flags
#         jcmd $product_pid VM.system_properties
#         jcmd $product_pid VM.version
#         jcmd $product_pid VM.command_line
#         echo "Ready"
#     else
#         echo "No process found listening on port $port for ProductService."
#     fi
# }

# start_order_service_profile() {
#     cp ./config.json ./compiled/.
#     cd ./compiled/
#     nohup java -cp .:./OrderService/*  OrderService.OrderService &
#     order_pid=$!
#     sleep 3
#     if ps -p $order_pid > /dev/null; then
#         jcmd $order_pid VM.info
#         jcmd $order_pid GC.class_histogram
#         jcmd $order_pid GC.heap_info
#         jcmd $order_pid Thread.print
#         jcmd $order_pid VM.flags
#         jcmd $order_pid VM.system_properties
#         jcmd $order_pid VM.version
#         jcmd $order_pid VM.command_line
#         echo "Ready"
#     else
#         echo "No process found listening on port $port for OrderService."
#     fi
# }

# Parse command-line options
while getopts ":cuipow:USPO" option; do
    case "${option}" in
        c) compile_code ;;
        u) start_user_service ;;
        p) start_product_service ;;
        i) start_iscs ;;
        o) start_order_service ;;
        w) start_workload_parser "${OPTARG}" ;;
        U) start_user_service_profile ;;
        P) start_product_service_profile ;;
        O) start_order_service_profile ;;
        \?) echo "Invalid option: -$OPTARG" >&2; exit 1 ;;
    esac
done

# If no options are provided, print usage information
if [ "$#" -eq 0 ]; then
    echo "Usage: $0 [-c] [-u] [-p] [-i] [-o] [-w workloadfile]"
fi