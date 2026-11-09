param(
    [switch]$Clean
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$wrapper = Join-Path $repoRoot 'mvnw.cmd'

if (-not (Test-Path -LiteralPath $wrapper)) {
    throw "Không tìm thấy Maven Wrapper: $wrapper"
}

Push-Location $repoRoot
$previousMavenOpts = $env:MAVEN_OPTS
if ($env:USERPROFILE) {
    $env:MAVEN_OPTS = (@($previousMavenOpts, "-Duser.home=$env:USERPROFILE") |
        Where-Object { $_ -and $_.Trim() }) -join ' '
}
try {
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
    $env:MAVEN_OPTS = $previousMavenOpts
    Pop-Location
}
