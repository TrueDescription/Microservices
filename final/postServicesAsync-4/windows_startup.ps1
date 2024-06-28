Start-Process PowerShell -ArgumentList "-NoExit", "-Command wsl --exec bash -c 'cd /mnt/c/Users/faisa/Documents/csc301/a2/Microservice/final/postServicesAsync-4 && ./runme.sh -c'"


Start-Sleep -Seconds 5

Start-Process PowerShell -ArgumentList "-NoExit", "-Command wsl --exec bash -c 'cd /mnt/c/Users/faisa/Documents/csc301/a2/Microservice/final/postServicesAsync-4 && ./runme.sh -i'"


Start-Sleep -Seconds 2


$options = @('-U','-P', '-O')

# $options = @('-p', '-o')

$options | ForEach-Object {
    $option = $_
    Start-Process PowerShell -ArgumentList "-NoExit", "-Command wsl --exec bash -c 'cd /mnt/c/Users/faisa/Documents/csc301/a2/Microservice/final/postServicesAsync-4 && ./runme.sh $option'"
}
# $options | ForEach-Object {
#     $option = $_
#     $bashCommand = "cd /mnt/c/Users/faisa/Documents/csc301/a2/Microservice/final/a2 && ./runme.sh $($option)"
#     Start-Process PowerShell -ArgumentList "-NoExit", "-Command", "wsl --exec bash -c `"$bashCommand`""
# }
Start-Sleep -Seconds 5
Start-Process PowerShell -ArgumentList "-NoExit", "-Command wsl --exec bash -c 'cd /mnt/c/Users/faisa/Documents/csc301/a2/Microservice/final/postServicesAsync-4/tests && python3 a1test_parser.py'"

