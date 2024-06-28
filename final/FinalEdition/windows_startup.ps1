
$scriptBlock = {
    param($path, $option)
    Set-Location -Path $path
    $command = "./runme.sh $option"
    Invoke-Expression $command
}

$path = "C:\Users\faisa\Documents\csc301\a2\Microservice\final\a2"
$options = @('-u', '-i', '-p', '-o')

$options | ForEach-Object {
    Start-Process PowerShell -ArgumentList "-NoExit", "-Command & {$scriptBlock.Invoke('$path', '$_')}"
}
