param(
    [string]$HostName = '127.0.0.1',
    [int]$Port = 9999
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
        (Find-DependencyJar $m2Repository "org\openjfx\javafx-base\$javafxVersion\javafx-base-$javafxVersion-$javafxPlatform.jar")
        (Find-DependencyJar $m2Repository "org\openjfx\javafx-graphics\$javafxVersion\javafx-graphics-$javafxVersion-$javafxPlatform.jar")
        (Find-DependencyJar $m2Repository "org\openjfx\javafx-controls\$javafxVersion\javafx-controls-$javafxVersion-$javafxPlatform.jar")
        (Find-DependencyJar $m2Repository "org\openjfx\javafx-fxml\$javafxVersion\javafx-fxml-$javafxVersion-$javafxPlatform.jar")
    )
    $modulePath = $javafxJars -join [IO.Path]::PathSeparator
    $classpath = @(
        (Join-Path $repoRoot 'client\target\classes'),
        (Join-Path $repoRoot 'common\target\classes'),
        (Find-DependencyJar $m2Repository 'com\google\code\gson\gson\2.10.1\gson-2.10.1.jar')
    ) -join [IO.Path]::PathSeparator

    # Running the application directly avoids Maven's plugin-prefix lookup and
    # does not require an install step. JavaFX native Windows artifacts are on
    # the module path; project classes and Gson stay on the classpath.
    & $javaExecutable '--module-path' $modulePath '--add-modules' 'javafx.controls,javafx.fxml' `
        "-Duser.home=$env:USERPROFILE" `
        "-Djavafx.cachedir=$javafxCache" `
        "-Dauction.server.host=$HostName" `
        "-Dauction.server.port=$Port" `
        '-cp' $classpath 'com.btl.team10.client.ClientApp'
    exit $LASTEXITCODE
} finally {
    $env:JAVA_HOME = $previousJavaHome
    $env:MAVEN_OPTS = $previousMavenOpts
    Pop-Location
}
