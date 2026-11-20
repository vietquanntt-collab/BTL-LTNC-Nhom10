# Shared Windows runtime checks for Maven and JavaFX.
function Resolve-Java21Home {
    $candidates = @()
    if ($env:JAVA_HOME) {
        $candidates += $env:JAVA_HOME
    }
    $javaOnPath = Get-Command java -CommandType Application -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($javaOnPath) {
        $candidates += Split-Path -Parent (Split-Path -Parent $javaOnPath.Source)
    }
    foreach ($candidate in ($candidates | Select-Object -Unique)) {
        $releaseFile = Join-Path $candidate 'release'
        $compiler = Join-Path $candidate 'bin\javac.exe'
        if ((Test-Path -LiteralPath $releaseFile) -and (Test-Path -LiteralPath $compiler)) {
            $release = Get-Content -LiteralPath $releaseFile -Raw
            if ($release -match '(?m)^JAVA_VERSION="21(?:\.|")') {
                return $candidate
            }
        }
    }
    throw 'Can JDK 21: cai JDK 21 va dat JAVA_HOME toi thu muc JDK (co bin\javac.exe).'
}

function Find-DependencyJar {
    param([string]$Repository, [string]$RelativePath)
    $jar = Join-Path $Repository $RelativePath
    if (-not (Test-Path -LiteralPath $jar)) {
        throw "Khong tim thay dependency $RelativePath. Chay scripts\build.ps1 truoc."
    }
    return $jar
}

