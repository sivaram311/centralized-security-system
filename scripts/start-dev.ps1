<#
.SYNOPSIS
  Start CSS DEV on :9000 using Postgres schema app_css.dev (aligned with F/G).

.NOTES
  Loads DB password from E:\MyAgent\workflow\db\secrets\postgres.env (gitignored).
  Does not print secrets.
#>
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$secrets = 'E:\MyAgent\workflow\db\secrets\postgres.env'

if (-not (Test-Path $secrets)) {
  throw "Missing secrets file: $secrets"
}

Get-Content $secrets | ForEach-Object {
  if ($_ -match '^\s*#' -or $_ -notmatch '=') { return }
  $parts = $_ -split '=', 2
  $k = $parts[0].Trim()
  $v = $parts[1].Trim().Trim('"')
  Set-Item -Path "Env:$k" -Value $v
}

$env:CSS_DB_USER = if ($env:CSS_DB_USER) { $env:CSS_DB_USER } else { $env:CSS_ROLE_DEV }
$env:CSS_DB_PASSWORD = if ($env:CSS_DB_PASSWORD) { $env:CSS_DB_PASSWORD } else { $env:CSS_ROLE_DEV_PASSWORD }
$env:CSS_JDBC_URL = if ($env:CSS_JDBC_URL) {
  $env:CSS_JDBC_URL
} else {
  "jdbc:postgresql://$($env:POSTGRES_HOST):$($env:POSTGRES_PORT)/$($env:CSS_DB)?currentSchema=dev"
}
$env:CSS_ISSUER = if ($env:CSS_ISSUER) { $env:CSS_ISSUER } else { 'http://127.0.0.1:9000' }
$env:CSS_SEED_ENABLED = if ($env:CSS_SEED_ENABLED) { $env:CSS_SEED_ENABLED } else { 'true' }

Write-Host "Starting CSS DEV profile → Postgres $($env:CSS_DB).dev as $($env:CSS_DB_USER) on :9000"
Set-Location $repo
mvn -q spring-boot:run "-Dspring-boot.run.profiles=dev"
