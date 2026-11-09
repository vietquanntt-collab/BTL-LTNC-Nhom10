param(
    [string]$HostName = '127.0.0.1',
    [int]$Port = 9999
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
    # Build through the reactor so common classes are available without
    # installing the root POM into the user's Maven repository.
    & $wrapper -B -pl client -am compile
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }

    $javafxVersion = '21.0.5'
    $javafxPlatform = 'win'
    $javafxCache = Join-Path $repoRoot '.javafx-cache'
    New-Item -ItemType Directory -Path $javafxCache -Force | Out-Null
    $javafxJars = @(
        (Find-MavenJar "javafx-base-$javafxVersion-$javafxPlatform.jar" 'JavaFX base')
        (Find-MavenJar "javafx-graphics-$javafxVersion-$javafxPlatform.jar" 'JavaFX graphics')
        (Find-MavenJar "javafx-controls-$javafxVersion-$javafxPlatform.jar" 'JavaFX controls')
        (Find-MavenJar "javafx-fxml-$javafxVersion-$javafxPlatform.jar" 'JavaFX FXML')
    )
    $modulePath = $javafxJars -join [IO.Path]::PathSeparator
    $classpath = @(
        (Join-Path $repoRoot 'client\target\classes'),
        (Join-Path $repoRoot 'common\target\classes'),
        (Find-MavenJar 'gson-*.jar' 'Gson')
    ) -join [IO.Path]::PathSeparator

    # Running the application directly avoids Maven's plugin-prefix lookup and
    # does not require an install step. JavaFX native Windows artifacts are on
    # the module path; project classes and Gson stay on the classpath.
    & java '--module-path' $modulePath '--add-modules' 'javafx.controls,javafx.fxml' `
        "-Duser.home=$env:USERPROFILE" `
        "-Djavafx.cachedir=$javafxCache" `
        "-Dauction.server.host=$HostName" `
        "-Dauction.server.port=$Port" `
        '-cp' $classpath 'com.btl.team10.client.ClientApp'
    exit $LASTEXITCODE
} finally {
    $env:MAVEN_OPTS = $previousMavenOpts
    Pop-Location
}
