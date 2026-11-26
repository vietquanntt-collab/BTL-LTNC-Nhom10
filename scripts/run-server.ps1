param(
    [int]$Port = 9999,
    [bool]$Seed = $true,
    [string]$DatabasePath = 'data/auction.db'
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$wrapper = Join-Path $repoRoot 'mvnw.cmd'
$m2Repository = Join-Path $env:USERPROFILE '.m2\repository'
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
    $javaExecutable = Join-Path $env:JAVA_HOME 'bin\java.exe'
    & $wrapper -B -pl server -am compile
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }

    $sqliteJar = Find-DependencyJar $m2Repository 'org\xerial\sqlite-jdbc\3.45.1.0\sqlite-jdbc-3.45.1.0.jar'
    $gsonJar = Find-DependencyJar $m2Repository 'com\google\code\gson\gson\2.10.1\gson-2.10.1.jar'
    $slf4jJar = Find-DependencyJar $m2Repository 'org\slf4j\slf4j-api\1.7.36\slf4j-api-1.7.36.jar'
    $classpath = @(
        (Join-Path $repoRoot 'server\target\classes'),
        (Join-Path $repoRoot 'common\target\classes'),
        $sqliteJar,
        $gsonJar,
        $slf4jJar
    ) -join [IO.Path]::PathSeparator

    $seedValue = $Seed.ToString().ToLowerInvariant()
    $databaseDirectory = [IO.Path]::GetDirectoryName([IO.Path]::GetFullPath($DatabasePath))
    if ($databaseDirectory) {
        New-Item -ItemType Directory -Path $databaseDirectory -Force | Out-Null
    }
    & $javaExecutable "-Dauction.server.port=$Port" "-Dauction.db.seed=$seedValue" `
        "-Dauction.db.path=$DatabasePath" `
        '-cp' $classpath 'com.btl.team10.server.ServerApplication'
    exit $LASTEXITCODE
} finally {
    $env:JAVA_HOME = $previousJavaHome
    $env:MAVEN_OPTS = $previousMavenOpts
    Pop-Location
}
