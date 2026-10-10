param([string]$KeepApkName = 'PretRadar-0.5.3-release.apk')
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$targets = @('app/build', 'app-v2/build', '.gradle', '.kotlin', 'tools/.runtime')
if (Get-Process emulator,qemu-system-x86_64 -ErrorAction SilentlyContinue) {
    throw 'Stop the test emulator before cleaning the workspace.'
}
$before = (Get-ChildItem -LiteralPath $projectRoot -Force -Recurse -File | Measure-Object Length -Sum).Sum
foreach ($relative in $targets) {
    $path = Join-Path $projectRoot $relative
    if (-not (Test-Path -LiteralPath $path)) { continue }
    $resolved = (Resolve-Path -LiteralPath $path).Path
    if (-not $resolved.StartsWith($projectRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Target is outside the project.'
    }
    if ((Get-Item -LiteralPath $resolved -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) {
        throw 'Refusing to follow a directory link.'
    }
    if (Get-ChildItem -LiteralPath $resolved -Force -Recurse | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint } | Select-Object -First 1) {
        throw 'Refusing to clean a directory containing links.'
    }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}
$output = Join-Path $projectRoot 'output'
if (Test-Path -LiteralPath $output) {
    Get-ChildItem -LiteralPath $output -File | Where-Object {
        $_.Name -like 'PretRadar-*' -and $_.Name -notin @($KeepApkName, "$KeepApkName.sha256")
    } | ForEach-Object { Remove-Item -LiteralPath $_.FullName -Force }
}
$after = (Get-ChildItem -LiteralPath $projectRoot -Force -Recurse -File | Measure-Object Length -Sum).Sum
Write-Output ('Freed {0:N1} MB. Source code, Git history and local signing keys were preserved.' -f (($before-$after)/1MB))
