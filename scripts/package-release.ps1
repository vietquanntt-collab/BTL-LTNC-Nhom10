param([switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not $SkipBuild) {
    & (Join-Path $PSScriptRoot 'build.ps1') -Clean -Ui
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
foreach ($relative in @('server/target/server.jar', 'client/target/client.jar')) {
    if (-not (Test-Path -LiteralPath (Join-Path $repoRoot $relative))) { throw "Missing $relative. Run build.ps1 -Clean -Ui first." }
}
$outputDirectory = Join-Path $repoRoot ('release-bundle/local-' + [Guid]::NewGuid().ToString('N').Substring(0, 8))
New-Item -ItemType Directory -Path (Join-Path $outputDirectory 'jars') -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $repoRoot 'server/target/server.jar'), (Join-Path $repoRoot 'client/target/client.jar') -Destination (Join-Path $outputDirectory 'jars')
foreach ($relative in @('database', 'scripts', 'docs', 'README.md')) {
    Copy-Item -LiteralPath (Join-Path $repoRoot $relative) -Destination $outputDirectory -Recurse
}
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$lineBreak = [string][char]10
$checksums = foreach ($name in @('server.jar', 'client.jar')) {
    $digest = (Get-FileHash -LiteralPath (Join-Path $outputDirectory "jars/$name") -Algorithm SHA256).Hash.ToLowerInvariant()
    "$digest  jars/$name"
}
[IO.File]::WriteAllText((Join-Path $outputDirectory 'SHA256SUMS.txt'), ($checksums -join $lineBreak) + $lineBreak, $utf8NoBom)
Copy-Item -LiteralPath (Join-Path $repoRoot 'docs/runtime-quickstart.md') -Destination (Join-Path $outputDirectory 'START-HERE.md')
Write-Host "Release package: $outputDirectory"
Write-Host 'Read START-HERE.md. Server: java -jar jars/server.jar'
Write-Host 'Client in another terminal: java -jar jars/client.jar'
