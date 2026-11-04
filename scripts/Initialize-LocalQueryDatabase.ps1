<#
기존 일반 PostgreSQL에 Query DB만 생성합니다. 운영 RDS 배포나 Command DB 이전 스크립트가 아닙니다.
비밀번호는 표준 입력으로 전달하며 콘솔·명령 인자에 출력하지 않습니다.
#>
param([string]$Container = 'stockdrop-account-postgres')
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$envPath = Join-Path $root '.env'
$utf8 = New-Object Text.UTF8Encoding($false)
if (Test-Path -LiteralPath $envPath) {
    $original = [IO.File]::ReadAllText($envPath)
    $match = [regex]::Matches($original,'(?m)^DB_PASSWORD=([^\r\n]+)')
    if ($match.Count -ne 1) { throw '기존 비밀번호를 안전하게 식별하지 못해 변경하지 않습니다.' }
    if ($original -notmatch '(?m)^DB_USERNAME=product_query\r?$') { throw '다른 DB 계정 설정이 있어 변경하지 않습니다.' }
    $password = $match[0].Groups[1].Value
    # 예제 자격증명으로 SQL을 실행하거나 초기화 완료로 오인하지 않습니다.
    if ($password -eq 'replace-with-generated-query-password') { throw '예제 비밀번호를 실제 Query 비밀번호로 설정해야 합니다.' }
} else {
    $bytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    $password = -join ($bytes | ForEach-Object { $_.ToString('x2') })
    $example = [IO.File]::ReadAllText((Join-Path $root '.env.example'))
    $original = [regex]::Replace($example,'(?m)^DB_PASSWORD=[^\r\n]*',('DB_PASSWORD='+$password))
    # 부분 실패 후 재시도에도 처음 생성한 자격증명을 잃지 않도록 먼저 저장합니다.
    $stream = [IO.File]::Open($envPath,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write)
    try { $data=$utf8.GetBytes($original); $stream.Write($data,0,$data.Length) } finally { $stream.Dispose() }
}
$template = [IO.File]::ReadAllText((Join-Path $root 'docker/postgres/create-query.sql'))
$literal = "'" + $password.Replace("'","''") + "'"
$nl = [Environment]::NewLine
$sql = "SET log_statement='none';"+$nl+"SET log_min_error_statement='panic';"+$nl+"SET standard_conforming_strings=on;"+$nl+$template.Replace(":'query_password'",$literal)
$previous = $ErrorActionPreference
try {
    $ErrorActionPreference = 'Continue'
    $captured = $sql | & docker exec -i $Container sh -c 'exec psql -X -U $POSTGRES_USER -d $POSTGRES_DB -v ON_ERROR_STOP=1' 2>&1
    $code = $LASTEXITCODE
    # 기존 역할의 비밀번호는 변경하지 않고 앱과 같은 자격증명으로 TCP 인증합니다.
    if ($code -eq 0) {
        $loginInput = $password + $nl + "SELECT 1;"
        $loginOutput = $loginInput | & docker exec -i $Container sh -c 'IFS= read -r PGPASSWORD; export PGPASSWORD; exec psql -X -h 127.0.0.1 -U product_query -d product_query -v ON_ERROR_STOP=1' 2>&1
        $code = $LASTEXITCODE
        $loginInput = $null
        $loginOutput = $null
    }
} finally {
    $ErrorActionPreference = $previous
    $password = $null
    $literal = $null
    $sql = $null
    $original = $null
}
if ($code -ne 0) { throw 'Query DB 준비 실패: 기존 DB와 환경 파일은 보존했습니다. 비밀값 없이 대상 상태를 확인해야 합니다.' }
Write-Output 'Query DB/role/schema ready in the general PostgreSQL. Secrets withheld.'
