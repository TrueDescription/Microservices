Start-Process PowerShell -ArgumentList "-NoExit", "-Command wsl --exec bash -c 'cd /mnt/c/Users/faisa/Documents/csc301/a2/Microservice/final/postIscsAsync-3 && ./runme.sh -c'"

Start-Sleep -Seconds 5


$options = @('-i', '-U','-P', '-O')

# $options = @('-p', '-o')

$options | ForEach-Object {
    $option = $_
    Start-Process PowerShell -ArgumentList "-NoExit", "-Command wsl --exec bash -c 'cd /mnt/c/Users/faisa/Documents/csc301/a2/Microservice/final/postIscsAsync-3 && ./runme.sh $option'"
}
# $options | ForEach-Object {
#     $option = $_
#     $bashCommand = "cd /mnt/c/Users/faisa/Documents/csc301/a2/Microservice/final/a2 && ./runme.sh $($option)"
#     Start-Process PowerShell -ArgumentList "-NoExit", "-Command", "wsl --exec bash -c `"$bashCommand`""
# }
Start-Sleep -Seconds 5
Start-Process PowerShell -ArgumentList "-NoExit", "-Command wsl --exec bash -c 'cd /mnt/c/Users/faisa/Documents/csc301/a2/Microservice/final/postIscsAsync-3/tests && python3 a1test_parser.py'"

