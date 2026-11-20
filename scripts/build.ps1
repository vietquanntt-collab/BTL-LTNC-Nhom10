param(
    [switch]$Clean
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$wrapper = Join-Path $repoRoot 'mvnw.cmd'
. (Join-Path $PSScriptRoot 'java-env.ps1')

if (-not (Test-Path -LiteralPath $wrapper)) {
    throw "Không tìm thấy Maven Wrapper: $wrapper"
}

Push-Location $repoRoot
$previousMavenOpts = $env:MAVEN_OPTS
$previousJavaHome = $env:JAVA_HOME
if ($env:USERPROFILE) {
    $env:MAVEN_OPTS = (@($previousMavenOpts, "-Duser.home=$env:USERPROFILE") |
        Where-Object { $_ -and $_.Trim() }) -join ' '
}
try {
    $env:JAVA_HOME = Resolve-Java21Home
    # OneDrive có thể đang giữ file trong target. Vì vậy mặc định verify không
    # xóa target; dùng -Clean khi muốn dọn build output sau khi đã đóng Java/Maven.
    if ($Clean) {
        & $wrapper -B clean verify
    } else {
        & $wrapper -B verify
    }
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
} finally {
    $env:JAVA_HOME = $previousJavaHome
    $env:MAVEN_OPTS = $previousMavenOpts
    Pop-Location
}
