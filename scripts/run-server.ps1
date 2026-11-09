param(
    [int]$Port = 9999,
    [bool]$Seed = $true,
    [string]$DatabasePath = 'data/auction.db'
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$wrapper = Join-Path $repoRoot 'mvnw.cmd'
$m2Repository = Join-Path $env:USERPROFILE '.m2\repository'

function Find-MavenJar {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Pattern,
        [Parameter(Mandatory = $true)]
        [string]$Description
    )

    $jar = Get-ChildItem -Path $m2Repository -Recurse -File -Filter $Pattern |
        Sort-Object FullName |
        Select-Object -Last 1
    if ($null -eq $jar) {
        throw "Không tìm thấy $Description trong Maven local repository: $m2Repository"
    }
    return $jar.FullName
}

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
    & $wrapper -B -pl server -am compile
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }

    $sqliteJar = Find-MavenJar 'sqlite-jdbc-*.jar' 'SQLite JDBC'
    $gsonJar = Find-MavenJar 'gson-*.jar' 'Gson'
    $slf4jJar = Find-MavenJar 'slf4j-api-*.jar' 'SLF4J API'
    $classpath = @(
        (Join-Path $repoRoot 'server\target\classes'),
        (Join-Path $repoRoot 'common\target\classes'),
        $sqliteJar,
        $gsonJar,
        $slf4jJar
    ) -join [IO.Path]::PathSeparator

    $seedValue = $Seed.ToString().ToLowerInvariant()
    & java "-Dauction.server.port=$Port" "-Dauction.db.seed=$seedValue" `
        "-Dauction.db.path=$DatabasePath" `
        '-cp' $classpath 'com.btl.team10.server.ServerApplication'
    exit $LASTEXITCODE
} finally {
    $env:MAVEN_OPTS = $previousMavenOpts
    Pop-Location
}
