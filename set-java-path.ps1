# Run this script as Administrator to permanently add JDK 17 to PATH
$jdkBin = "C:\Program Files\Java\jdk-17\bin"
$syspath = [System.Environment]::GetEnvironmentVariable("Path", "Machine")
if ($syspath -notlike "*jdk-17*") {
    [System.Environment]::SetEnvironmentVariable("Path", "$jdkBin;$syspath", "Machine")
    [System.Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Java\jdk-17", "Machine")
    Write-Host "Done! JDK 17 added to System PATH. Open a new terminal to use javac." -ForegroundColor Green
} else {
    Write-Host "JDK 17 is already in System PATH." -ForegroundColor Yellow
}
